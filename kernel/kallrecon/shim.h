// SPDX-License-Identifier: GPL-2.0-only
/*
 * shim.h
 *
 * Copyright (C) 2026 dere3046
 */

#ifndef SHIM_H
#define SHIM_H

#ifndef __ASSEMBLY__
#include <linux/types.h>
#endif

#if defined(__ASSEMBLY__) && defined(__riscv)
#include <asm/asm.h>
#endif

/* trampolines bind the consumer call sites to p_<name> which the
 * consumer resolves at runtime every call site keeps its direct call */
#if defined(__aarch64__)
#define KSHIM_TRAMPOLINE(name)				\
	.text;						\
	.globl name;					\
	.type name, @function;				\
name:							\
	adrp x16, p_ ## name;				\
	ldr x16, [x16, :lo12:p_ ## name];		\
	br x16;						\
	.size name, .-name;
#elif defined(__x86_64__)
#define KSHIM_TRAMPOLINE(name)				\
	.text;						\
	.globl name;					\
	.type name, @function;				\
name:							\
	jmp *p_ ## name(%rip);				\
	.size name, .-name;
#elif defined(__riscv)
#define KSHIM_TRAMPOLINE(name)				\
	.text;						\
	.globl name;					\
	.type name, @function;				\
name:							\
	lla t0, p_ ## name;				\
	REG_L t0, 0(t0);				\
	jr t0;						\
	.size name, .-name;
#else
#error "unsupported arch"
#endif

#ifndef __ASSEMBLY__
unsigned long __nocfi kshim_resolve(const char *name);
void kshim_data_copy(const char *name, void *dst, size_t size);
void kshim_key_sync(const char *name, struct static_key *key);
void kshim_key_array_sync(const char *name, struct static_key_false *keys,
			  int count);
#endif

#endif
