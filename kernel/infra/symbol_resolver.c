#include <linux/kallsyms.h>
#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/string.h>
#include <linux/version.h>

#include "infra/symbol_resolver.h"
#include "kallrecon/core.h"
#include "kallrecon/hint.h"
#include "kallrecon/shim.h"
#include "kallrecon/variant.h"

#define KSYM_NAME_BUF 256

// https://github.com/torvalds/linux/commit/89245600941e4e0f87d77f60ee269b5e61ef4e49
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
#define USE_KCFI 1
#else
#define USE_KCFI 0
#endif

/* same name and type as the KallRecon module addresses filled at runtime */
static unsigned long offsets;
static unsigned long base;
static unsigned long num;
static unsigned long names;
static unsigned long markers;
static unsigned long seqs;
static unsigned long token_table;
static unsigned long token_index;
static unsigned long scan_back;
static unsigned long scan_fwd;
static unsigned int timeout_ms;

module_param(offsets, ulong, 0444);
module_param(base, ulong, 0444);
module_param(num, ulong, 0444);
module_param(names, ulong, 0444);
module_param(markers, ulong, 0444);
module_param(seqs, ulong, 0444);
module_param(token_table, ulong, 0444);
module_param(token_index, ulong, 0444);
module_param(scan_back, ulong, 0444);
module_param(scan_fwd, ulong, 0444);
module_param(timeout_ms, uint, 0444);

static int (*kallsyms_lookup_size_offset_fn)(unsigned long addr,
                                             unsigned long *symbolsize,
                                             unsigned long *offset);

unsigned long __nocfi find_kernel_symbol_exact(const char *symbol_name)
{
	return kshim_resolve(symbol_name);
}

static unsigned long resolve_symbol_variant(const char *symbol_name)
{
    return kallrecon_find_variant(symbol_name, !USE_KCFI);
}

void *ksu_resolve_symbol_for_functable_hook(const char *symbol_name)
{
    unsigned long addr;

    if (!symbol_name || !symbol_name[0])
        return NULL;

#if !USE_KCFI
    {
        char cfi_name[KSYM_NAME_BUF];

        snprintf(cfi_name, sizeof(cfi_name), "%s.cfi_jt", symbol_name);
        addr = find_kernel_symbol_exact(cfi_name);
        if (addr)
            return (void *)addr;
    }

    addr = resolve_symbol_variant(symbol_name);
    if (addr)
        return (void *)addr;

    return (void *)find_kernel_symbol_exact(symbol_name);
#else
    addr = find_kernel_symbol_exact(symbol_name);
    if (addr)
        return (void *)addr;

    return (void *)resolve_symbol_variant(symbol_name);
#endif
}

bool __nocfi ksu_lookup_size_offset(unsigned long addr, unsigned long *size)
{
    if (!kallsyms_lookup_size_offset_fn || !addr || !size)
        return false;

    return kallsyms_lookup_size_offset_fn(addr, size, NULL) != 0;
}

void __init ksu_init_symbol_resolver(void)
{
    struct kallrecon_hint hint = {
        .offsets = offsets,
        .relative_base = base,
        .num_syms = num,
        .names = names,
        .markers = markers,
        .seqs = seqs,
        .token_table = token_table,
        .token_index = token_index,
        .scan_back = scan_back,
        .scan_fwd = scan_fwd,
        .timeout_ms = timeout_ms,
    };

    kallrecon_supply(&hint);
    find_kallsyms_base();

    if (!kloffs_addr && !scan_back && !scan_fwd) {
        hint.scan_back = 8UL << 20;
        hint.scan_fwd = 8UL << 20;
        pr_info("kallrecon: retry with 8MB window\n");
        kallrecon_supply(&hint);
        find_kallsyms_base();
    }

    if (!kloffs_addr) {
        pr_err("kallrecon: discovery failed (%d)\n", kallrecon_fail_reason());
        return;
    }

    pr_info("kallrecon: %u symbols, layout v%d\n", klnum_val, (int)kl_layout);

    kallsyms_lookup_size_offset_fn = (void *)find_kernel_symbol_exact("kallsyms_lookup_size_offset");
    if (!kallsyms_lookup_size_offset_fn)
        pr_warn("kallrecon: kallsyms_lookup_size_offset not found\n");
}
