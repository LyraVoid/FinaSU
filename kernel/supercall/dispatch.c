#include <linux/capability.h>
#include <linux/cred.h>
#include <linux/kallsyms.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <linux/slab.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <linux/thread_info.h>
#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs.h>
#endif
#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h" // IWYU pragma: keep
#include "policy/allowlist.h"
#include "policy/feature.h"
#include "klog.h" // IWYU pragma: keep
#include "ksu.h"
#include "runtime/ksud_boot.h"
#include "feature/kernel_umount.h"
#include "feature/netisolate.h"
#include "feature/uts_view.h"
#include "manager/dynamic_manager.h"
#include "manager/manager_identity.h"
#include "manager/throne_tracker.h"
#include "selinux/selinux.h"
#include "infra/file_wrapper.h"
#include "hook/tp_marker.h"
#include "policy/app_profile.h"
#include "sulog/event.h"
#include "sulog/fd.h"
#include "supercall/supercall.h"
#include "infra/symbol_resolver.h"
#include "preinit/preinit.h"

static int do_grant_root(void __user *arg)
{
    int ret;
    __u32 audit_uid = current_uid().val;
    __u32 audit_euid = current_euid().val;

    // we already check uid above on allowed_for_su()

    pr_info("allow root for: %d\n", audit_uid);
    ret = escape_with_root_profile();
    ksu_sulog_emit_grant_root(ret, audit_uid, audit_euid, GFP_KERNEL);

    return ret;
}

static int do_get_info(void __user *arg)
{
    struct ksu_get_info_cmd cmd = { .version = KERNEL_SU_VERSION, .flags = 0 };

#ifdef MODULE
    cmd.flags |= KSU_GET_INFO_FLAG_LKM;
    if (ksu_bundled) {
        cmd.flags |= KSU_GET_INFO_FLAG_BUNDLED;
    }
#endif

    if (is_manager()) {
        cmd.flags |= KSU_GET_INFO_FLAG_MANAGER;
    }
    if (ksu_late_loaded) {
        cmd.flags |= KSU_GET_INFO_FLAG_LATE_LOAD;
    }
    if (ksu_preinit_enabled_param() && !ksu_late_loaded)
        cmd.flags |= KSU_GET_INFO_FLAG_PREINIT;
#if defined(EXPECTED_SIZE2) && !defined(SECOND_SIGNATURE_RELEASE)
    cmd.flags |= KSU_GET_INFO_FLAG_PR_BUILD;
#endif
    cmd.features = KSU_FEATURE_MAX;
    cmd.uapi_version = KERNEL_SU_UAPI_VERSION;

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
    {
        u32 dynamic_version;

        if (ksu_dynamic_manager_version_code(current_uid().val, &dynamic_version))
            cmd.version = dynamic_version;
    }
#endif

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("get_version: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_get_info_legacy(void __user *arg)
{
    struct ksu_get_info_legacy_cmd cmd = { .version = KERNEL_SU_VERSION, .flags = 0 };

#ifdef MODULE
    cmd.flags |= KSU_GET_INFO_FLAG_LKM;
    if (ksu_bundled) {
        cmd.flags |= KSU_GET_INFO_FLAG_BUNDLED;
    }
#endif

    if (is_manager()) {
        cmd.flags |= KSU_GET_INFO_FLAG_MANAGER;
    }
    if (ksu_late_loaded) {
        cmd.flags |= KSU_GET_INFO_FLAG_LATE_LOAD;
    }
#if defined(EXPECTED_SIZE2) && !defined(SECOND_SIGNATURE_RELEASE)
    cmd.flags |= KSU_GET_INFO_FLAG_PR_BUILD;
#endif
    cmd.features = KSU_FEATURE_MAX;

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
    {
        u32 dynamic_version;

        if (ksu_dynamic_manager_version_code(current_uid().val, &dynamic_version))
            cmd.version = dynamic_version;
    }
#endif

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("get_version: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_get_full_version(void __user *arg)
{
    struct ksu_get_full_version_cmd cmd = { 0 };

    strscpy(cmd.version_full, KSU_VERSION_FULL, sizeof(cmd.version_full));

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("get_full_version: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_report_event(void __user *arg)
{
    static bool services_started = false;
    struct ksu_report_event_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        return -EFAULT;
    }

    switch (cmd.event) {
    case EVENT_POST_FS_DATA: {
        static bool post_fs_data_lock = false;

        // Reset for emulated soft reboot
        services_started = false;
        if (!post_fs_data_lock) {
            post_fs_data_lock = true;
            if (ksu_late_loaded) {
                pr_info("post-fs-data skipped (late load)\n");
            } else {
                pr_info("post-fs-data triggered\n");
                on_post_fs_data();
            }
        }
        break;
    }
    case EVENT_BOOT_COMPLETED: {
        static bool boot_complete_lock = false;
        if (!boot_complete_lock) {
            boot_complete_lock = true;
            if (ksu_late_loaded) {
                pr_info("boot_complete skipped (late load)\n");
            } else {
                pr_info("boot_complete triggered\n");
                on_boot_completed();
#ifdef CONFIG_KSU_SUSFS
                susfs_start_sdcard_monitor_fn();
#endif
            }
        }
        break;
    }
    case EVENT_MODULE_MOUNTED: {
        pr_info("module mounted!\n");
        on_module_mounted();
        break;
    }
    case EVENT_SERVICES: {
        if (services_started) {
            pr_info("services already started, skipping\n");
            return 0;
        }
        services_started = true;
        pr_info("services triggered\n");
        return 1;
    }
    default:
        break;
    }

    return 0;
}

static int do_set_sepolicy(void __user *arg)
{
    struct ksu_set_sepolicy_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        return -EFAULT;
    }

    return handle_sepolicy((void __user *)cmd.data, cmd.data_len);
}

static int do_check_safemode(void __user *arg)
{
    struct ksu_check_safemode_cmd cmd;

    cmd.in_safe_mode = ksu_is_safe_mode();

    if (cmd.in_safe_mode) {
        pr_warn("safemode enabled!\n");
    }

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("check_safemode: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_new_get_allow_list_common(void __user *arg, bool allow)
{
    struct ksu_new_get_allow_list_cmd cmd;
    int *arr = NULL;
    int err = 0;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        return -EFAULT;
    }

    if (cmd.count) {
        arr = kmalloc(sizeof(int) * cmd.count, GFP_KERNEL);
        if (!arr) {
            return -ENOMEM;
        }
    }

    bool success = ksu_get_allow_list(arr, cmd.count, &cmd.count, &cmd.total_count, allow);

    if (!success) {
        err = -EFAULT;
        goto out;
    }

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("new_get_allow_list: copy_to_user count failed\n");
        err = -EFAULT;
        goto out;
    }

    if (cmd.count && copy_to_user(&((struct ksu_new_get_allow_list_cmd *)arg)->uids, arr, sizeof(int) * cmd.count)) {
        pr_err("new_get_allow_list: copy_to_user uids failed\n");
        err = -EFAULT;
    }

out:
    if (arr) {
        kfree(arr);
    }
    return err;
}

static int do_new_get_deny_list(void __user *arg)
{
    return do_new_get_allow_list_common(arg, false);
}

static int do_new_get_allow_list(void __user *arg)
{
    return do_new_get_allow_list_common(arg, true);
}

static int do_get_allow_list_common(void __user *arg, bool allow)
{
    int *arr = NULL;
    int err = 0;
    u16 count;
    u32 out_count;
    static const u16 kSize = 128;

    arr = kmalloc(sizeof(int) * kSize, GFP_KERNEL);
    if (!arr) {
        return -ENOMEM;
    }

    bool success = ksu_get_allow_list(arr, kSize, &count, NULL, allow);

    if (!success) {
        err = -EFAULT;
        goto out;
    }

    out_count = count;

    if (copy_to_user(arg + offsetof(struct ksu_get_allow_list_cmd, count), &out_count, sizeof(u32))) {
        pr_err("get_allow_list: copy_to_user count failed\n");
        err = -EFAULT;
        goto out;
    }

    if (copy_to_user(arg, arr, sizeof(u32) * count)) {
        pr_err("get_allow_list: copy_to_user uids failed\n");
        err = -EFAULT;
    }

out:
    if (arr) {
        kfree(arr);
    }
    return err;
}

static int do_get_deny_list(void __user *arg)
{
    return do_get_allow_list_common(arg, false);
}

static int do_get_allow_list(void __user *arg)
{
    return do_get_allow_list_common(arg, true);
}

static int do_uid_granted_root(void __user *arg)
{
    struct ksu_uid_granted_root_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        return -EFAULT;
    }

    cmd.granted = ksu_is_uid_granted_root(cmd.uid);

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("uid_granted_root: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_uid_should_umount(void __user *arg)
{
    struct ksu_uid_should_umount_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        return -EFAULT;
    }

    cmd.should_umount = ksu_uid_should_umount(cmd.uid);

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("uid_should_umount: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_get_manager_appid(void __user *arg)
{
    struct ksu_get_manager_appid_cmd cmd;

    cmd.appid = ksu_get_manager_appid();

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("get_manager_appid: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_get_app_profile(void __user *arg)
{
#ifdef CONFIG_KSU_DISABLE_POLICY
    return -EOPNOTSUPP;
#endif
    uid_t uid;
    struct app_profile *profile;
    int ret = 0;

    if (copy_from_user(&uid, (char __user *)arg + offsetof(struct ksu_get_app_profile_cmd, profile.curr_uid),
                       sizeof(uid_t))) {
        pr_err("get_app_profile: copy_from_user failed\n");
        return -EFAULT;
    }

    rcu_read_lock();
    profile = ksu_get_app_profile(uid);
    rcu_read_unlock();
    if (!profile) {
        ret = -ENOENT;
    } else {
        if (copy_to_user((char __user *)arg + offsetof(struct ksu_get_app_profile_cmd, profile), profile,
                         sizeof(struct app_profile))) {
            pr_err("get_app_profile: copy_to_user failed\n");
            ret = -EFAULT;
        }
        ksu_put_app_profile(profile);
    }
    return ret;
}

static int do_set_app_profile(void __user *arg)
{
#ifdef CONFIG_KSU_DISABLE_POLICY
    return -EOPNOTSUPP;
#endif

    struct ksu_set_app_profile_cmd cmd;
    int ret;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("set_app_profile: copy_from_user failed\n");
        return -EFAULT;
    }

    ret = ksu_set_app_profile(&cmd.profile);
    if (!ret) {
        ksu_persistent_allow_list();
#ifndef CONFIG_KSU_SUSFS
        ksu_mark_running_process();
#endif
    }
    return ret;
}

static int do_get_feature(void __user *arg)
{
    struct ksu_get_feature_cmd cmd;
    bool supported = false;
    int ret;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("get_feature: copy_from_user failed\n");
        return -EFAULT;
    }

    ret = ksu_get_feature(cmd.feature_id, &cmd.value, &supported);
    cmd.supported = supported ? KSU_FEATURE_FLAG_SUPPORTED : 0;
    if (supported && ksu_feature_is_forced(cmd.feature_id))
        cmd.supported |= KSU_FEATURE_FLAG_FORCED;

    if (ret && supported) {
        pr_err("get_feature: failed for feature %u: %d\n", cmd.feature_id, ret);
        return ret;
    }

    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("get_feature: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_set_feature(void __user *arg)
{
    struct ksu_set_feature_cmd cmd;
    int ret;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("set_feature: copy_from_user failed\n");
        return -EFAULT;
    }

    ret = ksu_set_feature(cmd.feature_id, cmd.value);
    if (ret) {
        pr_err("set_feature: failed for feature %u: %d\n", cmd.feature_id, ret);
        return ret;
    }

    return 0;
}

static int do_get_uts_view_config(void __user *arg)
{
    struct ksu_uts_view_config config;
    int ret;

    ret = ksu_uts_view_get_config(&config);
    if (ret)
        return ret;
    if (copy_to_user(arg, &config, sizeof(config)))
        return -EFAULT;
    return 0;
}

static int do_set_uts_view_config(void __user *arg)
{
    struct ksu_uts_view_config config;

    if (copy_from_user(&config, arg, sizeof(config)))
        return -EFAULT;
    return ksu_uts_view_set_config(&config);
}

static int do_get_netisolate_config(void __user *arg)
{
    struct ksu_netisolate_config config;
    int ret;

    ret = ksu_netisolate_get_config(&config);
    if (ret)
        return ret;
    if (copy_to_user(arg, &config, sizeof(config)))
        return -EFAULT;
    return 0;
}

static int do_set_netisolate_config(void __user *arg)
{
    struct ksu_netisolate_config config;

    if (copy_from_user(&config, arg, sizeof(config)))
        return -EFAULT;
    return ksu_netisolate_set_config(&config);
}

static int do_get_netisolate_policies(void __user *arg)
{
    struct ksu_netisolate_policies *config;
    int ret;

    config = kzalloc(sizeof(*config), GFP_KERNEL);
    if (!config)
        return -ENOMEM;
    ret = ksu_netisolate_get_policies(config);
    if (ret)
        goto out;
    if (copy_to_user(arg, config, sizeof(*config)))
        ret = -EFAULT;
out:
    kfree(config);
    return ret;
}

static int do_set_netisolate_policies(void __user *arg)
{
    struct ksu_netisolate_policies *config;
    int ret;

    config = kzalloc(sizeof(*config), GFP_KERNEL);
    if (!config)
        return -ENOMEM;
    if (copy_from_user(config, arg, sizeof(*config)))
        ret = -EFAULT;
    else
        ret = ksu_netisolate_set_policies(config);
    kfree(config);
    return ret;
}

static int do_get_netisolate_events(void __user *arg)
{
    struct ksu_netisolate_events *events;
    int ret;

    events = kzalloc(sizeof(*events), GFP_KERNEL);
    if (!events)
        return -ENOMEM;
    ret = ksu_netisolate_get_events(events);
    if (!ret && copy_to_user(arg, events, sizeof(*events)))
        ret = -EFAULT;
    kfree(events);
    return ret;
}

static int do_clear_netisolate_events(void __user *arg)
{
    (void)arg;
    return ksu_netisolate_clear_events();
}

static int do_get_wrapper_fd(void __user *arg)
{
    if (!ksu_file_sid) {
        return -EINVAL;
    }

    struct ksu_get_wrapper_fd_cmd cmd;
    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("get_wrapper_fd: copy_from_user failed\n");
        return -EFAULT;
    }

    return ksu_install_file_wrapper(cmd.fd);
}

static int do_manage_mark(void __user *arg)
{
    struct ksu_manage_mark_cmd cmd;
#ifndef CONFIG_KSU_SUSFS
    int ret = 0;
#endif

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("manage_mark: copy_from_user failed\n");
        return -EFAULT;
    }

    switch (cmd.operation) {
#ifdef CONFIG_KSU_SUSFS
    case KSU_MARK_GET:
        cmd.result = susfs_is_current_proc_no_su() ? 0 : 1;
        break;
    case KSU_MARK_MARK:
    case KSU_MARK_UNMARK:
    case KSU_MARK_REFRESH:
        break;
#else
    case KSU_MARK_GET: {
        // Get task mark status
        ret = ksu_get_task_mark(cmd.pid);
        if (ret < 0) {
            pr_err("manage_mark: get failed for pid %d: %d\n", cmd.pid, ret);
            return ret;
        }
        cmd.result = (u32)ret;
        break;
    }
    case KSU_MARK_MARK: {
        if (cmd.pid == 0) {
            ksu_mark_all_process();
        } else {
            ret = ksu_set_task_mark(cmd.pid, true);
            if (ret < 0) {
                pr_err("manage_mark: set_mark failed for pid %d: %d\n", cmd.pid, ret);
                return ret;
            }
        }
        break;
    }
    case KSU_MARK_UNMARK: {
        if (cmd.pid == 0) {
            ksu_unmark_all_process();
        } else {
            ret = ksu_set_task_mark(cmd.pid, false);
            if (ret < 0) {
                pr_err("manage_mark: set_unmark failed for pid %d: %d\n", cmd.pid, ret);
                return ret;
            }
        }
        break;
    }
    case KSU_MARK_REFRESH: {
        ksu_mark_running_process();
        pr_info("manage_mark: refreshed running processes\n");
        break;
    }
#endif
    default: {
        pr_err("manage_mark: invalid operation %u\n", cmd.operation);
        return -EINVAL;
    }
    }
    if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        pr_err("manage_mark: copy_to_user failed\n");
        return -EFAULT;
    }

    return 0;
}

static int do_nuke_ext4_sysfs(void __user *arg)
{
    struct ksu_nuke_ext4_sysfs_cmd cmd;
    char mnt[256];
    long ret;

    if (copy_from_user(&cmd, arg, sizeof(cmd)))
        return -EFAULT;

    if (!cmd.arg)
        return -EINVAL;

    memset(mnt, 0, sizeof(mnt));

    ret = strncpy_from_user(mnt, cmd.arg, sizeof(mnt));
    if (ret < 0) {
        pr_err("nuke ext4 copy mnt failed: %ld\n", ret);
        return -EFAULT;
    }

    if (ret == sizeof(mnt)) {
        pr_err("nuke ext4 mnt path too long\n");
        return -ENAMETOOLONG;
    }

    pr_info("do_nuke_ext4_sysfs: %s\n", mnt);

    return nuke_ext4_sysfs(mnt);
}

struct list_head mount_list = LIST_HEAD_INIT(mount_list);
DECLARE_RWSEM(mount_list_lock);

_Static_assert(sizeof(struct ksu_try_umount_entry) == 264, "unexpected umount entry ABI");
_Static_assert(offsetof(struct ksu_try_umount_entry, flags) == 256, "unexpected umount flags offset");

static int add_try_umount(void __user *arg)
{
    struct mount_entry *new_entry, *entry, *tmp;
    struct ksu_add_try_umount_cmd cmd;
    char buf[256] = { 0 };
    unsigned int count = 0;

    if (copy_from_user(&cmd, arg, sizeof cmd))
        return -EFAULT;

    switch (cmd.mode) {
    case KSU_UMOUNT_WIPE: {
        struct mount_entry *entry, *tmp;
        unsigned int removed = 0;
        down_write(&mount_list_lock);
        list_for_each_entry_safe (entry, tmp, &mount_list, list) {
            list_del(&entry->list);
            kfree(entry->umountable);
            kfree(entry);
            removed++;
        }
        up_write(&mount_list_lock);
        pr_info("wipe_umount_list: removed %u entries\n", removed);

        return 0;
    }

    case KSU_UMOUNT_ADD: {
        long len = strncpy_from_user(buf, (const char __user *)cmd.arg, 256);
        if (len <= 0)
            return -EFAULT;

        buf[sizeof(buf) - 1] = '\0';

        new_entry = kzalloc(sizeof(*new_entry), GFP_KERNEL);
        if (!new_entry)
            return -ENOMEM;

        new_entry->umountable = kstrdup(buf, GFP_KERNEL);
        if (!new_entry->umountable) {
            kfree(new_entry);
            return -ENOMEM;
        }

        down_write(&mount_list_lock);

        // disallow dupes
        list_for_each_entry (entry, &mount_list, list) {
            if (!strcmp(entry->umountable, buf)) {
                pr_info("cmd_add_try_umount: entry already present\n");
                up_write(&mount_list_lock);
                kfree(new_entry->umountable);
                kfree(new_entry);
                return -EEXIST;
            }
            count++;
        }

        if (count >= KSU_TRY_UMOUNT_MAX_ENTRIES) {
            up_write(&mount_list_lock);
            kfree(new_entry->umountable);
            kfree(new_entry);
            pr_warn("cmd_add_try_umount: entry limit reached (%u)\n", KSU_TRY_UMOUNT_MAX_ENTRIES);
            return -ENOSPC;
        }

        // now check flags and add
        // this also serves as a null check
        if (cmd.flags)
            new_entry->flags = cmd.flags;
        else
            new_entry->flags = 0;

        // debug
        list_add(&new_entry->list, &mount_list);
        up_write(&mount_list_lock);
        pr_info("cmd_add_try_umount: entry added\n");

        return 0;
    }

    // this is just strcmp'd wipe anyway
    case KSU_UMOUNT_DEL: {
        long len = strncpy_from_user(buf, (const char __user *)cmd.arg, sizeof(buf) - 1);
        if (len <= 0)
            return -EFAULT;

        buf[sizeof(buf) - 1] = '\0';

        down_write(&mount_list_lock);
        list_for_each_entry_safe (entry, tmp, &mount_list, list) {
            if (!strcmp(entry->umountable, buf)) {
                list_del(&entry->list);
                kfree(entry->umountable);
                kfree(entry);
                pr_info("cmd_add_try_umount: entry removed\n");
            }
        }
        up_write(&mount_list_lock);

        return 0;
    }

    default: {
        pr_err("cmd_add_try_umount: invalid operation %u\n", cmd.mode);
        return -EINVAL;
    }

    } // switch(cmd.mode)

    return 0;
}

static int get_try_umount(void __user *arg)
{
    struct ksu_get_try_umount_cmd cmd;
    struct ksu_try_umount_entry *entries;
    struct mount_entry *entry;
    unsigned int count = 0;
    __u32 input_size;
    size_t required;
    int ret = 0;

    if (copy_from_user(&cmd, arg, sizeof(cmd)))
        return -EFAULT;

    input_size = cmd.size;

    entries = kvzalloc((size_t)KSU_TRY_UMOUNT_MAX_ENTRIES * sizeof(*entries), GFP_KERNEL);
    if (!entries)
        return -ENOMEM;

    down_read(&mount_list_lock);
    list_for_each_entry (entry, &mount_list, list) {
        size_t len;

        if (count >= KSU_TRY_UMOUNT_MAX_ENTRIES)
            break;
        if (!entry->umountable)
            continue;
        len = strlen(entry->umountable);
        if (len >= KSU_TRY_UMOUNT_PATH_MAX)
            continue;

        memcpy(entries[count].path, entry->umountable, len + 1);
        entries[count].flags = entry->flags;
        count++;
    }
    up_read(&mount_list_lock);

    required = (size_t)count * sizeof(*entries);
    cmd.size = (__u32)required;
    cmd.count = count;
    if (input_size < required) {
        ret = -ENOSPC;
    } else if (required && !cmd.entries) {
        ret = -EFAULT;
    } else if (required && copy_to_user((void __user *)cmd.entries, entries, required)) {
        ret = -EFAULT;
    } else if (copy_to_user(arg, &cmd, sizeof(cmd))) {
        ret = -EFAULT;
    }

    kvfree(entries);
    return ret;
}

static int do_set_init_pgrp(void __user *arg)
{
    int err;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 15, 0)
    struct pid *pids[PIDTYPE_MAX] = { 0 };
#endif

    write_lock_irq(&tasklist_lock);
    struct task_struct *p = current->group_leader;
    struct pid *init_group = task_pgrp(&init_task);

    err = -EPERM;
    if (task_session(p) != task_session(&init_task))
        goto out;

    err = 0;
    if (task_pgrp(p) != init_group) {
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 15, 0)
        change_pid(pids, p, PIDTYPE_PGID, init_group);
#else
        change_pid(p, PIDTYPE_PGID, init_group);
#endif
    }

out:
    write_unlock_irq(&tasklist_lock);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 15, 0)
    free_pids(pids);
#endif

    return err;
}

static int do_get_sulog_fd(void __user *arg)
{
    struct ksu_get_sulog_fd_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("get_sulog_fd: copy_from_user failed\n");
        return -EFAULT;
    }

    if (cmd.flags) {
        pr_err("get_sulog_fd: unsupported flags 0x%x\n", cmd.flags);
        return -EINVAL;
    }

    return ksu_install_sulog_fd();
}

static int do_disable_escape_to_root(void __user *arg)
{
    set_thread_flag(TIF_KSU_DISABLE_ESCAPE_WITH_ROOT);
    return 0;
}

#ifdef CONFIG_KSU_DYNAMIC_MANAGER
static int do_set_dynamic_managers(void __user *arg)
{
    struct ksu_dynamic_manager_cmd cmd;
    struct ksu_dynamic_manager_sign *signs = NULL;
    bool need_rescan = false;
    int ret;

    if (copy_from_user(&cmd, arg, sizeof(cmd)))
        return -EFAULT;

    if (cmd.count > KSU_DYNAMIC_MANAGER_MAX_SIGNS)
        return -EINVAL;

    if (cmd.count) {
        size_t size = sizeof(*signs) * cmd.count;
        signs = kzalloc(size, GFP_KERNEL);
        if (!signs)
            return -ENOMEM;
        if (copy_from_user(signs, (void __user *)(unsigned long)cmd.signs, size)) {
            kfree(signs);
            return -EFAULT;
        }
    }

    ret = ksu_dynamic_manager_set(signs, cmd.count, &need_rescan);
    kfree(signs);

    if (ret == 0) {
        ksu_dynamic_manager_persist();
        if (need_rescan)
            track_throne_force();
    }

    return ret;
}

static int do_get_dynamic_managers(void __user *arg)
{
    struct ksu_get_dynamic_managers_cmd cmd;
    struct ksu_dynamic_manager_app *apps = NULL;
    u32 count;
    int ret = 0;

    if (copy_from_user(&cmd, arg, sizeof(cmd)))
        return -EFAULT;

    if (cmd.count > KSU_DYNAMIC_MANAGER_MAX_APPS)
        cmd.count = KSU_DYNAMIC_MANAGER_MAX_APPS;

    if (cmd.count) {
        apps = kzalloc(sizeof(*apps) * cmd.count, GFP_KERNEL);
        if (!apps)
            return -ENOMEM;
    }

    count = ksu_dynamic_manager_get_apps(apps, cmd.count, &cmd.total_count);
    cmd.count = count;

    if (count && copy_to_user((void __user *)(unsigned long)cmd.apps, apps, sizeof(*apps) * count))
        ret = -EFAULT;

    kfree(apps);

    if (ret)
        return ret;

    if (copy_to_user(arg, &cmd, sizeof(cmd)))
        return -EFAULT;

    return 0;
}

static int do_set_dynamic_manager_grants(void __user *arg)
{
    struct ksu_dynamic_manager_grants_cmd cmd;
    uid_t *appids = NULL;
    int ret;

    if (copy_from_user(&cmd, arg, sizeof(cmd)))
        return -EFAULT;

    if (cmd.reserved || cmd.count > KSU_DYNAMIC_MANAGER_MAX_APPS)
        return -EINVAL;

    if (cmd.count) {
        size_t size = sizeof(*appids) * cmd.count;

        appids = kzalloc(size, GFP_KERNEL);
        if (!appids)
            return -ENOMEM;
        if (copy_from_user(appids, (void __user *)(unsigned long)cmd.appids, size)) {
            kfree(appids);
            return -EFAULT;
        }
    }

    ret = ksu_dynamic_manager_set_grants(appids, cmd.count);
    kfree(appids);

    return ret;
}
#endif // CONFIG_KSU_DYNAMIC_MANAGER

static int do_lookup_symbols(void __user *arg)
{
    struct ksu_lookup_symbols_cmd cmd;
    const u64 __user *unames;
    u64 __user *uaddrs;
    char **names;
    unsigned long *addrs;
    int ret = 0;
    u32 i;

    if (copy_from_user(&cmd, arg, sizeof(cmd)))
        return -EFAULT;
    if (cmd.flags || !cmd.count || cmd.count > KSU_LOOKUP_SYMBOLS_MAX)
        return -EINVAL;

    names = kvcalloc(cmd.count, sizeof(*names), GFP_KERNEL);
    addrs = kvcalloc(cmd.count, sizeof(*addrs), GFP_KERNEL);
    if (!names || !addrs) {
        ret = -ENOMEM;
        goto out;
    }

    unames = (const u64 __user *)(uintptr_t)cmd.names;
    uaddrs = (u64 __user *)(uintptr_t)cmd.addrs;
    for (i = 0; i < cmd.count; i++) {
        u64 uname;
        char *name;

        if (get_user(uname, unames + i)) {
            ret = -EFAULT;
            goto out;
        }
        name = strndup_user((const char __user *)(uintptr_t)uname, KSYM_NAME_LEN);
        if (IS_ERR(name)) {
            if (PTR_ERR(name) == -EINVAL)
                continue;
            ret = PTR_ERR(name);
            goto out;
        }
        if (!*name) {
            kfree(name);
            continue;
        }
        names[i] = name;
    }

    ret = ksu_lookup_kernel_symbols((const char *const *)names, addrs, cmd.count);
    if (ret < 0)
        goto out;
    for (i = 0; i < cmd.count; i++) {
        if (put_user((u64)addrs[i], uaddrs + i)) {
            ret = -EFAULT;
            goto out;
        }
    }

out:
    if (names) {
        for (i = 0; i < cmd.count; i++)
            kfree(names[i]);
    }
    kvfree(names);
    kvfree(addrs);
    return ret;
}

// IOCTL handlers mapping table
// clang-format off
static const struct ksu_ioctl_cmd_map ksu_ioctl_handlers[] = {
    { 
        .cmd = KSU_IOCTL_GRANT_ROOT,
        .name = "GRANT_ROOT",
        .handler = do_grant_root,
        .perm_check = allowed_for_su 
    },
    {
        .cmd = KSU_IOCTL_GET_INFO,
        .name = "GET_INFO",
        .handler = do_get_info,
        .perm_check = always_allow
    },
    {
        .cmd = KSU_IOCTL_GET_INFO_LEGACY,
        .name = "GET_INFO_LEGACY",
        .handler = do_get_info_legacy,
        .perm_check = always_allow
    },
    {
        .cmd = KSU_IOCTL_GET_FULL_VERSION,
        .name = "GET_FULL_VERSION",
        .handler = do_get_full_version,
        .perm_check = always_allow
    },
    {
        .cmd = KSU_IOCTL_REPORT_EVENT,
        .name = "REPORT_EVENT",
        .handler = do_report_event,
        .perm_check = only_root
    },
    {
        .cmd = KSU_IOCTL_SET_SEPOLICY,
        .name = "SET_SEPOLICY",
        .handler = do_set_sepolicy,
        .perm_check = only_root
    },
    {
        .cmd = KSU_IOCTL_CHECK_SAFEMODE,
        .name = "CHECK_SAFEMODE",
        .handler = do_check_safemode,
        .perm_check = always_allow
    },
    {
        .cmd = KSU_IOCTL_GET_ALLOW_LIST,
        .name = "GET_ALLOW_LIST",
        .handler = do_get_allow_list,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_DENY_LIST,
        .name = "GET_DENY_LIST",
        .handler = do_get_deny_list,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_NEW_GET_ALLOW_LIST,
        .name = "NEW_GET_ALLOW_LIST",
        .handler = do_new_get_allow_list,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_NEW_GET_DENY_LIST,
        .name = "NEW_GET_DENY_LIST",
        .handler = do_new_get_deny_list,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_UID_GRANTED_ROOT,
        .name = "UID_GRANTED_ROOT",
        .handler = do_uid_granted_root,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_UID_SHOULD_UMOUNT,
        .name = "UID_SHOULD_UMOUNT",
        .handler = do_uid_should_umount,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_MANAGER_APPID,
        .name = "GET_MANAGER_APPID",
        .handler = do_get_manager_appid,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_APP_PROFILE,
        .name = "GET_APP_PROFILE",
        .handler = do_get_app_profile,
        .perm_check = only_manager
    },
    {
        .cmd = KSU_IOCTL_SET_APP_PROFILE,
        .name = "SET_APP_PROFILE",
        .handler = do_set_app_profile,
        .perm_check = only_manager
    },
    {
        .cmd = KSU_IOCTL_GET_FEATURE,
        .name = "GET_FEATURE",
        .handler = do_get_feature,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_SET_FEATURE,
        .name = "SET_FEATURE",
        .handler = do_set_feature,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_WRAPPER_FD,
        .name = "GET_WRAPPER_FD",
        .handler = do_get_wrapper_fd,
        .perm_check = manager_or_root,
        .allow_su_session = true
    },
    {
        .cmd = KSU_IOCTL_MANAGE_MARK,
        .name = "MANAGE_MARK",
        .handler = do_manage_mark,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_NUKE_EXT4_SYSFS,
        .name = "NUKE_EXT4_SYSFS",
        .handler = do_nuke_ext4_sysfs,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_ADD_TRY_UMOUNT,
        .name = "ADD_TRY_UMOUNT",
        .handler = add_try_umount,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_TRY_UMOUNT,
        .name = "GET_TRY_UMOUNT",
        .handler = get_try_umount,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_SET_INIT_PGRP,
        .name = "SET_INIT_PGRP",
        .handler = do_set_init_pgrp,
        .perm_check = only_root
    },
    {
        .cmd = KSU_IOCTL_GET_SULOG_FD,
        .name = "GET_SULOG_FD",
        .handler = do_get_sulog_fd,
        .perm_check = only_root
    },
    { 
        .cmd = KSU_IOCTL_DISABLE_ESCAPE_TO_ROOT, 
        .name = "DISABLE_ESCAPE_TO_ROOT", 
        .handler = do_disable_escape_to_root, 
        .perm_check = only_root,
        .allow_su_session = true
    },
#ifdef CONFIG_KSU_DYNAMIC_MANAGER
    {
        .cmd = KSU_IOCTL_SET_DYNAMIC_MANAGERS,
        .name = "SET_DYNAMIC_MANAGERS",
        .handler = do_set_dynamic_managers,
        .perm_check = only_root
    },
    {
        .cmd = KSU_IOCTL_GET_DYNAMIC_MANAGERS,
        .name = "GET_DYNAMIC_MANAGERS",
        .handler = do_get_dynamic_managers,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_SET_DYNAMIC_MANAGER_GRANTS,
        .name = "SET_DYNAMIC_MANAGER_GRANTS",
        .handler = do_set_dynamic_manager_grants,
        .perm_check = only_root
    },
#endif
    {
        .cmd = KSU_IOCTL_GET_UTS_VIEW_CONFIG,
        .name = "GET_UTS_VIEW_CONFIG",
        .handler = do_get_uts_view_config,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_SET_UTS_VIEW_CONFIG,
        .name = "SET_UTS_VIEW_CONFIG",
        .handler = do_set_uts_view_config,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_NETISOLATE_CONFIG,
        .name = "GET_NETISOLATE_CONFIG",
        .handler = do_get_netisolate_config,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_SET_NETISOLATE_CONFIG,
        .name = "SET_NETISOLATE_CONFIG",
        .handler = do_set_netisolate_config,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_NETISOLATE_POLICIES,
        .name = "GET_NETISOLATE_POLICIES",
        .handler = do_get_netisolate_policies,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_SET_NETISOLATE_POLICIES,
        .name = "SET_NETISOLATE_POLICIES",
        .handler = do_set_netisolate_policies,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_GET_NETISOLATE_EVENTS,
        .name = "GET_NETISOLATE_EVENTS",
        .handler = do_get_netisolate_events,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_CLEAR_NETISOLATE_EVENTS,
        .name = "CLEAR_NETISOLATE_EVENTS",
        .handler = do_clear_netisolate_events,
        .perm_check = manager_or_root
    },
    {
        .cmd = KSU_IOCTL_LOOKUP_SYMBOLS,
        .name = "LOOKUP_SYMBOLS",
        .handler = do_lookup_symbols,
        .perm_check = only_root
    },
    {
        .cmd = 0,
        .name = NULL,
        .handler = NULL,
        .perm_check = NULL
    } // Sentinel
};
// clang-format on

long ksu_supercall_handle_ioctl(const struct file *filp, unsigned int cmd, void __user *argp)
{
    int i;

#ifdef CONFIG_KSU_DEBUG
    pr_info("ksu ioctl: cmd=0x%x from uid=%d\n", cmd, current_uid().val);
#endif

    for (i = 0; ksu_ioctl_handlers[i].handler; i++) {
        if (cmd == ksu_ioctl_handlers[i].cmd) {
            // Check permission first
            if (ksu_ioctl_handlers[i].perm_check && !ksu_ioctl_handlers[i].perm_check() &&
                !(ksu_ioctl_handlers[i].allow_su_session && ksu_is_su_session_fd(filp))) {
                pr_warn("ksu ioctl: permission denied for cmd=0x%x uid=%d\n", cmd, current_uid().val);
                return -EPERM;
            }
            // Execute handler
            return ksu_ioctl_handlers[i].handler(argp);
        }
    }

    pr_warn("ksu ioctl: unsupported command 0x%x\n", cmd);
    return -ENOTTY;
}

void __init ksu_supercall_dump_commands(void)
{
    int i;

    pr_info("KernelSU IOCTL Commands:\n");
    for (i = 0; ksu_ioctl_handlers[i].handler; i++) {
        pr_info("  %-18s = 0x%08x\n", ksu_ioctl_handlers[i].name, ksu_ioctl_handlers[i].cmd);
    }
}

void ksu_supercall_cleanup_state(void)
{
    struct mount_entry *entry, *tmp;

    down_write(&mount_list_lock);
    list_for_each_entry_safe (entry, tmp, &mount_list, list) {
        list_del(&entry->list);
        kfree(entry->umountable);
        kfree(entry);
    }
    up_write(&mount_list_lock);
}
