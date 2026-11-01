#include <linux/export.h>
#include <linux/fs.h>
#include <linux/kobject.h>
#include <linux/module.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/workqueue.h>
#include <linux/moduleparam.h>

#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs.h>
#endif

#include "policy/allowlist.h"
#include "policy/app_profile.h"
#include "policy/feature.h"
#include "feature/module_blacklist.h"
#include "feature/netisolate.h"
#include "feature/shell_su_adb_only.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/dynamic_manager.h"
#include "manager/manager_observer.h"
#include "manager/throne_tracker.h"
#include "hook/syscall_hook_manager.h"
#include "hook/setuid_hook.h"
#include "feature/sucompat.h"
#include "hook/lsm_hook.h"
#include "runtime/ksud.h"
#include "runtime/ksud_boot.h"
#include "feature/sulog.h"
#include "supercall/supercall.h"
#include "ksu.h"
#include "infra/file_wrapper.h"
#include "selinux/selinux.h"
#include "hook/syscall_hook.h"
#ifdef __aarch64__
#include "hook/init_module_filter.h"
#endif
#include "feature/adb_root.h"
#include "feature/selinux_hide.h"
#include "feature/avc_spoof.h"
#include "feature/uts_view.h"
#include "feature/mount_view_filter.h"
#include "infra/symbol_resolver.h"
#include "preinit/preinit.h"

#if defined(__x86_64__) && !defined(CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER)
#include <asm/cpufeature.h>
#include <linux/version.h>
#ifndef X86_FEATURE_INDIRECT_SAFE
#error "FATAL: Your kernel is missing the indirect syscall bypass patches!"
#endif
#endif

// workaround for A12-5.10 kernel
// Some third-party kernel (e.g. linegaeOS) uses wrong toolchain, which supports
// CC_HAVE_STACKPROTECTOR_SYSREG while gki's toolchain doesn't.
// Therefore, ksu lkm, which uses gki toolchain, requires this __stack_chk_guard,
// while those third-party kernel can't provide.
// Thus, we manually provide it instead of using kernel's
#if defined(CONFIG_STACKPROTECTOR) &&                                                                                  \
    (defined(CONFIG_ARM64) && defined(MODULE) && !defined(CONFIG_STACKPROTECTOR_PER_TASK))
#include <linux/stackprotector.h>
#include <linux/random.h>
unsigned long __stack_chk_guard __ro_after_init __attribute__((visibility("hidden")));

__attribute__((no_stack_protector)) void __init ksu_setup_stack_chk_guard()
{
    unsigned long canary;

    /* Try to get a semi random initial value. */
    get_random_bytes(&canary, sizeof(canary));
    canary ^= LINUX_VERSION_CODE;
    canary &= CANARY_MASK;
    __stack_chk_guard = canary;
}

__attribute__((naked)) int __init kernelsu_init_early(void)
{
    asm("mov x19, x30;\n"
        "bl ksu_setup_stack_chk_guard;\n"
        "mov x30, x19;\n"
        "b kernelsu_init;\n");
}
#define NEED_OWN_STACKPROTECTOR 1
#else
#define NEED_OWN_STACKPROTECTOR 0
#endif

struct cred *ksu_cred;
bool ksu_late_loaded;
bool ksu_hook_backend_ready;

#ifdef CONFIG_KSU_DEBUG
bool allow_shell = true;
#else
bool allow_shell = false;
#endif
module_param(allow_shell, bool, 0);

bool ksu_no_custom_rc = false;
module_param_named(norc, ksu_no_custom_rc, bool, 0);

#ifdef MODULE
bool ksu_bundled = false;
module_param_named(bundled, ksu_bundled, bool, 0);
#endif

int __init kernelsu_init(void)
{
    int ret;

#if defined(__x86_64__) && !defined(CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER)
    // If the kernel has the hardening patch, X86_FEATURE_INDIRECT_SAFE must be set
    if (!boot_cpu_has(X86_FEATURE_INDIRECT_SAFE)) {
        pr_alert("*************************************************************");
        pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
        pr_alert("**                                                         **");
        pr_alert("**        X86_FEATURE_INDIRECT_SAFE is not enabled!        **");
        pr_alert("**      KernelSU will abort initialization to prevent      **");
        pr_alert("**                     kernel panic.                       **");
        pr_alert("**                                                         **");
        pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
        pr_alert("*************************************************************");
        return -ENOSYS;
    }
#endif

#ifdef MODULE
    ksu_late_loaded = (current->pid != 1);
#else
    ksu_late_loaded = false;
#endif

#ifdef CONFIG_KSU_DEBUG
    pr_alert("*************************************************************");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("**                                                         **");
    pr_alert("**         You are running KernelSU in DEBUG mode          **");
    pr_alert("**                                                         **");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("*************************************************************");
#endif
    if (allow_shell) {
        pr_alert("shell is allowed at init!");
    }

    pr_info("KernelSU version: %s\n", KSU_VERSION_FULL);

    ksu_cred = prepare_creds();
    if (!ksu_cred) {
        pr_err("prepare cred failed!\n");
        return -ENOSYS;
    }

    ret = ksu_selinux_inode_init();
    if (ret) {
        put_cred(ksu_cred);
        ksu_cred = NULL;
        return ret;
    }

#ifdef CONFIG_KSU_SUSFS
    susfs_init();
#endif

    ksu_init_symbol_resolver();
#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
    ret = ksu_syscall_table_init();
    if (ret) {
        pr_warn("SUSFS inline: sys_call_table unavailable: %d\n", ret);
        ksu_hook_backend_ready = false;
    } else {
        ksu_hook_backend_ready = true;
    }
#else
    ret = ksu_syscall_hook_init();
    if (ret) {
        pr_err("tracepoint backend initialization failed: %d\n", ret);
        ksu_hook_backend_ready = false;
    }
#endif

    ksu_feature_init();
    ksu_module_blacklist_init();
    ksu_sulog_init();
    ksu_adb_root_init();
    ksu_lsm_hook_init();
    ksu_selinux_hide_init();
    ksu_shell_su_adb_only_init();
    ksu_avc_spoof_init();
    ksu_mvf_init();
    if (ksu_uts_view_init())
        pr_info("uts_view: feature unavailable\n");
    if (ksu_netisolate_init())
        pr_info("netisolate: feature unavailable\n");

    ksu_supercalls_init();
    ksu_app_profile_init();
#ifdef __aarch64__
    ksu_init_module_filter_init();
#endif

    if (ksu_late_loaded) {
        pr_info("late load mode, skipping kprobe hooks\n");

        apply_kernelsu_rules();
        cache_sid();
        setup_ksu_cred();

        // Grant current process (ksud late-load) root
        // with KSU SELinux domain before enforcing SELinux, so it
        // can continue to access /data/app etc. after enforcement.
        escape_to_root_for_init();

        ksu_allowlist_init();
        ksu_load_allow_list();

#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
        ksu_setuid_hook_init();
        ksu_sucompat_init();
#else
        ret = ksu_syscall_hook_manager_init();
        if (!ret)
            ksu_hook_backend_ready = true;
#endif

        ksu_throne_tracker_init();
#ifdef CONFIG_KSU_DYNAMIC_MANAGER
        ksu_dynamic_manager_init();
        ksu_dynamic_manager_load();
#endif
        ksu_observer_init();
        ksu_file_wrapper_init();

        ksu_boot_completed = true;
        track_throne(false);

        ksu_feature_apply_forced();

        if (!getenforce()) {
            pr_info("Permissive SELinux, enforcing\n");
            setenforce(true);
        }

    } else {
#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
        ksu_setuid_hook_init();
        ksu_sucompat_init();
#else
        ret = ksu_syscall_hook_manager_init();
        if (!ret)
            ksu_hook_backend_ready = true;
#endif

        ksu_allowlist_init();

        ksu_throne_tracker_init();

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
        ksu_dynamic_manager_init();
#endif

        ksu_ksud_init();

        ksu_preinit_init();

        ksu_file_wrapper_init();
    }

#ifdef MODULE
#ifndef CONFIG_KSU_DEBUG
    kobject_del(&THIS_MODULE->mkobj.kobj);
#endif
#endif
    return 0;
}

void __exit kernelsu_exit(void)
{
    ksu_netisolate_exit();

    // Phase 1: Stop all hooks first to prevent new callbacks
#ifdef __aarch64__
    ksu_init_module_filter_exit();
#endif
#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
    ksu_sucompat_exit();
    ksu_setuid_hook_exit();
    ksu_syscall_hook_exit();
#else
    ksu_syscall_hook_manager_exit();
#endif
    ksu_avc_spoof_exit();

    ksu_supercalls_exit();

    if (!ksu_late_loaded) {
        ksu_ksud_exit();
        ksu_preinit_exit();
    }

    // Wait for any in-flight RCU readers (e.g. handler traversing allow_list)
    synchronize_rcu();

    // Phase 2: Now safe to release data structures
    ksu_observer_exit();

    ksu_throne_tracker_exit();

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
    ksu_dynamic_manager_exit();
#endif

    ksu_allowlist_exit();

    ksu_shell_su_adb_only_exit();
    ksu_selinux_hide_exit();
    ksu_mvf_exit();
    ksu_lsm_hook_exit();
    ksu_adb_root_exit();
    ksu_sulog_exit();
    ksu_uts_view_exit();
    ksu_feature_exit();
    ksu_module_blacklist_exit();

    put_cred(ksu_cred);
}

#if NEED_OWN_STACKPROTECTOR
module_init(kernelsu_init_early);
#else
module_init(kernelsu_init);
#endif
module_exit(kernelsu_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("weishu");
MODULE_DESCRIPTION("Android KernelSU");
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 13, 0)
MODULE_IMPORT_NS("VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver");
#else
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver);
#endif
