#ifdef __aarch64__

#include <linux/capability.h>
#include <linux/elf.h>
#include <linux/fcntl.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/limits.h>
#include <linux/moduleparam.h>
#include <linux/overflow.h>
#include <linux/slab.h>
#include <linux/stat.h>
#include <linux/string.h>
#include <linux/uaccess.h>

#include "arch.h"
#include "hook/init_module_filter.h"
#include "hook/syscall_hook.h"
#include "klog.h" // IWYU pragma: keep

#define VIVO_FILTER_LIST_MAX 128
#define VIVO_FILTER_SECTIONS_MAX 4096
#define VIVO_FILTER_SHSTRTAB_MAX (64 * 1024)
#define VIVO_FILTER_MODINFO_MAX (64 * 1024)

static char vivo_module_filter[VIVO_FILTER_LIST_MAX];
module_param_string(vivo_module_filter, vivo_module_filter, sizeof(vivo_module_filter), 0);
MODULE_PARM_DESC(vivo_module_filter,
                 "Acknowledge target module loads without loading: auto, 0, or comma-separated names");

static long (*orig_init_module)(const struct pt_regs *regs);
static long (*orig_finit_module)(const struct pt_regs *regs);
static bool filter_installed;

struct module_reader {
    const char __user *user_data;
    unsigned long user_len;
    struct file *file;
    loff_t file_size;
};

static bool filter_enabled(void)
{
    return vivo_module_filter[0] && strcmp(vivo_module_filter, "0") != 0;
}

static const char *module_list(void)
{
    return strcmp(vivo_module_filter, "auto") == 0 ? "vr" : vivo_module_filter;
}

static bool name_in_filter(const char *name, size_t name_len)
{
    const char *cursor = module_list();
    const char *end = cursor + strnlen(cursor, sizeof(vivo_module_filter));

    while (cursor < end) {
        const char *separator = memchr(cursor, ',', end - cursor);
        size_t entry_len = separator ? (size_t)(separator - cursor) : (size_t)(end - cursor);

        if (entry_len && entry_len == name_len && memcmp(cursor, name, name_len) == 0)
            return true;

        if (!separator)
            break;
        cursor = separator + 1;
    }

    return false;
}

static bool section_header_offset(const Elf64_Ehdr *ehdr, unsigned int index, loff_t *offset)
{
    u64 value;

    if (check_add_overflow(ehdr->e_shoff, (u64)index * sizeof(Elf64_Shdr), &value) || value > LLONG_MAX)
        return false;
    *offset = (loff_t)value;
    return true;
}

static int reader_read(struct module_reader *reader, loff_t offset, void *buf, size_t len)
{
    loff_t total;

    if (reader->user_data) {
        if (reader->user_len > (unsigned long)LLONG_MAX)
            return -EINVAL;
        total = (loff_t)reader->user_len;
    } else {
        total = reader->file_size;
    }

    if (!len || offset < 0 || total < 0 || (loff_t)len > total || offset > total - (loff_t)len)
        return -ERANGE;

    if (reader->user_data) {
        if (copy_from_user(buf, reader->user_data + offset, len))
            return -EFAULT;
        return 0;
    }

    {
        loff_t pos = offset;
        ssize_t read = kernel_read(reader->file, buf, len, &pos);

        if (read < 0)
            return read;
        return (size_t)read == len ? 0 : -EIO;
    }
}

static bool module_name_matches(struct module_reader *reader)
{
    Elf64_Ehdr ehdr;
    Elf64_Shdr shstr_shdr;
    Elf64_Shdr modinfo_shdr;
    char *shstrtab = NULL;
    char *modinfo = NULL;
    loff_t section_offset;
    unsigned int i;
    bool found_modinfo = false;
    bool matched = false;

    if (reader_read(reader, 0, &ehdr, sizeof(ehdr)))
        return false;

    if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) || ehdr.e_ident[EI_CLASS] != ELFCLASS64 ||
        ehdr.e_ident[EI_DATA] != ELFDATA2LSB || ehdr.e_ident[EI_VERSION] != EV_CURRENT ||
        ehdr.e_version != EV_CURRENT || ehdr.e_type != ET_REL || ehdr.e_machine != EM_AARCH64 ||
        ehdr.e_ehsize != sizeof(ehdr) || ehdr.e_shentsize != sizeof(Elf64_Shdr))
        return false;

    if (!ehdr.e_shnum || ehdr.e_shnum > VIVO_FILTER_SECTIONS_MAX || ehdr.e_shstrndx >= ehdr.e_shnum)
        return false;

    if (!section_header_offset(&ehdr, ehdr.e_shstrndx, &section_offset) ||
        reader_read(reader, section_offset, &shstr_shdr, sizeof(shstr_shdr)))
        return false;

    if (shstr_shdr.sh_type != SHT_STRTAB || !shstr_shdr.sh_size || shstr_shdr.sh_size > VIVO_FILTER_SHSTRTAB_MAX)
        return false;

    shstrtab = kmalloc(shstr_shdr.sh_size, GFP_KERNEL);
    if (!shstrtab)
        return false;
    if (reader_read(reader, shstr_shdr.sh_offset, shstrtab, shstr_shdr.sh_size))
        goto out;

    for (i = 0; i < ehdr.e_shnum; i++) {
        const char *name;
        size_t remaining;

        if (!section_header_offset(&ehdr, i, &section_offset) ||
            reader_read(reader, section_offset, &modinfo_shdr, sizeof(modinfo_shdr)))
            goto out;
        if (modinfo_shdr.sh_name >= shstr_shdr.sh_size)
            continue;

        name = shstrtab + modinfo_shdr.sh_name;
        remaining = shstr_shdr.sh_size - modinfo_shdr.sh_name;
        if (strnlen(name, remaining) == remaining)
            continue;
        if (modinfo_shdr.sh_type == SHT_PROGBITS && strcmp(name, ".modinfo") == 0) {
            found_modinfo = true;
            break;
        }
    }

    if (!found_modinfo || !modinfo_shdr.sh_size || modinfo_shdr.sh_size > VIVO_FILTER_MODINFO_MAX)
        goto out;

    modinfo = kmalloc(modinfo_shdr.sh_size, GFP_KERNEL);
    if (!modinfo)
        goto out;
    if (reader_read(reader, modinfo_shdr.sh_offset, modinfo, modinfo_shdr.sh_size))
        goto out;

    {
        const char *cursor = modinfo;
        const char *end = modinfo + modinfo_shdr.sh_size;

        while (cursor < end) {
            const char *nul = memchr(cursor, '\0', end - cursor);
            size_t entry_len;

            if (!nul)
                break;
            entry_len = nul - cursor;
            if (entry_len > sizeof("name=") - 1 && memcmp(cursor, "name=", sizeof("name=") - 1) == 0) {
                matched = name_in_filter(cursor + sizeof("name=") - 1, entry_len - (sizeof("name=") - 1));
                break;
            }
            cursor = nul + 1;
        }
    }

out:
    kfree(modinfo);
    kfree(shstrtab);
    return matched;
}

static bool init_module_matches(const struct pt_regs *regs)
{
    struct module_reader reader = {
        .user_data = (const char __user *)PT_REGS_PARM1(regs),
        .user_len = PT_REGS_PARM2(regs),
    };

    if (!reader.user_data || reader.user_len < sizeof(Elf64_Ehdr))
        return false;
    return module_name_matches(&reader);
}

static bool finit_module_matches(const struct pt_regs *regs)
{
    struct file *file = fget((int)PT_REGS_PARM1(regs));
    struct inode *inode;
    bool matched = false;

    if (!file)
        return false;

    inode = file_inode(file);
    if (!(file->f_mode & FMODE_READ) || !S_ISREG(inode->i_mode))
        goto out;

    {
        loff_t size = i_size_read(inode);
        struct module_reader reader = {
            .file = file,
            .file_size = size,
        };

        if (size >= (loff_t)sizeof(Elf64_Ehdr))
            matched = module_name_matches(&reader);
    }

out:
    fput(file);
    return matched;
}

static long filter_init_module(const struct pt_regs *regs)
{
    if (capable(CAP_SYS_MODULE) && init_module_matches(regs)) {
        pr_info("init_module_filter: acknowledged init_module for target module\n");
        return 0;
    }
    return orig_init_module(regs);
}

static long filter_finit_module(const struct pt_regs *regs)
{
    if (capable(CAP_SYS_MODULE) && finit_module_matches(regs)) {
        pr_info("init_module_filter: acknowledged finit_module for target module\n");
        return 0;
    }
    return orig_finit_module(regs);
}

void __init ksu_init_module_filter_init(void)
{
    if (!filter_enabled())
        return;
    if (!ksu_syscall_table) {
        pr_warn("init_module_filter: syscall table unavailable\n");
        return;
    }

    ksu_syscall_table_hook(__NR_init_module, (syscall_fn_t)filter_init_module, &orig_init_module);
    ksu_syscall_table_hook(__NR_finit_module, (syscall_fn_t)filter_finit_module, &orig_finit_module);

    filter_installed = READ_ONCE(ksu_syscall_table[__NR_init_module]) == (syscall_fn_t)filter_init_module &&
                       READ_ONCE(ksu_syscall_table[__NR_finit_module]) == (syscall_fn_t)filter_finit_module;
    if (!filter_installed) {
        ksu_syscall_table_unhook(__NR_init_module);
        ksu_syscall_table_unhook(__NR_finit_module);
        pr_err("init_module_filter: failed to install syscall hooks\n");
        return;
    }

    pr_info("init_module_filter: enabled for %s\n", module_list());
}

void __exit ksu_init_module_filter_exit(void)
{
    if (!filter_installed)
        return;
    ksu_syscall_table_unhook(__NR_finit_module);
    ksu_syscall_table_unhook(__NR_init_module);
    filter_installed = false;
}

#endif
