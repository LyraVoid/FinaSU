#[cfg(target_os = "android")]
mod android {
    use const_format::concatcp;
    use std::time::Duration;

    pub const BOOT_STAGE_TIMEOUT: Duration = Duration::from_secs(35);
    pub const HOT_INSTALL_SCRIPT_TIMEOUT: Duration = Duration::from_secs(30);
    pub const EMULATED_SOFT_REBOOT_TIMEOUT: Duration = Duration::from_secs(5);
    pub const WAITSYS_READY_TIMEOUT: Duration = Duration::from_secs(10);
    pub const WAITSYS_STOP_TIMEOUT: Duration = Duration::from_secs(5);
    pub const SOFT_REBOOT_LOCK: &str = concatcp!(WORKING_DIR, "soft_reboot.lock");
    pub const BOOTLOG_TIMEOUT: &str = "30s";

    pub const ADB_DIR: &str = "/data/adb/";
    pub const WORKING_DIR: &str = concatcp!(ADB_DIR, "ksu/");
    pub const BINARY_DIR: &str = concatcp!(WORKING_DIR, "bin/");
    pub const LIBRARY_DIR: &str = concatcp!(WORKING_DIR, "lib/");
    pub const LOG_DIR: &str = concatcp!(WORKING_DIR, "log/");
    pub const SULOGD_LOCK_PATH: &str = concatcp!(WORKING_DIR, "sulogd.lock");

    pub const PROFILE_DIR: &str = concatcp!(WORKING_DIR, "profile/");
    pub const PROFILE_SELINUX_DIR: &str = concatcp!(PROFILE_DIR, "selinux/");
    pub const PROFILE_TEMPLATE_DIR: &str = concatcp!(PROFILE_DIR, "templates/");

    pub const KSURC_PATH: &str = concatcp!(WORKING_DIR, ".ksurc");
    pub const DYNAMIC_MANAGER_PATH: &str = concatcp!(WORKING_DIR, ".dynamic_manager");
    /// Held while the dynamic-manager configuration is read and rewritten.
    pub const DYNAMIC_MANAGER_LOCK_PATH: &str = concatcp!(WORKING_DIR, ".dynamic_manager.lock");
    pub const DAEMON_PATH: &str = concatcp!(ADB_DIR, "ksud");
    pub const LIBADBROOT_PATH: &str = concatcp!(LIBRARY_DIR, "libadbroot.so");

    pub const DAEMON_LINK_PATH: &str = concatcp!(BINARY_DIR, "ksud");

    pub const MODULE_DIR: &str = concatcp!(ADB_DIR, "modules/");
    pub const MODULE_UPDATE_DIR: &str = concatcp!(ADB_DIR, "modules_update/");
    pub const METAMODULE_DIR: &str = concatcp!(ADB_DIR, "metamodule/");
    pub const LKM_DIR: &str = concatcp!(WORKING_DIR, "lkm/");
    pub const HOT_INSTALL_TRASH_DIR: &str = concatcp!(WORKING_DIR, "hot_install_trash/");

    pub const PREINIT_DIR_VOLD: &str = "/metadata/vold/ksu/";
    pub const PREINIT_DIR_WATCHDOG: &str = "/metadata/watchdog/ksu/";
    pub const PREINIT_DIR_DEFAULT: &str = "/metadata/ksu/";
    pub const METADATA_FILE_CON: &str = "u:object_r:metadata_file:s0";
    pub const VOLD_METADATA_FILE_CON: &str = "u:object_r:vold_metadata_file:s0";
    pub const MODULES_RC_FILE: &str = "modules.rc";
    pub const MODULES_RC_TMP_FILE: &str = ".modules.rc.tmp";
    pub const PREINIT_SUBDIR: &str = "preinit";
    pub const PREINIT_BOOTCOUNT_FILE: &str = ".preinit_bootcount";
    pub const PREINIT_STATUS_PATH: &str = concatcp!(WORKING_DIR, "preinit_status");
    pub const PREINIT_LOCK_PATH: &str = concatcp!(WORKING_DIR, ".preinit.lock");

    pub const MODULE_WEB_DIR: &str = "webroot";
    pub const MODULE_ACTION_SH: &str = "action.sh";
    pub const DISABLE_FILE_NAME: &str = "disable";
    pub const UPDATE_FILE_NAME: &str = "update";
    pub const REMOVE_FILE_NAME: &str = "remove";
    pub const SKIP_MOUNT_FILE_NAME: &str = "skip_mount";
    pub const MODULE_INIT_RC_DIR: &str = "initrc";
    pub const MODULE_PREINIT_DIR: &str = "preinit";
    pub const PREINIT_CONF: &str = "preinit.conf";
    pub const HOT_INSTALL_MARKER: &str = ".ksu_hot_install";

    // Module config system
    pub const MODULE_CONFIG_DIR: &str = concatcp!(WORKING_DIR, "module_configs/");
    pub const PERSIST_CONFIG_NAME: &str = "persist.config";
    pub const TEMP_CONFIG_NAME: &str = "tmp.config";

    // Metamodule support
    pub const METAMODULE_MOUNT_SCRIPT: &str = "metamount.sh";
    pub const METAMODULE_METAINSTALL_SCRIPT: &str = "metainstall.sh";
    pub const METAMODULE_METAUNINSTALL_SCRIPT: &str = "metauninstall.sh";

    // Folk Mount (built-in module mounting)
    pub const FOLK_MOUNT_CONFIG: &str = concatcp!(WORKING_DIR, "mount_mode");
    pub const FOLK_MOUNT_WORK_DIR: &str = concatcp!(WORKING_DIR, "workdir/");
    pub const FOLK_MOUNT_FS_NAME: &str = "FolkMount";
    // Per-boot runtime record and the concurrency lock for the built-in executor.
    pub const FOLK_MOUNT_RUNTIME: &str = concatcp!(WORKING_DIR, "mount_runtime");
    pub const FOLK_MOUNT_LOCK: &str = concatcp!(WORKING_DIR, "mount.lock");

    // Built-in NoMount backend.
    /// Per-boot record of the driver state and the injection result.
    pub const NOMOUNT_RUNTIME: &str = concatcp!(WORKING_DIR, "nomount_runtime");
    /// UIDs that must see the stock filesystem, one per line. Written by the
    /// manager from the app profiles and replayed into the kernel on boot.
    pub const NOMOUNT_UIDS: &str = concatcp!(WORKING_DIR, "nomount_uids");
    /// Last action that changed NoMount kernel state, for diagnostics.
    pub const NOMOUNT_LAST_ACTION: &str = concatcp!(WORKING_DIR, "nomount_last_action");
    /// Written before a step that can wedge the kernel (loading the embedded
    /// NoMount module, injecting the module tree) and removed once the step is
    /// known to have finished. Finding an earlier boot's record means that step
    /// never finished, so the next boot has to skip it.
    pub const MOUNT_INFLIGHT: &str = concatcp!(WORKING_DIR, "mount_inflight");
    pub const NOMOUNT_LOAD_INFLIGHT: &str = concatcp!(WORKING_DIR, "nomount_load_inflight");
    pub const NOMOUNT_LOAD_LOCK: &str = concatcp!(WORKING_DIR, "nomount_load.lock");
    pub const MOUNT_UI_PENDING: &str = concatcp!(WORKING_DIR, "mount_ui_pending");
    pub const MOUNT_UI_CONFIG: &str = concatcp!(WORKING_DIR, "mount_ui_guard");
    /// Per-boot mount log, kept small enough to ride along in a bug report.
    pub const MOUNT_LOG: &str = concatcp!(LOG_DIR, "mount.log");
    /// Size the mount log may reach before the previous one is rotated aside.
    pub const MOUNT_LOG_MAX_BYTES: u64 = 256 * 1024;
    /// Flag file that arms the opt-in capture of a boot that never finished.
    pub const MOUNT_CAPTURE_FLAG: &str = concatcp!(WORKING_DIR, "mount_capture");
    /// Where a capture writes what a stuck boot was doing.
    pub const MOUNT_CAPTURE_LOG: &str = concatcp!(LOG_DIR, "mount_capture.log");
    /// Size the capture log may reach before the previous one is rotated aside.
    pub const MOUNT_CAPTURE_LOG_MAX_BYTES: u64 = 512 * 1024;
    pub const UMOUNT_CONFIG_PATH: &str = concatcp!(WORKING_DIR, "umount.config.json");

    pub const KSU_BACKUP_DIR: &str = WORKING_DIR;
    pub const KSU_BACKUP_FILE_PREFIX: &str = "ksu_backup_";
    pub const BACKUP_FILENAME: &str = "stock_image.sha1";
    pub const KSU_TEMP_BACKUP_DIR_NAME: &str = "boot_backup";

    pub const DEFAULT_PACKAGE_NAME: &str = env!("KSU_PACKAGE_NAME");
}

#[allow(unused)]
pub const VERSION_CODE: &str = env!("VERSION_CODE");
pub const VERSION_NAME: &str = env!("VERSION_NAME");
#[cfg(target_os = "android")]
pub const FULL_VERSION: &str = const_format::formatcp!(
    "{VERSION_NAME} (uapi: {})",
    crate::ksu_uapi::KERNEL_SU_UAPI_VERSION
);

// Plugin package layout and limits. These do not depend on Android paths so
// they stay available to host tests.
pub const PLUGIN_DIR: &str = "/data/adb/plugins/";
pub const PLUGIN_MANIFEST: &str = "plugin.json";
pub const PLUGIN_ENTRY: &str = "main.lua";
pub const PLUGIN_DISABLE_FILE: &str = "disable";
pub const PLUGIN_CONFIG_FILE: &str = "config.json";
pub const PLUGIN_LOG_FILE: &str = "last_output.log";
// Private state owned by this manager, so it stays out of the shared package
// directory that may hold packages installed by another manager.
pub const PLUGIN_LOCK_DIR: &str = "/data/adb/ksu/plugin_locks/finasu/";
pub const PLUGIN_STAGE_DIR: &str = "/data/adb/ksu/plugin_stage/finasu/";
pub const PLUGIN_STATE_DIR: &str = "/data/adb/ksu/plugin_state/finasu/";

pub const PLUGIN_MAX_ID_LEN: usize = 64;
pub const PLUGIN_MAX_ENTRY_LEN: usize = 128;
pub const PLUGIN_MAX_CALLBACK_LEN: usize = 64;
pub const PLUGIN_MAX_CONFIG_KEY_LEN: usize = 64;
pub const PLUGIN_MAX_MANIFEST_SIZE: u64 = 1024 * 1024;
pub const PLUGIN_MAX_CONFIG_SIZE: u64 = 4 * 1024 * 1024;
pub const PLUGIN_MAX_LOG_SIZE: u64 = 64 * 1024;

// Package limits applied before anything is written to the plugin directory.
pub const PLUGIN_MAX_ARCHIVE_FILES: usize = 2048;
pub const PLUGIN_MAX_FILE_SIZE: u64 = 16 * 1024 * 1024;
pub const PLUGIN_MAX_TOTAL_SIZE: u64 = 64 * 1024 * 1024;
pub const PLUGIN_MAX_COMPRESSION_RATIO: u64 = 200;

#[cfg(target_os = "android")]
pub use android::*;
