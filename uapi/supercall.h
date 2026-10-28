#ifndef __KSU_UAPI_SUPERCALL_H
#define __KSU_UAPI_SUPERCALL_H

#include <linux/ioctl.h>
#include <linux/types.h>

#include "uapi/app_profile.h"
#include "uapi/netisolate.h"
#include "uapi/uts_view.h"

// 2: allowlist v4 root profile flags
// 3: scoped su-session driver fd
// 4: add KSU_GET_INFO_FLAG_BUNDLED
// 5: add EVENT_SERVICES with a start/skip result
// 6: kernel symbol lookup and preinit; metadata paths may use /metadata/vold/ksu
static const __u32 KERNEL_SU_UAPI_VERSION = 6;

/* Magic numbers for reboot hook to install fd */
static const __u32 KSU_INSTALL_MAGIC1 = 0xDEADBEEF;
static const __u32 KSU_INSTALL_MAGIC2 = 0xCAFEBABE;

struct ksu_become_daemon_cmd {
    __u8 token[65]; /* Input: daemon token (null-terminated) */
};

static const __u32 EVENT_POST_FS_DATA = 1;
static const __u32 EVENT_BOOT_COMPLETED = 2;
static const __u32 EVENT_MODULE_MOUNTED = 3;
static const __u32 EVENT_SERVICES = 4;

static const __u32 KSU_GET_INFO_FLAG_LKM = (1U << 0);
static const __u32 KSU_GET_INFO_FLAG_MANAGER = (1U << 1);
static const __u32 KSU_GET_INFO_FLAG_LATE_LOAD = (1U << 2);
static const __u32 KSU_GET_INFO_FLAG_PR_BUILD = (1U << 3);
static const __u32 KSU_GET_INFO_FLAG_BUNDLED = (1U << 4);
static const __u32 KSU_GET_INFO_FLAG_PREINIT = (1U << 5);

struct ksu_get_info_cmd {
    __u32 version; /* Output: KERNEL_SU_VERSION */
    __u32 flags; /* Output: KSU_GET_INFO_FLAG_* bits */
    __u32 features; /* Output: max feature ID supported */
    __u32 uapi_version; /* Output: KERNEL_SU_UAPI_VERSION */
};

struct ksu_get_info_legacy_cmd {
    __u32 version; /* Output: KERNEL_SU_VERSION */
    __u32 flags; /* Output: KSU_GET_INFO_FLAG_* bits */
    __u32 features; /* Output: max feature ID supported */
};

struct ksu_get_full_version_cmd {
    char version_full[64]; /* Output: KSU_VERSION_FULL */
};

struct ksu_report_event_cmd {
    __u32 event; /* Input: EVENT_POST_FS_DATA, EVENT_BOOT_COMPLETED, etc. */
};

struct ksu_set_sepolicy_cmd {
    __u64 data_len; /* Input: bytes of serialized command payload */
    __aligned_u64 data; /* Input: pointer to serialized payload */
};

struct ksu_sepolicy_cmd_hdr {
    __u32 cmd; /* Input: command type, CMD_* */
    __u32 subcmd; /* Input: command subtype */
};
/*
 * After each ksu_sepolicy_cmd_hdr, command arguments are encoded sequentially as:
 * [u32 len][len bytes][\0], where len excludes the trailing '\0'.
 * len == 0 represents ALL.
 * Argument count is derived from cmd:
 * KSU_SEPOLICY_CMD_NORMAL_PERM=4, KSU_SEPOLICY_CMD_XPERM=5,
 * KSU_SEPOLICY_CMD_TYPE_STATE=1, KSU_SEPOLICY_CMD_TYPE=2,
 * KSU_SEPOLICY_CMD_TYPE_ATTR=2, KSU_SEPOLICY_CMD_ATTR=1,
 * KSU_SEPOLICY_CMD_TYPE_TRANSITION=5, KSU_SEPOLICY_CMD_TYPE_CHANGE=4,
 * KSU_SEPOLICY_CMD_GENFSCON=3.
 */

struct ksu_check_safemode_cmd {
    __u8 in_safe_mode; /* Output: true if in safe mode, false otherwise */
};

/* deprecated */
struct ksu_get_allow_list_cmd {
    __u32 uids[128]; /* Output: array of allowed/denied UIDs */
    __u32 count; /* Output: number of UIDs in array */
    __u8 allow; /* Input: true for allow list, false for deny list */
};

struct ksu_new_get_allow_list_cmd {
    __u16 count; /* Input / Output: number of UIDs in array */
    __u16 total_count; /* Output: total number of UIDs in requested list */
    __u32 uids[0]; /* Output: array of allowed/denied UIDs */
};

struct ksu_uid_granted_root_cmd {
    __u32 uid; /* Input: target UID to check */
    __u8 granted; /* Output: true if granted, false otherwise */
};

struct ksu_uid_should_umount_cmd {
    __u32 uid; /* Input: target UID to check */
    __u8 should_umount; /* Output: true if should umount, false otherwise */
};

struct ksu_get_manager_appid_cmd {
    __u32 appid; /* Output: manager app id */
};

struct ksu_get_app_profile_cmd {
    struct app_profile profile; /* Input/Output: app profile structure */
};

struct ksu_set_app_profile_cmd {
    struct app_profile profile; /* Input: app profile structure */
};

struct ksu_get_feature_cmd {
    __u32 feature_id; /* Input: feature ID (enum ksu_feature_id) */
    __u64 value; /* Output: feature value/state */
    __u8 supported; /* Output: KSU_FEATURE_FLAG_* bits, 0 if unsupported */
};

#define KSU_FEATURE_FLAG_SUPPORTED (1 << 0)
#define KSU_FEATURE_FLAG_FORCED (1 << 1)

struct ksu_set_feature_cmd {
    __u32 feature_id; /* Input: feature ID (enum ksu_feature_id) */
    __u64 value; /* Input: feature value/state to set */
};

struct ksu_get_wrapper_fd_cmd {
    __u32 fd; /* Input: userspace fd */
    __u32 flags; /* Input: flags of userspace fd */
};

struct ksu_manage_mark_cmd {
    __u32 operation; /* Input: KSU_MARK_* */
    __s32 pid; /* Input: target pid (0 for all processes) */
    __u32 result; /* Output: for get operation - mark status or reg_count */
};

static const __u32 KSU_MARK_GET = 1;
static const __u32 KSU_MARK_MARK = 2;
static const __u32 KSU_MARK_UNMARK = 3;
static const __u32 KSU_MARK_REFRESH = 4;

struct ksu_nuke_ext4_sysfs_cmd {
    __aligned_u64 arg; /* Input: mnt pointer */
};

struct ksu_add_try_umount_cmd {
    __aligned_u64 arg; /* char ptr, this is the mountpoint */
    __u32 flags; /* this is the flag we use for it */
    __u8 mode; /* denotes what to do with it 0:wipe_list 1:add_to_list 2:delete_entry */
};

struct ksu_get_sulog_fd_cmd {
    __u32 flags; /* Input: reserved for future use, must be 0 */
};

struct ksu_lookup_symbols_cmd {
    __aligned_u64 names;
    __aligned_u64 addrs;
    __u32 count;
    __u32 flags;
};

static const __u32 KSU_LOOKUP_SYMBOLS_MAX = 1024;

#define KSU_DYNAMIC_MANAGER_MAX_SIGNS 64
#define KSU_DYNAMIC_MANAGER_MAX_APPS 64

struct ksu_dynamic_manager_sign {
    __u32 size; /* Input: certificate length */
    __u32 version_code; /* Input: manager APK versionCode (0 if unknown) */
    char hash[65]; /* Input: lowercase hex SHA-256 of the certificate */
};

struct ksu_dynamic_manager_cmd {
    __u32 count; /* Input: number of signatures in signs */
    __aligned_u64 signs; /* Input: array of struct ksu_dynamic_manager_sign */
};

#define KSU_DYNAMIC_MANAGER_FLAG_PRESET (1U << 0)
#define KSU_DYNAMIC_MANAGER_FLAG_TRUSTED (1U << 1)
/*
 * The appid is authorized *and* its signature is registered: only such appids
 * are published as trusted dynamic managers, so an app that merely shares a
 * registered certificate is recognized without being honoured.
 */
#define KSU_DYNAMIC_MANAGER_FLAG_GRANTED (1U << 2)

struct ksu_dynamic_manager_app {
    __u32 appid; /* Output: application id */
    __u32 flags; /* Output: KSU_DYNAMIC_MANAGER_FLAG_* */
};

struct ksu_get_dynamic_managers_cmd {
    __u32 count; /* Input: capacity of apps; Output: number written */
    __u32 total_count; /* Output: total number of tracked apps */
    __aligned_u64 apps; /* Input: array of struct ksu_dynamic_manager_app */
};

/*
 * Replace the authorized dynamic-manager appids. Additive command: it reuses no
 * field of the calls above, so a manager built against uapi 5 keeps working.
 */
struct ksu_dynamic_manager_grants_cmd {
    __u32 count; /* Input: number of appids in appids */
    __u32 reserved; /* Input: must be 0 */
    __aligned_u64 appids; /* Input: array of __u32 appids */
};

static const __u8 KSU_UMOUNT_WIPE = 0; /* ignore everything and wipe list */
static const __u8 KSU_UMOUNT_ADD = 1; /* add entry (path + flags) */
static const __u8 KSU_UMOUNT_DEL = 2; /* delete entry, strcmp */

#define KSU_TRY_UMOUNT_PATH_MAX 256
/*
 * Every zygote setuid walks this list. Keep the hard cap small enough that a
 * corrupt or runaway writer cannot hide an unbounded cost behind app launches.
 */
#define KSU_TRY_UMOUNT_MAX_ENTRIES 256

struct ksu_try_umount_entry {
    char path[KSU_TRY_UMOUNT_PATH_MAX];
    __u32 flags;
    __u32 reserved; /* Input/output: must be 0 */
};

struct ksu_get_try_umount_cmd {
    __u32 size; /* Input: bytes available; Output: bytes required */
    __u32 count; /* Output: number of entries */
    __aligned_u64 entries; /* Input: array of struct ksu_try_umount_entry */
};

/* IOCTL command definitions */
static const __u32 KSU_IOCTL_GRANT_ROOT = _IOC(_IOC_NONE, 'K', 1, 0);
static const __u32 KSU_IOCTL_GET_INFO = _IOR('K', 2, struct ksu_get_info_cmd);
/* deprecated */
static const __u32 KSU_IOCTL_GET_INFO_LEGACY = _IOC(_IOC_READ, 'K', 2, 0);
static const __u32 KSU_IOCTL_REPORT_EVENT = _IOC(_IOC_WRITE, 'K', 3, 0);
static const __u32 KSU_IOCTL_SET_SEPOLICY = _IOC(_IOC_READ | _IOC_WRITE, 'K', 4, 0);
static const __u32 KSU_IOCTL_CHECK_SAFEMODE = _IOC(_IOC_READ, 'K', 5, 0);
/* deprecated */
static const __u32 KSU_IOCTL_GET_ALLOW_LIST = _IOC(_IOC_READ | _IOC_WRITE, 'K', 6, 0);
/* deprecated */
static const __u32 KSU_IOCTL_GET_DENY_LIST = _IOC(_IOC_READ | _IOC_WRITE, 'K', 7, 0);
static const __u32 KSU_IOCTL_NEW_GET_ALLOW_LIST = _IOWR('K', 6, struct ksu_new_get_allow_list_cmd);
static const __u32 KSU_IOCTL_NEW_GET_DENY_LIST = _IOWR('K', 7, struct ksu_new_get_allow_list_cmd);
static const __u32 KSU_IOCTL_UID_GRANTED_ROOT = _IOC(_IOC_READ | _IOC_WRITE, 'K', 8, 0);
static const __u32 KSU_IOCTL_UID_SHOULD_UMOUNT = _IOC(_IOC_READ | _IOC_WRITE, 'K', 9, 0);
static const __u32 KSU_IOCTL_GET_MANAGER_APPID = _IOC(_IOC_READ, 'K', 10, 0);
static const __u32 KSU_IOCTL_GET_APP_PROFILE = _IOC(_IOC_READ | _IOC_WRITE, 'K', 11, 0);
static const __u32 KSU_IOCTL_SET_APP_PROFILE = _IOC(_IOC_WRITE, 'K', 12, 0);
static const __u32 KSU_IOCTL_GET_FEATURE = _IOC(_IOC_READ | _IOC_WRITE, 'K', 13, 0);
static const __u32 KSU_IOCTL_SET_FEATURE = _IOC(_IOC_WRITE, 'K', 14, 0);
static const __u32 KSU_IOCTL_GET_WRAPPER_FD = _IOC(_IOC_WRITE, 'K', 15, 0);
static const __u32 KSU_IOCTL_MANAGE_MARK = _IOC(_IOC_READ | _IOC_WRITE, 'K', 16, 0);
static const __u32 KSU_IOCTL_NUKE_EXT4_SYSFS = _IOC(_IOC_WRITE, 'K', 17, 0);
static const __u32 KSU_IOCTL_ADD_TRY_UMOUNT = _IOC(_IOC_WRITE, 'K', 18, 0);
static const __u32 KSU_IOCTL_SET_INIT_PGRP = _IO('K', 19);
static const __u32 KSU_IOCTL_GET_SULOG_FD = _IOW('K', 20, struct ksu_get_sulog_fd_cmd);
static const __u32 KSU_IOCTL_DISABLE_ESCAPE_TO_ROOT = _IO('K', 21);
static const __u32 KSU_IOCTL_LOOKUP_SYMBOLS = _IOW('K', 22, struct ksu_lookup_symbols_cmd);
static const __u32 KSU_IOCTL_SET_DYNAMIC_MANAGERS = _IOC(_IOC_WRITE, 'K', 22, 0);
static const __u32 KSU_IOCTL_GET_DYNAMIC_MANAGERS = _IOWR('K', 23, struct ksu_get_dynamic_managers_cmd);
static const __u32 KSU_IOCTL_SET_DYNAMIC_MANAGER_GRANTS = _IOC(_IOC_WRITE, 'K', 24, 0);
static const __u32 KSU_IOCTL_GET_UTS_VIEW_CONFIG = _IOWR('K', 25, struct ksu_uts_view_config);
static const __u32 KSU_IOCTL_SET_UTS_VIEW_CONFIG = _IOW('K', 26, struct ksu_uts_view_config);
static const __u32 KSU_IOCTL_GET_NETISOLATE_CONFIG = _IOWR('K', 27, struct ksu_netisolate_config);
static const __u32 KSU_IOCTL_SET_NETISOLATE_CONFIG = _IOW('K', 28, struct ksu_netisolate_config);
static const __u32 KSU_IOCTL_GET_NETISOLATE_POLICIES =
    _IOWR('K', 29, struct ksu_netisolate_policies);
static const __u32 KSU_IOCTL_SET_NETISOLATE_POLICIES =
    _IOW('K', 30, struct ksu_netisolate_policies);
static const __u32 KSU_IOCTL_GET_NETISOLATE_EVENTS =
    _IOWR('K', 31, struct ksu_netisolate_events);
static const __u32 KSU_IOCTL_CLEAR_NETISOLATE_EVENTS =
    _IOW('K', 32, struct ksu_netisolate_events);
static const __u32 KSU_IOCTL_GET_TRY_UMOUNT =
    _IOWR('K', 33, struct ksu_get_try_umount_cmd);
static const __u32 KSU_IOCTL_GET_FULL_VERSION = _IOR('K', 29, struct ksu_get_full_version_cmd);

#endif
