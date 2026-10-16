// SPDX-License-Identifier: GPL-2.0-only
/*
 * dynsym.c
 *
 * Copyright (C) 2026 dere3046
 */

#include <linux/atomic.h>
#include <linux/cpumask.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/init.h>
#include <linux/lsm_hooks.h>
#include <linux/mm.h>
#include <linux/module.h>
#include <linux/pid_namespace.h>
#include <linux/printk.h>
#include <linux/proc_ns.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/sched/task.h>
#include <linux/security.h>
#include <linux/srcu.h>
#include <linux/tracepoint.h>

#include "objsec.h"

#include "dynsym.h"
#include "dynsym_list.h"
#include "infra/symbol_resolver.h"

#ifdef MODULE
#define DYNSYM_PTR(name) void *p_##name;
KSU_DYNSYM_LIST(DYNSYM_PTR)
KSU_DYNSYM_LEGACY(DYNSYM_PTR)
#undef DYNSYM_PTR

void *p___arm64_sys_close;
void *p___arm64_sys_setns;
void *p___x64_sys_close;
void *p___x64_sys_setns;
void *p___riscv_sys_close;
void *p___riscv_sys_setns;

struct selinux_state *ksu_selinux_state;
struct lsm_blob_sizes *ksu_selinux_blob_sizes;
struct mm_struct *ksu_init_mm;
const struct proc_ns_operations *ksu_mntns_operations;
struct tracepoint *ksu_tracepoint_sys_enter;
void *ksu_tasklist_lock;
void *ksu_tracepoint_srcu;

static struct task_struct *init_task_ptr;
static struct pid_namespace *init_pid_ns_ptr;
static const struct cpumask *cpu_online_mask_ptr;
static atomic_t *num_online_cpus_ptr;

struct task_struct *ksu_init_task(void)
{
	return init_task_ptr;
}

struct pid_namespace *ksu_init_pid_ns(void)
{
	return init_pid_ns_ptr;
}

const struct cpumask *ksu_cpu_online_mask(void)
{
	return cpu_online_mask_ptr;
}

int ksu_num_online_cpus(void)
{
	return num_online_cpus_ptr ? atomic_read(num_online_cpus_ptr) : 0;
}
#else
extern struct selinux_state selinux_state;
extern struct lsm_blob_sizes selinux_blob_sizes;
extern struct mm_struct init_mm;
extern const struct proc_ns_operations mntns_operations;
extern struct tracepoint __tracepoint_sys_enter;
extern rwlock_t tasklist_lock;
extern struct srcu_struct tracepoint_srcu;

struct selinux_state *ksu_selinux_state = &selinux_state;
struct lsm_blob_sizes *ksu_selinux_blob_sizes = &selinux_blob_sizes;
struct mm_struct *ksu_init_mm = &init_mm;
const struct proc_ns_operations *ksu_mntns_operations = &mntns_operations;
struct tracepoint *ksu_tracepoint_sys_enter = &__tracepoint_sys_enter;
void *ksu_tasklist_lock = &tasklist_lock;
void *ksu_tracepoint_srcu = &tracepoint_srcu;

struct task_struct *ksu_init_task(void)
{
	return &init_task;
}

struct pid_namespace *ksu_init_pid_ns(void)
{
	return &init_pid_ns;
}

const struct cpumask *ksu_cpu_online_mask(void)
{
	return cpu_online_mask;
}

int ksu_num_online_cpus(void)
{
	return num_online_cpus();
}
#endif

void *ksu_selinux_cred(const struct cred *cred)
{
#ifdef MODULE
	return cred->security + ksu_selinux_blob_sizes->lbs_cred;
#else
	return selinux_cred(cred);
#endif
}

void *ksu_selinux_inode(const struct inode *inode)
{
#ifdef MODULE
	if (!inode->i_security)
		return NULL;
	return inode->i_security + ksu_selinux_blob_sizes->lbs_inode;
#else
	return selinux_inode(inode);
#endif
}

u32 ksu_current_sid(void)
{
	u32 sid = 0;

	security_cred_getsecid(current_cred(), &sid);
	return sid;
}

void ksu_tracepoint_synchronize_unregister(void)
{
	synchronize_srcu(ksu_tracepoint_srcu);
	synchronize_rcu();
}

#ifdef MODULE
void __init ksu_dynsym_init(void)
{
	int missing = 0;

#define DYNSYM_RESOLVE(name)						\
	do {								\
		p_##name = (void *)find_kernel_symbol_exact(#name);	\
		if (!p_##name) {					\
			missing++;					\
			pr_debug("ksu: dynsym %s unresolved\n", #name);	\
		}							\
	} while (0);
	KSU_DYNSYM_LIST(DYNSYM_RESOLVE)
	KSU_DYNSYM_LEGACY(DYNSYM_RESOLVE)

#if defined(__aarch64__)
	DYNSYM_RESOLVE(__arm64_sys_close);
	DYNSYM_RESOLVE(__arm64_sys_setns);
#elif defined(__x86_64__)
	DYNSYM_RESOLVE(__x64_sys_close);
	DYNSYM_RESOLVE(__x64_sys_setns);
#elif defined(__riscv)
	DYNSYM_RESOLVE(__riscv_sys_close);
	DYNSYM_RESOLVE(__riscv_sys_setns);
#endif
#undef DYNSYM_RESOLVE

	if (missing)
		pr_info("ksu: %d dynsym entries not on this kernel\n", missing);

	ksu_selinux_state = (void *)find_kernel_symbol_exact("selinux_state");
	ksu_selinux_blob_sizes =
		(void *)find_kernel_symbol_exact("selinux_blob_sizes");
	ksu_init_mm = (void *)find_kernel_symbol_exact("init_mm");
	ksu_mntns_operations =
		(void *)find_kernel_symbol_exact("mntns_operations");
	ksu_tracepoint_sys_enter =
		(void *)find_kernel_symbol_exact("__tracepoint_sys_enter");
	ksu_tasklist_lock = (void *)find_kernel_symbol_exact("tasklist_lock");
	ksu_tracepoint_srcu = (void *)find_kernel_symbol_exact("tracepoint_srcu");

	init_task_ptr = (void *)find_kernel_symbol_exact("init_task");
	init_pid_ns_ptr = (void *)find_kernel_symbol_exact("init_pid_ns");
	cpu_online_mask_ptr =
		(const struct cpumask *)find_kernel_symbol_exact("__cpu_online_mask");
	num_online_cpus_ptr =
		(atomic_t *)find_kernel_symbol_exact("__num_online_cpus");

#ifdef __aarch64__
	ksu_dynsym_data_init();
#endif
}
#else
void __init ksu_dynsym_init(void)
{
}
#endif
