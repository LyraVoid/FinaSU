/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * dynsym_shim.S
 *
 * Copyright (C) 2026 dere3046
 */

#include "shim.h"

#include "dynsym_list.h"

#define KSU_SHIM_ENTRY(name) KSHIM_TRAMPOLINE(name)
KSU_DYNSYM_LIST(KSU_SHIM_ENTRY)
KSU_DYNSYM_LEGACY(KSU_SHIM_ENTRY)
#undef KSU_SHIM_ENTRY

KSHIM_TRAMPOLINE(__arm64_sys_close)
KSHIM_TRAMPOLINE(__arm64_sys_setns)
KSHIM_TRAMPOLINE(__x64_sys_close)
KSHIM_TRAMPOLINE(__x64_sys_setns)
KSHIM_TRAMPOLINE(__riscv_sys_close)
KSHIM_TRAMPOLINE(__riscv_sys_setns)
