// SPDX-License-Identifier: GPL-2.0-only
/*
 * dynsym_data.c
 *
 * Copyright (C) 2026 dere3046
 */

#if defined(__aarch64__) && defined(MODULE)

#include <linux/bitops.h>
#include <linux/init.h>
#include <linux/jump_label.h>
#include <linux/slab.h>
#include <linux/version.h>
#include <linux/workqueue.h>
#include <asm/cpufeature.h>
#include <asm/memory.h>
#include <asm/pgtable-prot.h>

#include "shim.h"

/* local definitions under the kernel names so nothing here needs an
 * export kallrecon fills them at dynsym init */
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 12, 0)
kmem_buckets kmalloc_caches[NR_KMALLOC_TYPES];
#else
struct kmem_cache *kmalloc_caches[NR_KMALLOC_TYPES][KMALLOC_SHIFT_HIGH + 1];
#endif

struct workqueue_struct *system_wq;
struct workqueue_struct *system_percpu_wq;

s64 memstart_addr;
bool arm64_use_ng_mappings;

unsigned long cpu_hwcaps[BITS_TO_LONGS(ARM64_NCAPS)];
unsigned long system_cpucaps[BITS_TO_LONGS(ARM64_NCAPS)];

DEFINE_STATIC_KEY_FALSE(arm64_const_caps_ready);
DEFINE_STATIC_KEY_ARRAY_FALSE(cpu_hwcap_keys, ARM64_NCAPS);
DEFINE_STATIC_KEY_FALSE(kasan_flag_enabled);
DEFINE_STATIC_KEY_FALSE(gic_nonsecure_priorities);
DEFINE_STATIC_KEY_FALSE(mem_alloc_profiling_key);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 18, 0)
DEFINE_STATIC_KEY_FALSE(validate_usercopy_range);
#endif

void __init ksu_dynsym_data_init(void)
{
	kshim_data_copy("kmalloc_caches", kmalloc_caches, sizeof(kmalloc_caches));
	kshim_data_copy("system_wq", &system_wq, sizeof(system_wq));
	kshim_data_copy("system_percpu_wq", &system_percpu_wq,
			sizeof(system_percpu_wq));
	kshim_data_copy("memstart_addr", &memstart_addr, sizeof(memstart_addr));
	kshim_data_copy("arm64_use_ng_mappings", &arm64_use_ng_mappings,
			sizeof(arm64_use_ng_mappings));
	kshim_data_copy("cpu_hwcaps", cpu_hwcaps, sizeof(cpu_hwcaps));
	kshim_data_copy("system_cpucaps", system_cpucaps, sizeof(system_cpucaps));

	kshim_key_sync("arm64_const_caps_ready", &arm64_const_caps_ready.key);
	kshim_key_sync("kasan_flag_enabled", &kasan_flag_enabled.key);
	kshim_key_sync("gic_nonsecure_priorities", &gic_nonsecure_priorities.key);
	kshim_key_sync("mem_alloc_profiling_key", &mem_alloc_profiling_key.key);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 18, 0)
	kshim_key_sync("validate_usercopy_range", &validate_usercopy_range.key);
#endif
	kshim_key_array_sync("cpu_hwcap_keys", cpu_hwcap_keys, ARM64_NCAPS);
}

#endif
