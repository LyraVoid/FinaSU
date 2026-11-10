#include <linux/cred.h>
#include <linux/err.h>
#include <linux/fs.h>
#include <linux/kernel.h>
#include <linux/namei.h>
#include <linux/sched.h>
#include <linux/thread_info.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/susfs.h>
#include <linux/susfs_def.h>

#include "feature/adb_root.h"
#include "feature/sucompat.h"
#include "ksu.h"
#include "policy/app_profile.h"
#include "policy/allowlist.h"
#include "preinit/preinit.h"
#include "runtime/ksud.h"
#include "selinux/selinux.h"
#include "sulog/event.h"
#include "supercall/supercall.h"
#include "uapi/supercall.h"
#include "util.h"

#define SU_PATH "/system/bin/su"
#define SH_PATH "/system/bin/sh"

_Static_assert(sizeof(SU_PATH) >= sizeof(KSUD_PATH) && sizeof(SU_PATH) >= sizeof(SH_PATH),
               "su filename storage must fit all redirect targets");

void ksu_handle_init_rc_read(unsigned int fd);
void ksu_handle_init_rc_fstat(int fd, loff_t *size);

#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
static bool inline_filename_valid(struct filename **filename_ptr)
{
    return filename_ptr && *filename_ptr && !IS_ERR(*filename_ptr) && (*filename_ptr)->name;
}

static bool inline_sucompat_allowed(void)
{
    return ksu_su_compat_is_enabled() && !susfs_is_current_proc_no_su() &&
           ksu_is_allow_uid_for_current(current_uid().val);
}

static bool inline_ksud_exists(void)
{
    struct path path;
    const struct cred *old_cred;
    int ret;

    old_cred = override_creds(ksu_cred);
    ret = kern_path(KSUD_PATH, 0, &path);
    revert_creds(old_cred);
    if (!ret)
        path_put(&path);
    return !ret;
}

static const char __user *const __user *inline_argv_ptr(struct user_arg_ptr *argv)
{
#ifdef CONFIG_COMPAT
    if (argv->is_compat)
        return (const char __user *const __user *)argv->ptr.compat;
#endif
    return argv->ptr.native;
}

static void inline_handle_init_exec(const char *path, struct user_arg_ptr *envp)
{
    if (current->pid == 1 || !is_init(current_cred()))
        return;

    if (!strcmp(path, KSUD_PATH)) {
        escape_to_root_for_init();
    } else if (!strstr(path, "/app_process") && !strstr(path, "/adbd") && !strstr(path, "/stub_zygote")) {
        susfs_set_current_proc_no_su();
    }

    if (ksu_adb_root_handle_execve_direct(path, envp))
        pr_err("adb root failed\n");
}

static int inline_handle_execveat(int *fd, struct filename **filename_ptr, struct user_arg_ptr *argv,
                                  struct user_arg_ptr *envp, int *flags)
{
    struct ksu_sulog_pending_event *pending_root = NULL;
    struct ksu_sulog_pending_event *pending_sucompat = NULL;
    const char __user *const __user *argv_user = argv ? inline_argv_ptr(argv) : NULL;
    const char *path;
    long ret;

    if (!inline_filename_valid(filename_ptr) || !fd || !flags)
        return -EINVAL;

    path = (*filename_ptr)->name;
    inline_handle_init_exec(path, envp);
    if (argv)
        ksu_handle_execveat_ksud(path, argv);

    if (current_euid().val == 0)
        pending_root = ksu_sulog_capture_root_execve_kernel(path, argv_user, GFP_KERNEL);

    ksu_sulog_emit_pending(pending_root, 0, GFP_KERNEL);
    pending_root = NULL;

    if (*fd != AT_FDCWD || *flags != 0)
        goto out;
    if (!inline_sucompat_allowed() || strcmp(path, SU_PATH) || !inline_ksud_exists())
        goto out;

    pending_sucompat = ksu_sulog_capture_sucompat_kernel(path, argv_user, GFP_KERNEL);
    pr_info("sucompat: direct exec su -> ksud\n");
    ret = escape_with_root_profile();
    ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
    pending_sucompat = NULL;
    if (ret)
        goto out;

    memcpy((void *)(*filename_ptr)->name, KSUD_PATH, sizeof(KSUD_PATH));
    return 0;

out:
    ksu_sulog_emit_pending(pending_root, 0, GFP_KERNEL);
    ksu_sulog_emit_pending(pending_sucompat, -EINVAL, GFP_KERNEL);
    return -EINVAL;
}

int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    return inline_handle_execveat(fd, filename_ptr, argv, envp, flags);
}

int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    return inline_handle_execveat(fd, filename_ptr, argv, envp, flags);
}

int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags,
                                      int *retval)
{
    int ret;

    if (retval && *retval < 0)
        return 0;

    ret = ksu_install_su_fd();
    if (ret < 0) {
        pr_err("sucompat: install session fd failed: %d\n", ret);
        ksu_hook_backend_ready = false;
    }
    return 0;
}

int ksu_handle_faccessat(int *dfd, struct filename **filename, int *mode, int *unused_flags)
{
    if (!inline_filename_valid(filename) || strcmp((*filename)->name, SU_PATH) || !inline_sucompat_allowed() ||
        !inline_ksud_exists())
        return 0;

    memcpy((void *)(*filename)->name, SH_PATH, sizeof(SH_PATH));
    return 0;
}

int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{
    return ksu_handle_faccessat(dfd, filename, NULL, flags);
}
#else
int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    return -EINVAL;
}

int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    return -EINVAL;
}

int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags,
                                      int *retval)
{
    return 0;
}

int ksu_handle_faccessat(int *dfd, struct filename **filename, int *mode, int *unused_flags)
{
    return 0;
}

int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{
    return 0;
}
#endif

void ksu_handle_sys_read(unsigned int fd)
{
    ksu_handle_init_rc_read(fd);
}

void ksu_handle_vfs_fstat(int fd, loff_t *size)
{
    if (size)
        ksu_handle_init_rc_fstat(fd, size);
}

static int inline_install_fd(void __user **arg)
{
    int fd;

    if (!arg || !*arg)
        return -EFAULT;
    fd = ksu_install_fd();
    if (fd < 0)
        return fd;
    if (copy_to_user(*arg, &fd, sizeof(fd))) {
        ksu_close_fd(fd);
        return -EFAULT;
    }
    return 0;
}

int ksu_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user **arg)
{
    if (magic1 != KSU_INSTALL_MAGIC1)
        return -EINVAL;

    if (magic2 == KSU_INSTALL_MAGIC2) {
#ifdef CONFIG_KSU_HOOK_SUSFS_INLINE
        return inline_install_fd(arg);
#else
        return 0;
#endif
    }

    if (magic2 != SUSFS_MAGIC || current_uid().val != 0)
        return -EINVAL;

    switch (cmd) {
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
    case CMD_SUSFS_ADD_SUS_PATH:
        susfs_add_sus_path(arg);
        return 0;
    case CMD_SUSFS_ADD_SUS_PATH_LOOP:
        susfs_add_sus_path_loop(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
    case CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS:
        susfs_set_hide_sus_mnts_for_non_su_procs(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
    case CMD_SUSFS_ADD_SUS_KSTAT:
        susfs_add_sus_kstat(arg);
        return 0;
    case CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY:
        susfs_add_sus_kstat(arg);
        return 0;
    case CMD_SUSFS_UPDATE_SUS_KSTAT:
        susfs_update_sus_kstat(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
    case CMD_SUSFS_SET_UNAME:
        susfs_set_uname(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
    case CMD_SUSFS_ENABLE_LOG:
        susfs_enable_log(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
    case CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG:
        susfs_set_cmdline_or_bootconfig(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
    case CMD_SUSFS_ADD_OPEN_REDIRECT:
        susfs_add_open_redirect(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_MAP
    case CMD_SUSFS_ADD_SUS_MAP:
        susfs_add_sus_map(arg);
        return 0;
#endif
#ifdef CMD_SUSFS_SET_PRIORITY_UID_FILTER
    case CMD_SUSFS_SET_PRIORITY_UID_FILTER:
        susfs_set_priority_uid_filter(arg);
        return 0;
#endif
    case CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING:
        susfs_set_avc_log_spoofing(arg);
        return 0;
    case CMD_SUSFS_SHOW_ENABLED_FEATURES:
        susfs_get_enabled_features(arg);
        return 0;
    case CMD_SUSFS_SHOW_VARIANT:
        susfs_show_variant(arg);
        return 0;
    case CMD_SUSFS_SHOW_VERSION:
        susfs_show_version(arg);
        return 0;
    default:
        return -EINVAL;
    }
}
