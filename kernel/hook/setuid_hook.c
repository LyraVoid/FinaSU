#include <linux/compiler.h>
#include <linux/version.h>
#include <linux/slab.h>
#include <linux/task_work.h>
#include <linux/thread_info.h>
#include <linux/seccomp.h>
#include <linux/printk.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/uidgid.h>
#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs_def.h>
#include <linux/workqueue.h>
#include "selinux/selinux.h"
#endif

#include "policy/allowlist.h"
#include "hook/setuid_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "infra/seccomp_cache.h"
#include "supercall/supercall.h"
#include "hook/tp_marker.h"
#include "feature/kernel_umount.h"
#include "feature/uts_view.h"

#ifdef CONFIG_KSU_SUSFS
extern struct work_struct susfs_extra_works;

static void ksu_susfs_queue_extra_work(void)
{
    schedule_work(&susfs_extra_works);
}

static void ksu_susfs_mark_umounted(bool zygote_next)
{
    susfs_set_current_proc_no_su();
    susfs_set_current_proc_umounted();
    if (zygote_next)
        susfs_set_current_proc_umounted_for_zygote_next();
}

static void ksu_handle_susfs_setresuid(uid_t old_uid, uid_t new_uid, bool zygote_next)
{
    if (is_isolated_process(new_uid)) {
        ksu_susfs_mark_umounted(zygote_next);
        if (!zygote_next)
            ksu_handle_umount(old_uid, new_uid);
        ksu_susfs_queue_extra_work();
        return;
    }

    if (unlikely(is_uid_manager(new_uid))) {
        spin_lock_irq(&current->sighand->siglock);
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
        ksu_set_task_tracepoint_flag(current);
        spin_unlock_irq(&current->sighand->siglock);

        ksu_install_fd();
        return;
    }

    if (likely((is_appuid(new_uid) || new_uid == WEBVIEW_ZYGOTE_UID) && ksu_uid_should_umount(new_uid))) {
        ksu_susfs_mark_umounted(zygote_next);
        if (!zygote_next)
            ksu_handle_umount(old_uid, new_uid);
        ksu_susfs_queue_extra_work();
        return;
    }

    if (ksu_is_allow_uid_for_current(new_uid)) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER && current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
        return;
    }

    susfs_set_current_proc_no_su();
}
#endif

int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
    (void)suid;
#ifdef CONFIG_KSU_SUSFS
    uid_t old_uid = current_uid().val;

    if (susfs_is_current_zygote_domain())
        ksu_handle_susfs_setresuid(old_uid, ruid, false);
    else if (susfs_is_current_zygote_next_domain())
        ksu_handle_susfs_setresuid(old_uid, ruid, true);
    else
        return 0;

    ksu_uts_view_on_setresuid(old_uid, ruid);
    return 0;
#else
    uid_t old_uid = ruid;
    uid_t new_uid = euid;

    // we rely on the fact that zygote always call setresuid(3) with same uids

    if (unlikely(is_uid_manager(new_uid))) {
        spin_lock_irq(&current->sighand->siglock);
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
        ksu_set_task_tracepoint_flag(current);
        spin_unlock_irq(&current->sighand->siglock);

        ksu_install_fd();
        ksu_uts_view_on_setresuid(old_uid, new_uid);
        return 0;
    }

    if (ksu_is_allow_uid_for_current(new_uid)) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER && current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
        ksu_set_task_tracepoint_flag(current);
    } else {
        ksu_clear_task_tracepoint_flag_if_needed(current);
    }

    // Handle kernel umount
    ksu_handle_umount(old_uid, new_uid);

    ksu_uts_view_on_setresuid(old_uid, new_uid);

    return 0;
#endif
}

void __init ksu_setuid_hook_init(void)
{
    ksu_kernel_umount_init();
}

void __exit ksu_setuid_hook_exit(void)
{
    pr_info("ksu_core_exit\n");
    ksu_kernel_umount_exit();
}
