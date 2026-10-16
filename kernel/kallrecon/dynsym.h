#ifndef __KSU_DYNSYM_H
#define __KSU_DYNSYM_H

#include <linux/types.h>

struct cred;
struct cpumask;
struct inode;
struct lsm_blob_sizes;
struct mm_struct;
struct pid_namespace;
struct proc_ns_operations;
struct selinux_state;
struct task_struct;
struct tracepoint;

extern struct selinux_state *ksu_selinux_state;
extern struct lsm_blob_sizes *ksu_selinux_blob_sizes;
extern struct mm_struct *ksu_init_mm;
extern const struct proc_ns_operations *ksu_mntns_operations;
extern struct tracepoint *ksu_tracepoint_sys_enter;
extern void *ksu_tasklist_lock;
extern void *ksu_tracepoint_srcu;

void *ksu_selinux_cred(const struct cred *cred);
void *ksu_selinux_inode(const struct inode *inode);
u32 ksu_current_sid(void);
void ksu_tracepoint_synchronize_unregister(void);

struct task_struct *ksu_init_task(void);
struct pid_namespace *ksu_init_pid_ns(void);
const struct cpumask *ksu_cpu_online_mask(void);
int ksu_num_online_cpus(void);

#define ksu_for_each_process_thread(p, t)					\
	for (p = ksu_init_task(); (p = next_task(p)) != ksu_init_task();)	\
		for_each_thread(p, t)

void ksu_dynsym_init(void);
void ksu_dynsym_data_init(void);

#endif
