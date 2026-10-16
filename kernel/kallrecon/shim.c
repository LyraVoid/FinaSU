// SPDX-License-Identifier: GPL-2.0-only
/*
 * shim.c
 *
 * Copyright (C) 2026 dere3046
 */

#include <linux/compiler.h>
#include <linux/jump_label.h>
#include <linux/string.h>

#include "core.h"
#include "shim.h"

unsigned long __nocfi kshim_resolve(const char *name)
{
	if (!name || !name[0])
		return 0;

	if (kallrecon_klp) {
		unsigned long addr = kallrecon_klp(name);

		if (addr)
			return addr;
	}

	return kallsyms_name_to_addr(name);
}

void kshim_data_copy(const char *name, void *dst, size_t size)
{
	unsigned long addr = kshim_resolve(name);

	if (addr)
		memcpy(dst, (void *)addr, size);
}

void kshim_key_sync(const char *name, struct static_key *key)
{
	unsigned long addr = kshim_resolve(name);

	if (addr && static_key_enabled((struct static_key *)addr))
		static_key_enable(key);
}

void kshim_key_array_sync(const char *name, struct static_key_false *keys,
			  int count)
{
	unsigned long addr = kshim_resolve(name);
	struct static_key_false *real = (void *)addr;
	int i;

	if (!addr)
		return;

	for (i = 0; i < count; i++) {
		if (static_key_enabled(&real[i].key))
			static_key_enable(&keys[i].key);
	}
}
