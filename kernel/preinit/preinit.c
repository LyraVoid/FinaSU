#include <linux/err.h>
#include <linux/fcntl.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/moduleparam.h>
#include <linux/namei.h>
#include <linux/sched.h>
#include <linux/shmem_fs.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#include "klog.h" // IWYU pragma: keep
#include "util.h"
#include "hook/syscall_hook.h"
#include "preinit/preinit.h"
#include "runtime/ksud.h"

#define INIT_PATH "/system/bin/init"

// The runner is a static ELF embedded into .init.rodata by preinit/blob.S.
extern const char ksu_preinit_runner_start[];
extern const char ksu_preinit_runner_end[];

static bool ksu_preinit_enabled = true;
module_param_named(preinit, ksu_preinit_enabled, bool, 0);

// Owned here until PID 1 executes it; the init section holding the blob is
// gone by then, so it lives in an unlinked shmem file.
static struct file *ksu_preinit_file;
#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
static struct filename *direct_orig_filename;
static struct filename *direct_swapped_filename;
static int direct_preinit_fd = -1;
#endif

void __init ksu_preinit_init(void)
{
    size_t size = ksu_preinit_runner_end - ksu_preinit_runner_start;
    struct file *file;
    loff_t pos = 0;
    ssize_t written;

    if (!ksu_preinit_enabled) {
        pr_info("preinit: disabled\n");
        return;
    }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 19, 0)
    file = shmem_file_setup("ksu_preinit", size, EMPTY_VMA_FLAGS);
#else
    file = shmem_file_setup("ksu_preinit", size, 0);
#endif
    if (IS_ERR(file)) {
        pr_err("preinit: shmem_file_setup failed: %ld\n", PTR_ERR(file));
        return;
    }

    written = kernel_write(file, ksu_preinit_runner_start, size, &pos);
    if (written != size) {
        pr_err("preinit: write runner failed: %zd\n", written);
        fput(file);
        return;
    }

    ksu_preinit_file = file;
    pr_info("preinit: runner staged (%zu bytes)\n", size);
}

bool ksu_preinit_enabled_param(void)
{
    return ksu_preinit_enabled;
}

void ksu_preinit_release(void)
{
    struct file *file = xchg(&ksu_preinit_file, NULL);

    if (file) {
        pr_info("preinit: selinux_setup not seen, dropping runner\n");
        fput(file);
    }
}

void ksu_preinit_exit(void)
{
    ksu_preinit_release();
}

static bool is_selinux_setup(const char __user *const __user *argv_user)
{
    static const char expected[] = "selinux_setup";
    const char __user *arg;
    char buf[sizeof(expected) + 1];

    if (!argv_user || get_user(arg, argv_user + 1) || !arg)
        return false;

    memset(buf, 0, sizeof(buf));
    if (strncpy_from_user(buf, arg, sizeof(buf) - 1) < 0)
        return false;

    return !strcmp(buf, expected);
}

int ksu_setup_preinit_file(struct filename **filename_ptr, struct user_arg_ptr *argv_user)
{
#ifndef CONFIG_KSU_HOOK_SUSFS_INLINE
    return 0;
#else
    const char __user *arg;
    struct filename *next;
    struct file *file;
    char arg_buf[sizeof("selinux_setup") + 1];
    char fd_path[32];
    int fd;

    if (!READ_ONCE(ksu_preinit_file) || !filename_ptr || !*filename_ptr || IS_ERR(*filename_ptr) || !argv_user)
        return 0;
    if (!(*filename_ptr)->name || strcmp((*filename_ptr)->name, INIT_PATH))
        return 0;

    arg = ksu_get_user_arg_ptr(*argv_user, 1);
    if (!arg || IS_ERR(arg))
        return 0;
    memset(arg_buf, 0, sizeof(arg_buf));
    if (strncpy_from_user(arg_buf, arg, sizeof(arg_buf) - 1) < 0 || strcmp(arg_buf, "selinux_setup"))
        return 0;

    file = xchg(&ksu_preinit_file, NULL);
    if (!file)
        return 0;

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("preinit: get fd failed: %d\n", fd);
        fput(file);
        return 0;
    }
    fd_install(fd, file);

    snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", fd);
    next = getname_kernel(fd_path);
    if (IS_ERR(next)) {
        pr_err("preinit: getname_kernel failed: %ld\n", PTR_ERR(next));
        ksu_close_fd(fd);
        return 0;
    }

    direct_orig_filename = *filename_ptr;
    direct_swapped_filename = next;
    direct_preinit_fd = fd;
    *filename_ptr = next;
    pr_info("preinit: direct exec via %s\n", fd_path);
    return fd;
#endif
}

int ksu_preinit_handle_post_execveat(int *fd, struct filename **filename_ptr, void *argv_user, void *envp_user,
                                     int *flags, int *retval, int *preinit_fd)
{
#ifndef CONFIG_KSU_HOOK_SUSFS_INLINE
    return 0;
#else
    int failed;

    if (!direct_orig_filename || !direct_swapped_filename)
        return 0;

    failed = retval && *retval < 0;
    if (failed) {
        pr_err("preinit: direct exec failed: %d, retrying original\n", *retval);
        ksu_close_fd(direct_preinit_fd);
    }

    if (filename_ptr)
        *filename_ptr = direct_orig_filename;
    putname(direct_swapped_filename);
    direct_orig_filename = NULL;
    direct_swapped_filename = NULL;
    direct_preinit_fd = -1;
    if (preinit_fd)
        *preinit_fd = 0;

    return failed;
#endif
}

bool ksu_preinit_handle_execve(const char __user **filename_user, const char __user *const __user *argv_user,
                               int orig_nr, struct pt_regs *regs, long *ret)
{
    const char __user *orig_filename;
    char path[sizeof(INIT_PATH) + 1];
    char fd_path[32];
    struct file *file;
    struct path p;
    int fd, len;

    if (likely(!READ_ONCE(ksu_preinit_file)))
        return false;

    if (!filename_user || !*filename_user)
        return false;

    memset(path, 0, sizeof(path));
    if (strncpy_from_user(path, (const char __user *)untagged_addr((unsigned long)*filename_user), sizeof(path) - 1) <
        0)
        return false;

    if (strcmp(path, INIT_PATH) || !is_selinux_setup(argv_user))
        return false;

    // One shot: the runner's own exec of the real init must pass through.
    file = xchg(&ksu_preinit_file, NULL);
    if (!file)
        return false;

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("preinit: get fd failed: %d\n", fd);
        fput(file);
        return false;
    }
    // The fd owns the file from here on; close-on-exec frees it once the
    // runner has been loaded.
    fd_install(fd, file);

    len = snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", fd);
    if (kern_path(fd_path, LOOKUP_FOLLOW, &p)) {
        pr_err("preinit: %s not reachable, booting without preinit\n", fd_path);
        ksu_close_fd(fd);
        return false;
    }
    path_put(&p);

    orig_filename = *filename_user;
    *filename_user = userspace_stack_buffer(fd_path, len + 1);
    if (!*filename_user) {
        *filename_user = orig_filename;
        ksu_close_fd(fd);
        return false;
    }

    pr_info("preinit: exec runner via %s\n", fd_path);
    *ret = ksu_syscall_table[orig_nr](regs);
    if (*ret < 0) {
        pr_err("preinit: exec runner failed: %ld, booting without preinit\n", *ret);
        *filename_user = orig_filename;
        ksu_close_fd(fd);
        *ret = ksu_syscall_table[orig_nr](regs);
    }
    return true;
}
