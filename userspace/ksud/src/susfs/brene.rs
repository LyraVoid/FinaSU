//! Built-in port of BRENE's SuSFS hardening profile.
//!
//! BRENE (https://github.com/rrr333nnn333/BRENE, AGPL-3.0) ships its work as a
//! KernelSU module: `post-fs-data.sh`, `boot-completed.sh` and the `susfs`
//! userspace helper. FinaSU already owns a native SUSFS manager, so this module
//! reproduces those two stages with direct ABI calls instead of installing a
//! module, a second copy of `susfs`, or inotify helper scripts.
//!
//! The profile is on by default (`Config::brene`), and is independent of the
//! user's own rule list: turning `automatic` off still leaves BRENE's built-in
//! hiding and spoofing active, exactly like having the module installed.
//!
//! Deliberate divergences from BRENE v0.0.72:
//! * Developer options, USB debugging and wireless debugging default to
//!   "leave unchanged" instead of switching them off, because losing ADB is not
//!   recoverable for every user. Set them explicitly to restore BRENE's
//!   behaviour.
//! * `set_priority_uid_filter` is sent only when the kernel advertises support
//!   for it; the shipped LKM (v2.3.0-r18.4) does not implement command 0x60030,
//!   so the step is reported as skipped rather than silently failing.
#![cfg_attr(not(target_os = "android"), allow(dead_code))]

use super::model::Brene;
use super::{Config, Detection, abi, detect, store};
use anyhow::{Context, Result, bail};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fs;
use std::io::Write;
use std::os::unix::fs::MetadataExt;
use std::path::{Path, PathBuf};

#[cfg(target_os = "android")]
use prop_rs_android::{resetprop::ResetProp, sys_prop};
#[cfg(target_os = "android")]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
#[cfg(target_os = "android")]
use std::process::Command;

const REPORT: &str = const_format::concatcp!(store::DIR, "/brene.json");
const WATCH_LOCK: &str = const_format::concatcp!(store::DIR, "/brene-watch.lock");
const FAKE_DIR: &str = const_format::concatcp!(store::DIR, "/fake");

const KSU_PACKAGES: [&str; 3] = ["me.weishu.kernelsu", "com.rifsxd.ksunext", "me.yuki.fina"];

const CUSTOM_ROM_NAMES: [&str; 23] = [
    "lineage",
    "infinity",
    "evolution",
    "crdroid",
    "mistos",
    "axion",
    "pixelos",
    "rising",
    "lunaris",
    "halcyon",
    "havoc",
    "alphadroid",
    "bliss",
    "calyx",
    "derpfest",
    "graphene",
    "lmodroid",
    "lumine",
    "matrixx",
    "clover",
    "yaap",
    "aospa",
    "lineageos",
];

const MAX_SCAN_FILES: usize = 4096;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Report {
    pub boot_id: String,
    pub stage: String,
    pub applied: usize,
    pub skipped: Vec<String>,
    pub failures: BTreeMap<String, String>,
}

struct Run<'a> {
    brene: &'a Brene,
    detection: Detection,
    applied: Vec<String>,
    skipped: Vec<String>,
    failures: BTreeMap<String, String>,
}

/// Entry point from the SUSFS boot pipeline. Runs before the user's
/// `automatic` check so the built-in profile is active without any interaction.
pub fn boot(stage: &str, config: &Config) {
    if !config.brene.enabled {
        return;
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = stage;
        return;
    }
    #[cfg(target_os = "android")]
    {
        if crate::utils::is_safe_mode() {
            log::warn!("BRENE: safe mode, skipping built-in profile");
            return;
        }
        let detection = detect();
        if detection.status != "compatible" {
            log::warn!(
                "BRENE: SUSFS unavailable ({}), built-in profile not applied",
                detection.status
            );
            return;
        }
        if let Err(error) = crate::utils::switch_mnt_ns(1) {
            log::warn!("BRENE: switch to init mount namespace failed: {error:#}");
        }
        if let Err(error) = fs::create_dir_all(store::DIR) {
            log::warn!("BRENE: create state directory failed: {error:#}");
        }
        let mut run = Run {
            brene: &config.brene,
            detection,
            applied: Vec::new(),
            skipped: Vec::new(),
            failures: BTreeMap::new(),
        };
        match stage {
            "post-mount" => run.post_fs_data(config),
            // `service` runs before the framework and /storage are usable;
            // BRENE's boot-completed half must wait for the real stage.
            "boot-completed" => run.boot_completed(config),
            // An explicit Apply from the manager should make saved switches
            // take effect without a reboot.
            "live" => {
                run.post_fs_data(config);
                run.boot_completed(config);
            }
            _ => return,
        }
        run.finish(stage);
    }
}
pub fn report() -> Option<Report> {
    fs::read_to_string(REPORT)
        .ok()
        .and_then(|text| serde_json::from_str(&text).ok())
}

impl Run<'_> {
    fn op(&mut self, name: &str, result: Result<()>) {
        match result {
            Ok(()) => self.applied.push(name.to_owned()),
            Err(error) => {
                log::debug!("BRENE: {name}: {error:#}");
                self.failures.insert(name.to_owned(), error.to_string());
            }
        }
    }

    fn op_count(&mut self, name: &str, result: Result<usize>) {
        match result {
            Ok(count) => {
                log::debug!("BRENE: {name}: {count} entries");
                self.applied.push(name.to_owned());
            }
            Err(error) => {
                log::debug!("BRENE: {name}: {error:#}");
                self.failures.insert(name.to_owned(), error.to_string());
            }
        }
    }

    fn skip(&mut self, name: &str, reason: &str) {
        log::info!("BRENE: {name} skipped: {reason}");
        self.skipped.push(name.to_owned());
    }

    /// Mirrors BRENE's post-fs-data.sh.
    fn post_fs_data(&mut self, config: &Config) {
        let brene = self.brene.clone();

        if brene.hide_sus_mounts {
            let result = toggle(
                &self.detection,
                abi::CMD_HIDE_SUS_MNTS_FOR_NON_SU_PROCS,
                true,
            );
            self.op("hide_sus_mounts", result);
        }
        if brene.avc_log_spoofing {
            let result = avc_spoof(&self.detection, true);
            self.op("avc_log_spoofing", result);
        }
        if brene.spoof_cmdline {
            let result = spoof_cmdline(&self.detection);
            self.op("spoof_cmdline", result);
        }
        self.apply_uname(config);

        #[cfg(target_os = "android")]
        {
            let result = spoof_properties(&brene);
            self.op_count("spoof_properties", result);
        }

        if brene.hide_addon_d {
            let result = (|| {
                sus_map(&self.detection, "/system/addon.d")?;
                sus_path(&self.detection, "/system/addon.d", true)
            })();
            self.op("hide_addon_d", result);
        }
        if brene.hide_suspicious_pty {
            let mut failed = None;
            for index in 0..6 {
                if let Err(error) = sus_path(&self.detection, &format!("/dev/pts/{index}"), true) {
                    failed = Some(error);
                }
            }
            match failed {
                Some(error) => self.op("hide_suspicious_pty", Err(error)),
                None => self.op("hide_suspicious_pty", Ok(())),
            }
        }
        if brene.hide_custom_rom_paths {
            let paths = find_custom_rom_paths(false);
            let result = self.apply_paths(&paths);
            self.op_count("hide_custom_rom_paths", result.map(|_| paths.len()));
        }
        if brene.hide_custom_rom_paths_deep {
            let paths = find_custom_rom_paths(true);
            let result = self.apply_paths(&paths);
            self.op_count("hide_custom_rom_paths_deep", result.map(|_| paths.len()));
        }
        if brene.spoof_hosts {
            let result = spoof_hosts(&self.detection);
            self.op("spoof_hosts", result);
        }

        let (paths, loops, maps) = (
            brene.sus_paths.clone(),
            brene.sus_path_loops.clone(),
            brene.sus_maps.clone(),
        );
        let result = (|| -> Result<()> {
            for path in &paths {
                sus_path(&self.detection, path, false)?;
            }
            for path in &loops {
                sus_path(&self.detection, path, true)?;
            }
            for path in &maps {
                sus_map(&self.detection, path)?;
            }
            Ok(())
        })();
        self.op("custom_rules", result);

        let redirects = brene.redirects.clone();
        let result = (|| -> Result<()> {
            for redirect in &redirects {
                abi_redirect(&redirect.path, &redirect.destination, redirect.uid_scheme)?;
            }
            Ok(())
        })();
        self.op("custom_redirects", result);
    }

    /// Mirrors BRENE's boot-completed.sh (everything that needs /storage or a
    /// booted system).
    fn boot_completed(&mut self, config: &Config) {
        let brene = self.brene.clone();

        #[cfg(target_os = "android")]
        {
            for (name, enabled) in [
                ("su_compat", brene.su_compat),
                ("kernel_umount", brene.kernel_umount),
                ("selinux_hide", brene.selinux_hide),
            ] {
                let result = crate::feature::set_feature(name, u64::from(enabled));
                self.op(&format!("feature:{name}"), result);
            }
            let result = crate::feature::save_config();
            self.op("feature:save", result);

            if let Some(enabled) = brene.developer_options {
                let result = run_tool(
                    "/system/bin/settings",
                    &[
                        "put",
                        "global",
                        "development_settings_enabled",
                        boolean(enabled),
                    ],
                );
                self.op("settings:developer_options", result);
            }
            if let Some(enabled) = brene.usb_debugging {
                let result = run_tool(
                    "/system/bin/settings",
                    &["put", "global", "adb_enabled", boolean(enabled)],
                );
                self.op("settings:usb_debugging", result);
            }
            if let Some(enabled) = brene.wireless_debugging {
                let result = run_tool(
                    "/system/bin/settings",
                    &["put", "global", "adb_wifi_enabled", boolean(enabled)],
                );
                self.op("settings:wireless_debugging", result);
            }
            if brene.disable_child_process_restrictions {
                let result = set_property(
                    "persist.sys.fflag.override.settings_enable_monitor_phantom_procs",
                    "false",
                );
                self.op("child_process_restrictions", result);
            }
            if brene.shrink_log_buffers {
                let result = (|| {
                    run_tool("/system/bin/logcat", &["-G", "64K"])?;
                    set_property("persist.logd.size", "65536")
                })();
                self.op("shrink_log_buffers", result);
            }
            if brene.selinux_enforcing {
                let result = enforce_selinux();
                self.op("selinux_enforcing", result);
            }
            if brene.max_saturation {
                let result = run_tool(
                    "/system/bin/service",
                    &["call", "SurfaceFlinger", "1022", "f", "2.0"],
                );
                self.op("max_saturation", result);
            }
            if brene.show_refresh_rate {
                let result = run_tool(
                    "/system/bin/service",
                    &["call", "SurfaceFlinger", "1034", "i32", "1"],
                );
                self.op("show_refresh_rate", result);
            }
            if brene.remove_rom_props {
                let result = remove_rom_properties();
                self.op_count("remove_rom_props", result);
            }
            if brene.remove_pif_props {
                let result = remove_pif_properties();
                self.op_count("remove_pif_props", result);
            }
            if !brene.verified_boot_hash.is_empty() {
                let result = set_property("ro.boot.vbmeta.digest", &brene.verified_boot_hash);
                self.op("verified_boot_hash", result);
            }
        }

        if brene.hide_local_tmp || brene.fix_local_tmp {
            let mut result = Ok(());
            if brene.hide_local_tmp {
                let paths = children("/data/local/tmp");
                result = self.apply_loops(&paths);
            }
            if brene.fix_local_tmp {
                if let Err(error) = fix_local_tmp() {
                    result = Err(error);
                }
            }
            self.op("local_tmp", result);
        }
        if brene.hide_android_dirs || brene.hide_non_standard_storage {
            let mut result = Ok(());
            if !wait_for("/storage/emulated/0/Android", 10) {
                result = Err(anyhow::anyhow!("/storage is not ready"));
            }
            if result.is_ok() && brene.hide_android_dirs {
                let paths = non_standard_android_dirs();
                result = self.apply_loops(&paths);
            }
            if result.is_ok() && brene.hide_non_standard_storage {
                let paths = non_standard_storage_dirs();
                result = self.apply_loops(&paths);
            }
            self.op("storage_dirs", result);
        }
        if brene.hide_custom_recovery {
            let paths = custom_recovery_paths();
            let result = self.apply_loops(&paths);
            self.op("hide_custom_recovery", result);
        }
        if brene.hide_framework_res {
            let paths = find_named(Path::new("/system"), "framework-res.apk", 8);
            let result = (|| -> Result<()> {
                for path in &paths {
                    sus_map(&self.detection, path)?;
                }
                Ok(())
            })();
            self.op_count("hide_framework_res", result.map(|()| paths.len()));
        }
        if brene.hide_lineage_strings {
            let result = hide_lineage_strings(&self.detection);
            self.op_count("hide_lineage_strings", result);
        }
        if brene.spoof_libstagefright {
            let result = redirect_empty(
                &self.detection,
                "/system/lib64/libstagefright.so",
                "libstagefright.so",
            );
            self.op("spoof_libstagefright", result);
        }
        if brene.hide_third_party_apps {
            #[cfg(target_os = "android")]
            match priority_uid_filter() {
                Ok(()) => self.op("hide_third_party_apps", Ok(())),
                Err(error) => self.skip("hide_third_party_apps", &error.to_string()),
            }
            #[cfg(not(target_os = "android"))]
            self.skip("hide_third_party_apps", "not running on Android");
        }

        if !brene.kernel_umounts.is_empty() {
            #[cfg(target_os = "android")]
            {
                crate::ksucalls::report_module_mounted();
                let mut result = Ok(());
                for path in &brene.kernel_umounts {
                    if let Err(error) = crate::ksucalls::umount_list_add(path, 2) {
                        result = Err(error);
                    }
                }
                self.op("kernel_umounts", result);
            }
        }

        #[cfg(target_os = "android")]
        {
            // BRENE re-runs the property spoof after vold/framework settle,
            // then commits with the equivalent of resetprop -c --force.
            let result = spoof_properties(&brene);
            self.op_count("spoof_properties_late", result);
            if let Some(current) = sys_prop::get("ro.crypto.state") {
                if current != "encrypted" {
                    let result = set_property("ro.crypto.state", "encrypted");
                    self.op("crypto_state", result);
                }
            }
            let result = rebuild_properties();
            self.op("properties_commit", result);
        }

        self.cleanup();
        let _ = config;
    }

    fn cleanup(&mut self) {
        let brene = self.brene.clone();
        remove_device_artifacts(&brene);
        #[cfg(target_os = "android")]
        ensure_watcher();
    }

    fn apply_paths(&self, paths: &[PathBuf]) -> Result<()> {
        for path in paths {
            sus_map(&self.detection, path)?;
            sus_path(&self.detection, path, true)?;
        }
        Ok(())
    }

    fn apply_loops(&self, paths: &[PathBuf]) -> Result<()> {
        for path in paths {
            sus_path(&self.detection, path, true)?;
        }
        Ok(())
    }

    fn apply_uname(&mut self, config: &Config) {
        let brene = self.brene.clone();
        if !brene.spoof_uname
            && brene.custom_uname_release.is_empty()
            && brene.custom_uname_version.is_empty()
        {
            return;
        }
        if config.settings.uname.is_some() {
            self.skip("spoof_uname", "an explicit SUSFS uname is configured");
            return;
        }
        #[cfg(target_os = "android")]
        {
            if crate::uts_view::provider_active().unwrap_or(false) {
                self.skip("spoof_uname", "UTS View owns uname spoofing");
                return;
            }
        }
        let result = uname_values(&brene)
            .and_then(|(release, version)| set_uname(&self.detection, &release, &version));
        self.op("spoof_uname", result);
    }

    fn finish(self, stage: &str) {
        self.finish_inner(stage);
    }

    fn finish_inner(&self, stage: &str) {
        let report = Report {
            boot_id: fs::read_to_string("/proc/sys/kernel/random/boot_id")
                .map(|value| value.trim().to_owned())
                .unwrap_or_default(),
            stage: stage.to_owned(),
            applied: self.applied.len(),
            skipped: self.skipped.clone(),
            failures: self.failures.clone(),
        };
        if let Ok(json) = serde_json::to_vec(&report) {
            let _ = write_private(REPORT, &json);
        }
        if self.failures.is_empty() {
            log::info!(
                "BRENE: {stage}: {} operations applied, {} skipped",
                report.applied,
                report.skipped.len()
            );
        } else {
            log::warn!(
                "BRENE: {stage}: {} applied, {} skipped, {} failed: {}",
                report.applied,
                report.skipped.len(),
                report.failures.len(),
                report
                    .failures
                    .keys()
                    .cloned()
                    .collect::<Vec<_>>()
                    .join(",")
            );
        }
    }
}

fn write_private(path: &str, contents: &[u8]) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open(path)?;
        file.set_permissions(fs::Permissions::from_mode(0o600))?;
        file.write_all(contents)?;
        file.sync_all()?;
        Ok(())
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (path, contents);
        Ok(())
    }
}

fn boolean(value: bool) -> &'static str {
    if value { "1" } else { "0" }
}

// ---------------------------------------------------------------------------
// SUSFS ABI helpers
// ---------------------------------------------------------------------------

fn require(detection: &Detection, feature: &str) -> Result<()> {
    if !detection.supports(feature) {
        bail!("Capability unavailable: {feature}");
    }
    Ok(())
}

fn call_toggle(command: u32, enabled: bool) -> Result<()> {
    let mut payload = abi::Toggle { enabled, err: 126 };
    abi::call(command, &mut payload);
    abi::checked(payload.err)
}

fn toggle(detection: &Detection, command: u32, enabled: bool) -> Result<()> {
    // hide_sus_mnts has no CONFIG_ token in the LKM feature list; the kernel
    // still answers the command, so let it decide instead of refusing here.
    let _ = detection;
    call_toggle(command, enabled)
}

fn text_command<const N: usize>(command: u32, value: &str) -> Result<()> {
    let mut payload = abi::Text::<N>::empty();
    payload.text = abi::bytes::<N>(value)?;
    abi::call(command, &mut payload);
    abi::checked(payload.err)
}

fn sus_path(detection: &Detection, path: impl AsRef<Path>, looping: bool) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SUS_PATH")?;
    let path = path_to_abi(path.as_ref())?;
    text_command::<256>(if looping { 0x0005_5553 } else { 0x0005_5550 }, &path)
}

fn sus_map(detection: &Detection, path: impl AsRef<Path>) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SUS_MAP")?;
    let path = path_to_abi(path.as_ref())?;
    text_command::<256>(0x0006_0020, &path)
}

fn abi_redirect(target: &str, destination: &str, uid_scheme: u32) -> Result<()> {
    if uid_scheme > 4 {
        bail!("UID scheme is outside the SUSFS ABI");
    }
    let mut payload = abi::Redirect {
        target: abi::bytes::<256>(target)?,
        destination: abi::bytes::<256>(destination)?,
        uid_scheme: uid_scheme as i32,
        err: 126,
    };
    abi::call(0x0005_55c0, &mut payload);
    abi::checked(payload.err)
}

fn set_uname(detection: &Detection, release: &str, version: &str) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SPOOF_UNAME")?;
    let mut payload = abi::Uname {
        release: abi::bytes::<65>(release)?,
        version: abi::bytes::<65>(version)?,
        err: 126,
    };
    abi::call(0x0005_5590, &mut payload);
    abi::checked(payload.err)
}

#[derive(Clone, Copy, Default)]
struct KstatOverride {
    ino: Option<u64>,
    size: Option<i64>,
    blocks: Option<i64>,
    blksize: Option<i64>,
}

fn static_kstat(
    detection: &Detection,
    path: impl AsRef<Path>,
    overrides: KstatOverride,
) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SUS_KSTAT")?;
    let path = path_to_abi(path.as_ref())?;
    let md = fs::metadata(&path)?;
    let mut payload = abi::Kstat {
        is_static: 1,
        path: abi::bytes::<256>(&path)?,
        ino: md.ino() as libc::c_ulong,
        dev: md.dev() as libc::c_ulong,
        nlink: md.nlink() as u32,
        size: md.size() as i64,
        atime_sec: md.atime() as libc::c_long,
        atime_nsec: md.atime_nsec() as libc::c_ulong,
        mtime_sec: md.mtime() as libc::c_long,
        mtime_nsec: md.mtime_nsec() as libc::c_ulong,
        ctime_sec: md.ctime() as libc::c_long,
        ctime_nsec: md.ctime_nsec() as libc::c_ulong,
        blocks: md.blocks() as i64,
        blksize: md.blksize() as libc::c_long,
        ..abi::Kstat::default()
    };
    if let Some(value) = overrides.ino {
        payload.ino = value as libc::c_ulong;
        payload.flags |= abi::KSTAT_SPOOF_INO;
    }
    if let Some(value) = overrides.size {
        payload.size = value;
        payload.flags |= abi::KSTAT_SPOOF_SIZE;
    }
    if let Some(value) = overrides.blocks {
        payload.blocks = value;
        payload.flags |= abi::KSTAT_SPOOF_BLOCKS;
    }
    if let Some(value) = overrides.blksize {
        payload.blksize = value as libc::c_long;
        payload.flags |= abi::KSTAT_SPOOF_BLKSIZE;
    }
    abi::call(abi::CMD_ADD_SUS_KSTAT_STATICALLY, &mut payload);
    abi::checked(payload.err)
}

fn avc_spoof(detection: &Detection, enabled: bool) -> Result<()> {
    if detection.proc("susfs_avc_spoof") {
        let mut file = fs::OpenOptions::new()
            .write(true)
            .open("/proc/susfs_avc_spoof")?;
        file.write_all(if enabled { b"1\n" } else { b"0\n" })?;
        Ok(())
    } else {
        // The LKM supercall handlers exist even when the feature string is not
        // advertised; let the kernel answer and keep the error if it refuses.
        let _ = detection;
        call_toggle(0x0006_0010, enabled)
    }
}

// ---------------------------------------------------------------------------
// Path-driven features
// ---------------------------------------------------------------------------

fn path_to_abi(path: &Path) -> Result<String> {
    let text = path
        .to_str()
        .with_context(|| format!("non-UTF-8 path {}", path.display()))?;
    if !text.starts_with('/')
        || text.len() >= 256
        || text.contains('\0')
        || text.chars().any(char::is_control)
        || text.split('/').any(|part| matches!(part, "." | ".."))
    {
        bail!("Invalid SUSFS path");
    }
    Ok(text.to_owned())
}

fn children(dir: &str) -> Vec<PathBuf> {
    let Ok(entries) = fs::read_dir(dir) else {
        return Vec::new();
    };
    let mut paths = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if path_to_abi(&path).is_ok() {
            paths.push(path);
        }
    }
    paths.sort();
    paths
}

fn wait_for(path: &str, seconds: u64) -> bool {
    for _ in 0..seconds * 2 {
        if Path::new(path).exists() {
            return true;
        }
        std::thread::sleep(std::time::Duration::from_millis(500));
    }
    false
}

fn non_standard_android_dirs() -> Vec<PathBuf> {
    let mut paths = children("/storage/emulated/0/Android");
    paths.retain(|path| {
        !matches!(
            path.file_name().and_then(|name| name.to_str()),
            Some("data" | "media" | "obb")
        )
    });
    paths
}

fn non_standard_storage_dirs() -> Vec<PathBuf> {
    #[allow(unused_mut)]
    let mut standard = vec![
        "Alarms",
        "Android",
        "Audiobooks",
        "DCIM",
        "Documents",
        "Download",
        "Movies",
        "Music",
        "Notifications",
        "Pictures",
        "Podcasts",
        "Recordings",
        "Ringtones",
    ];
    #[cfg(target_os = "android")]
    if sys_prop::get("ro.miui.ui.version.name").is_some_and(|value| !value.is_empty()) {
        standard.push("MIUI");
    }
    children("/storage/emulated/0")
        .into_iter()
        .filter(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .is_none_or(|name| !standard.contains(&name))
        })
        .collect()
}

fn custom_recovery_paths() -> Vec<PathBuf> {
    [
        "/storage/emulated/0/Fox",
        "/storage/emulated/0/TWRP",
        "/data/recovery",
        "/vendor/bin/install-recovery.sh",
        "/system/bin/install-recovery.sh",
    ]
    .into_iter()
    .map(PathBuf::from)
    .filter(|path| path.exists())
    .collect()
}

fn find_named(root: &Path, needle: &str, max_depth: usize) -> Vec<PathBuf> {
    let mut output = Vec::new();
    let needle = needle.to_ascii_lowercase();
    walk(root, max_depth, &mut |path| {
        if path
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name.to_ascii_lowercase().contains(&needle))
        {
            output.push(path.to_owned());
        }
    });
    output
}

fn walk(root: &Path, depth: usize, visit: &mut impl FnMut(&Path)) {
    if depth == 0 {
        return;
    }
    let Ok(entries) = fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let Ok(file_type) = entry.file_type() else {
            continue;
        };
        if file_type.is_symlink() {
            continue;
        }
        visit(&path);
        if file_type.is_dir() {
            walk(&path, depth - 1, visit);
        }
    }
}

fn find_custom_rom_paths(deep: bool) -> Vec<PathBuf> {
    let roots: &[&str] = if deep {
        &["/data/misc", "/data/dalvik-cache", "/data/resource-cache"]
    } else {
        &["/system", "/system_ext", "/vendor", "/product"]
    };
    let mut output = Vec::new();
    for root in roots {
        let mut found = Vec::new();
        walk(Path::new(root), if deep { 4 } else { 6 }, &mut |path| {
            if found.len() >= MAX_SCAN_FILES {
                return;
            }
            if let Some(name) = path.file_name().and_then(|name| name.to_str()) {
                let lower = name.to_ascii_lowercase();
                if CUSTOM_ROM_NAMES.iter().any(|needle| lower.contains(needle)) {
                    found.push(path.to_owned());
                }
            }
        });
        output.extend(found);
    }
    if !deep {
        for path in children("/data") {
            if let Some(name) = path.file_name().and_then(|name| name.to_str()) {
                let lower = name.to_ascii_lowercase();
                if CUSTOM_ROM_NAMES.iter().any(|needle| lower.contains(needle)) {
                    output.push(path);
                }
            }
        }
    }
    output.sort();
    output.dedup();
    output
}

fn hide_lineage_strings(detection: &Detection) -> Result<usize> {
    require(detection, "CONFIG_KSU_SUSFS_OPEN_REDIRECT")?;
    let mut count = 0usize;
    let mut files = Vec::new();
    for root in ["/system", "/system_ext", "/vendor", "/product"] {
        walk(Path::new(root), 8, &mut |path| {
            if count >= MAX_SCAN_FILES {
                return;
            }
            let Some(name) = path.file_name().and_then(|name| name.to_str()) else {
                return;
            };
            let lower = name.to_ascii_lowercase();
            if lower.ends_with(".rc")
                || lower.ends_with("sepolicy.cil")
                || lower.ends_with("file_contexts")
            {
                files.push(path.to_owned());
                count += 1;
            }
        });
    }
    let mut redirected = 0usize;
    for path in files {
        if fs::symlink_metadata(&path).is_ok_and(|md| md.is_file()) {
            if let Ok(contents) = fs::read(&path) {
                if contents
                    .windows(7)
                    .any(|window| window.eq_ignore_ascii_case(b"lineage"))
                {
                    let fake = fake_file(&path, b"")?;
                    abi_redirect(&path_to_abi(&path)?, &fake, 3)?;
                    redirected += 1;
                }
            }
        }
    }
    Ok(redirected)
}

// ---------------------------------------------------------------------------
// Property spoofing
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
fn resetprop() -> Result<ResetProp> {
    sys_prop::init().context("initialize property API")?;
    Ok(ResetProp {
        skip_svc: true,
        persistent: false,
        persist_only: false,
        verbose: false,
        show_context: false,
        rebuild: false,
    })
}

#[cfg(target_os = "android")]
fn set_property(key: &str, value: &str) -> Result<()> {
    let rp = resetprop()?;
    if rp.set(key, value)? {
        rp.rebuild_all(true)?;
    }
    Ok(())
}

#[cfg(not(target_os = "android"))]
fn set_property(_key: &str, _value: &str) -> Result<()> {
    bail!("properties are only available on Android")
}

#[cfg(target_os = "android")]
fn spoof_properties(brene: &Brene) -> Result<usize> {
    let rp = resetprop()?;
    let mut writes = 0usize;

    if brene.spoof_system_properties {
        for (key, value) in [
            ("ro.secure", "1"),
            ("ro.debuggable", "0"),
            ("ro.adb.secure", "1"),
            ("persist.sys.usb.config", "mtp"),
            ("ro.boot.verifiedbootstate", "green"),
            ("ro.boot.flash.locked", "1"),
            ("ro.boot.veritymode", "enforcing"),
            ("ro.boot.avb_version", "1.3"),
            ("ro.build.type", "user"),
            ("ro.build.tags", "release-keys"),
            ("ro.build.keys", "release-keys"),
            ("ro.allow.mock.location", "0"),
            ("ro.boot.warranty_bit", "0"),
            ("ro.warranty_bit", "0"),
            ("ro.force.debuggable", "0"),
            ("ro.secureboot.devicelock", "1"),
            ("ro.secureboot.lockstate", "locked"),
            ("ro.is_ever_orange", "0"),
            ("ro.bootmode", "normal"),
            ("vendor.boot.verifiedbootstate", "green"),
            ("ro.vendor.boot.warranty_bit", "0"),
            ("ro.vendor.warranty_bit", "0"),
            ("init.svc.adbd", "stopped"),
            ("init.svc_debug_pid.adbd", ""),
            ("ro.oem_unlock_supported", "0"),
            // Realme
            ("ro.boot.realme.lockstate", "1"),
            ("ro.boot.realmebootstate", "green"),
        ] {
            if let Some(current) = sys_prop::get(key) {
                if current != value {
                    if rp.set(key, value).is_ok() {
                        writes += 1;
                    }
                }
            }
        }

        if let Some(suffix) = sys_prop::get("ro.boot.slot_suffix") {
            if let Ok(size) = Command::new("/system/bin/blockdev")
                .args(["--getsize64", &format!("/dev/block/by-name/vbmeta{suffix}")])
                .output()
            {
                let size = String::from_utf8_lossy(&size.stdout).trim().to_owned();
                if !size.is_empty() && size.chars().all(|c| c.is_ascii_digit()) {
                    if rp.set("ro.boot.vbmeta.size", &size).is_ok() {
                        writes += 1;
                    }
                }
            }
        }
        for (key, value) in [
            ("ro.boot.vbmeta.avb_version", "1.3"),
            ("ro.boot.vbmeta.hash_alg", "sha256"),
            ("ro.boot.vbmeta.device_state", "locked"),
            ("ro.boot.vbmeta.invalidate_on_error", "yes"),
            ("vendor.boot.vbmeta.device_state", "locked"),
        ] {
            if rp.set(key, value).is_ok() {
                writes += 1;
            }
        }
        for key in [
            "ro.boot.verifiedbooterror",
            "ro.boot.verifyerrorpart",
            "vendor.boot.verifyerrorpart",
            "vendor.boot.verifiedbooterror",
            "crashrecovery.rescue_boot_count",
            "service.adb.root",
            "service.adb.tcp.port",
        ] {
            if rp.delete(key).unwrap_or(false) {
                writes += 1;
            }
        }
        let sdk = sys_prop::get("ro.build.version.sdk")
            .and_then(|value| value.parse::<u32>().ok())
            .unwrap_or(0);
        if sdk >= 36 {
            let _ = rp.delete("sys.oem_unlock_allowed");
        } else if sys_prop::get("sys.oem_unlock_allowed").is_some() {
            if rp.set("sys.oem_unlock_allowed", "0").is_ok() {
                writes += 1;
            }
        }
    }

    if brene.spoof_fingerprint {
        let mut fingerprint = sys_prop::get("ro.build.fingerprint").unwrap_or_default();
        if !fingerprint.is_empty() {
            fingerprint = fingerprint.replace("userdebug", "user");
            for rom in ["evolution", "crdroid", "lineage"] {
                fingerprint = fingerprint.replace(rom, "");
            }
            for key in [
                "ro.bootimage.build.fingerprint",
                "ro.build.fingerprint",
                "ro.odm.build.fingerprint",
                "ro.odm_dlkm.build.fingerprint",
                "ro.product.build.fingerprint",
                "ro.system.build.fingerprint",
                "ro.system_dlkm.build.fingerprint",
                "ro.system_ext.build.fingerprint",
                "ro.vendor.build.fingerprint",
                "ro.vendor_dlkm.build.fingerprint",
            ] {
                if let Some(current) = sys_prop::get(key) {
                    if current != fingerprint && rp.set(key, &fingerprint).is_ok() {
                        writes += 1;
                    }
                }
            }
        }
    }

    if brene.spoof_build_dates {
        if let Some(date) = sys_prop::get("ro.build.date") {
            for key in [
                "ro.bootimage.build.date",
                "ro.build.date",
                "ro.odm.build.date",
                "ro.odm_dlkm.build.date",
                "ro.product.build.date",
                "ro.system.build.date",
                "ro.system_dlkm.build.date",
                "ro.system_ext.build.date",
                "ro.vendor.build.date",
                "ro.vendor_dlkm.build.date",
            ] {
                if let Some(current) = sys_prop::get(key) {
                    if current != date && rp.set(key, &date).is_ok() {
                        writes += 1;
                    }
                }
            }
        }
        if let Some(utc) = sys_prop::get("ro.build.date.utc") {
            for key in [
                "ro.bootimage.build.date.utc",
                "ro.build.date.utc",
                "ro.odm.build.date.utc",
                "ro.odm_dlkm.build.date.utc",
                "ro.product.build.date.utc",
                "ro.system.build.date.utc",
                "ro.system_dlkm.build.date.utc",
                "ro.system_ext.build.date.utc",
                "ro.vendor.build.date.utc",
                "ro.vendor_dlkm.build.date.utc",
                "persist.vendor.build.date.utc",
            ] {
                if let Some(current) = sys_prop::get(key) {
                    if current != utc && rp.set(key, &utc).is_ok() {
                        writes += 1;
                    }
                }
            }
        }
    }

    if brene.spoof_security_patch {
        let (os_patch, vendor_patch) = security_patch(chrono::Local::now().date_naive());
        for (key, value) in [
            ("ro.build.version.security_patch", os_patch),
            ("ro.vendor.build.security_patch", vendor_patch),
        ] {
            if let Some(current) = sys_prop::get(key) {
                if current != value && rp.set(key, &value).is_ok() {
                    writes += 1;
                }
            }
        }
    }

    if rp.rebuild_all(true).unwrap_or(false) {
        writes += 1;
    }
    Ok(writes)
}

#[cfg(target_os = "android")]
fn rebuild_properties() -> Result<()> {
    let rp = resetprop()?;
    rp.rebuild_all(true)?;
    Ok(())
}

#[cfg(target_os = "android")]
fn remove_rom_properties() -> Result<usize> {
    let rp = resetprop()?;
    let pattern = regex_lite::Regex::new(
        "(?i)lineage|infinity|evolution|crdroid|mistos|axion|pixelos|rising|lunaris|halcyon|havoc|alphadroid|bliss|calyx|derpfest|graphene|lmodroid|lumine|matrixx|clover|yaap|aospa",
    )?;
    let mut removed = 0usize;
    for (key, _value) in rp.list_all()? {
        if pattern.is_match(&key) && rp.delete(&key).unwrap_or(false) {
            removed += 1;
        }
    }
    if rp.delete("ro.modversion").unwrap_or(false) {
        removed += 1;
    }
    Ok(removed)
}

#[cfg(target_os = "android")]
fn remove_pif_properties() -> Result<usize> {
    let rp = ResetProp {
        skip_svc: true,
        persistent: true,
        persist_only: false,
        verbose: false,
        show_context: false,
        rebuild: false,
    };
    let pattern = regex_lite::Regex::new("(?i)pihook|pixelprops|spoof")?;
    let mut removed = 0usize;
    for (key, _value) in rp.list_all()? {
        if pattern.is_match(&key) && rp.delete(&key).unwrap_or(false) {
            removed += 1;
        }
    }
    Ok(removed)
}

fn security_patch(today: chrono::NaiveDate) -> (String, String) {
    use chrono::{Datelike, Duration};
    let day = today.day();
    let previous = today.with_day(1).unwrap_or(today) - Duration::days(1);
    let (os_year, os_month) = if day >= 5 {
        (today.year(), today.month())
    } else {
        (previous.year(), previous.month())
    };
    let (vendor_year, vendor_month) = if day >= 5 {
        (today.year(), today.month())
    } else {
        (previous.year(), previous.month())
    };
    (
        format!("{os_year}-{os_month:02}-01"),
        format!("{vendor_year}-{vendor_month:02}-05"),
    )
}

// ---------------------------------------------------------------------------
// Uname
// ---------------------------------------------------------------------------

fn uname_values(brene: &Brene) -> Result<(String, String)> {
    if !brene.custom_uname_release.is_empty() || !brene.custom_uname_version.is_empty() {
        let release = if brene.custom_uname_release.is_empty() {
            "default"
        } else {
            &brene.custom_uname_release
        };
        let version = if brene.custom_uname_version.is_empty() {
            "default"
        } else {
            &brene.custom_uname_version
        };
        return Ok((release.to_owned(), version.to_owned()));
    }

    let kernel = kernel_release().unwrap_or_else(|| "5.10.0".to_owned());
    let build_date = {
        #[cfg(target_os = "android")]
        {
            sys_prop::get("ro.build.date").unwrap_or_default()
        }
        #[cfg(not(target_os = "android"))]
        {
            String::new()
        }
    };
    let version = format!(
        "#1 SMP PREEMPT {}",
        build_date.split_whitespace().collect::<Vec<_>>().join(" ")
    );
    let variant = detect().variant;
    let release = if variant.contains("GKI") {
        #[cfg(target_os = "android")]
        let kmi = crate::boot_patch::get_current_kmi()
            .ok()
            .and_then(|kmi| kmi.split('-').next().map(str::to_owned))
            .unwrap_or_else(|| "android12".to_owned());
        #[cfg(not(target_os = "android"))]
        let kmi = "android12".to_owned();
        format!(
            "{kernel}-{kmi}-{}-g{}-ab{}",
            random_between(1, 9),
            random_between(10_000_000, 99_999_999),
            random_between(10_000_000, 99_999_999)
        )
    } else {
        format!("{kernel}-g{}", random_between(10_000_000, 99_999_999))
    };
    Ok((release, version))
}

fn kernel_release() -> Option<String> {
    let version = fs::read_to_string("/proc/version").ok()?;
    let token = version.split_whitespace().nth(2)?;
    let mut release = String::new();
    for character in token.chars() {
        if character.is_ascii_digit() || character == '.' {
            release.push(character);
        } else {
            break;
        }
    }
    let release = release.trim_end_matches('.').to_owned();
    (!release.is_empty()).then_some(release)
}

fn random_between(low: u32, high: u32) -> u32 {
    use std::io::Read;
    let mut bytes = [0u8; 4];
    let value = fs::File::open("/dev/urandom")
        .and_then(|mut file| file.read_exact(&mut bytes))
        .map(|()| u32::from_le_bytes(bytes))
        .unwrap_or_else(|_| {
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|duration| duration.subsec_nanos())
                .unwrap_or(0)
        });
    low + value % (high - low + 1)
}

// ---------------------------------------------------------------------------
// cmdline / bootconfig and redirects
// ---------------------------------------------------------------------------

fn spoof_cmdline(detection: &Detection) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG")?;
    let gki = detection.variant.contains("GKI");
    let source = if gki {
        "/proc/bootconfig"
    } else {
        "/proc/cmdline"
    };
    let mut text = fs::read_to_string(source)?;
    if gki {
        text = text
            .replace(
                "androidboot.warranty_bit = \"1\"",
                "androidboot.warranty_bit = \"0\"",
            )
            .replace(
                "androidboot.verifiedbootstate = \"orange\"",
                "androidboot.verifiedbootstate = \"green\"",
            );
    } else {
        text = text
            .replace("androidboot.warranty_bit=1", "androidboot.warranty_bit=0")
            .replace(
                "androidboot.verifiedbootstate=orange",
                "androidboot.verifiedbootstate=green",
            );
    }
    if text.len() >= 8192 {
        bail!("Fake cmdline/bootconfig is too large");
    }
    let path = const_format::concatcp!(store::DIR, "/fake_cmdline");
    write_private(path, text.as_bytes())?;
    text_command::<8192>(0x0005_55b0, &text)
}

fn spoof_hosts(detection: &Detection) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_OPEN_REDIRECT")?;
    require(detection, "CONFIG_KSU_SUSFS_SUS_KSTAT")?;
    const TARGET: &str = "/system/etc/hosts";
    let fake = fake_file(
        Path::new(TARGET),
        b"127.0.0.1       localhost\n::1             ip6-localhost\n",
    )?;
    clone_permissions(Path::new(TARGET), Path::new(&fake))?;
    abi_redirect(TARGET, &fake, 3)?;
    static_kstat(
        detection,
        TARGET,
        KstatOverride {
            ino: Some(100),
            size: Some(56),
            blocks: Some(1),
            blksize: Some(4096),
        },
    )
}

fn redirect_empty(detection: &Detection, target: &str, name: &str) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_OPEN_REDIRECT")?;
    let fake = fake_file(Path::new(name), b"")?;
    clone_permissions(Path::new(target), Path::new(&fake))?;
    abi_redirect(target, &fake, 3)
}

/// Every redirected target needs its own backing file: hosts and the empty
/// decoys must never share an inode, and a name collision between two
/// directories would otherwise merge their destinations.
fn fake_file(hint: &Path, contents: &[u8]) -> Result<String> {
    fs::create_dir_all(FAKE_DIR)?;
    #[cfg(target_os = "android")]
    fs::set_permissions(FAKE_DIR, fs::Permissions::from_mode(0o700))?;
    let name = hint
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("file")
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | '-') {
                character
            } else {
                '_'
            }
        })
        .collect::<String>();
    let path = format!("{FAKE_DIR}/{name}");
    write_private(&path, contents)?;
    Ok(path)
}

#[cfg(target_os = "android")]
fn clone_permissions(source: &Path, destination: &Path) -> Result<()> {
    let md = fs::metadata(source)?;
    fs::set_permissions(destination, fs::Permissions::from_mode(md.mode()))?;
    let path = std::ffi::CString::new(destination.as_os_str().as_encoded_bytes())?;
    unsafe { libc::chown(path.as_ptr(), md.uid(), md.gid()) };
    let _ = Command::new("/system/bin/chcon")
        .args([
            "--reference",
            &source.to_string_lossy(),
            &destination.to_string_lossy(),
        ])
        .status();
    Ok(())
}

#[cfg(not(target_os = "android"))]
fn clone_permissions(_source: &Path, _destination: &Path) -> Result<()> {
    Ok(())
}

fn fix_local_tmp() -> Result<()> {
    const TARGET: &str = "/data/local/tmp";
    fs::create_dir_all(TARGET)?;
    #[cfg(target_os = "android")]
    {
        let mode = fs::Permissions::from_mode(0o771);
        fs::set_permissions(TARGET, mode)?;
        let _ = Command::new("/system/bin/chown")
            .args(["shell:shell", TARGET])
            .status();
        let _ = Command::new("/system/bin/chcon")
            .args(["u:object_r:shell_data_file:s0", TARGET])
            .status();
    }
    let detection = detect();
    if detection.status == "compatible" {
        static_kstat(
            &detection,
            TARGET,
            KstatOverride {
                ino: Some(100),
                size: Some(4096),
                blocks: Some(8),
                blksize: Some(4096),
            },
        )?;
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Cleanup / watcher
// ---------------------------------------------------------------------------

fn remove_device_artifacts(brene: &Brene) {
    for base in [
        "/storage/emulated/0",
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/media",
        "/storage/emulated/0/Android/obb",
    ] {
        let target = Path::new(base).join("..5.u.S");
        let _ = fs::remove_dir_all(&target);
        let _ = fs::remove_file(&target);
    }
    if brene.hide_ksu_dirs {
        for package in KSU_PACKAGES {
            for base in [
                "/storage/emulated/0/Android/data",
                "/storage/emulated/0/Android/media",
                "/storage/emulated/0/Android/obb",
            ] {
                let _ = fs::remove_dir_all(Path::new(base).join(package));
            }
        }
    }
}

/// Start the artifact watcher as a short-lived helper process. Using
/// `Command` (posix_spawn) instead of `fork` avoids inheriting the SUSFS store
/// lock and any Rust thread locks held by the boot stage.
#[cfg(target_os = "android")]
fn ensure_watcher() {
    // The child deduplicates itself with WATCH_LOCK, so spawning on every boot
    // stage (and on live Apply) is cheap: at most one loop survives.
    let Ok(exe) = std::env::current_exe() else {
        return;
    };
    let _ = Command::new(exe)
        .args(["susfs", "brene-watch"])
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn();
}

#[cfg(target_os = "android")]
pub fn watch_daemon() -> Result<()> {
    use std::os::fd::AsRawFd;
    let lock = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(WATCH_LOCK)?;
    if unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        bail!("BRENE watcher is already running");
    }
    let brene = store::read_config()
        .map(|config| config.brene)
        .unwrap_or_default();
    let listen = unsafe { libc::inotify_init1(libc::IN_CLOEXEC) };
    if listen < 0 {
        bail!("inotify_init1: {}", std::io::Error::last_os_error());
    }
    let mask = libc::IN_CREATE
        | libc::IN_MOVED_TO
        | libc::IN_CLOSE_WRITE
        | libc::IN_ATTRIB
        | libc::IN_DELETE;
    // /storage may not be mounted yet when the boot stage fires: retry for a
    // minute before blocking on a watch set that would never deliver events.
    for _ in 0..60 {
        let mut watched = 0;
        for dir in [
            "/storage/emulated/0",
            "/storage/emulated/0/Android/data",
            "/storage/emulated/0/Android/media",
            "/storage/emulated/0/Android/obb",
        ] {
            let Ok(path) = std::ffi::CString::new(dir) else {
                continue;
            };
            if unsafe { libc::inotify_add_watch(listen, path.as_ptr(), mask) } >= 0 {
                watched += 1;
            }
        }
        if watched > 0 {
            break;
        }
        unsafe { libc::sleep(1) };
    }
    let _lock = lock;
    let mut buffer = [0u8; 4096];
    loop {
        let read = unsafe {
            libc::read(
                listen,
                buffer.as_mut_ptr().cast::<libc::c_void>(),
                buffer.len(),
            )
        };
        remove_device_artifacts(&brene);
        if read < 0 {
            unsafe { libc::sleep(5) };
        }
    }
}

#[cfg(not(target_os = "android"))]
pub fn watch_daemon() -> Result<()> {
    bail!("BRENE watcher is only available on Android")
}

// ---------------------------------------------------------------------------
// Small system helpers
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
fn run_tool(tool: &str, args: &[&str]) -> Result<()> {
    let output = Command::new(tool)
        .args(args)
        .output()
        .with_context(|| format!("run {tool}"))?;
    if !output.status.success() {
        bail!(
            "{tool} exited with {}: {}",
            output.status,
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    Ok(())
}

fn enforce_selinux() -> Result<()> {
    #[cfg(target_os = "android")]
    {
        let path = "/sys/fs/selinux/enforce";
        let current = fs::read_to_string(path).unwrap_or_default();
        if current.trim() != "1" {
            fs::write(path, b"1")?;
        }
    }
    Ok(())
}

#[cfg(target_os = "android")]
fn priority_uid_filter() -> Result<()> {
    let output = Command::new("/system/bin/pm")
        .args(["list", "packages", "-3"])
        .output()
        .context("list third-party packages")?;
    if !output.status.success() {
        bail!("pm list packages failed");
    }
    let text = String::from_utf8_lossy(&output.stdout);
    let mut packages: Vec<&str> = text
        .lines()
        .filter_map(|line| line.strip_prefix("package:"))
        .map(str::trim)
        .filter(|name| !name.is_empty())
        .collect();
    packages.sort_unstable();
    packages.dedup();
    let mut joined = packages.join(",");
    if joined.len() >= 65_535 {
        while joined.len() >= 65_535 {
            joined.truncate(joined.rfind(',').unwrap_or(0));
        }
    }
    if joined.is_empty() {
        bail!("no third-party packages");
    }
    let mut payload = abi::Text::<65_535>::empty();
    payload.text = abi::bytes::<65_535>(&joined)?;
    abi::call(abi::CMD_SET_PRIORITY_UID_FILTER, &mut payload);
    if payload.err == 126 {
        bail!("kernel does not implement the priority UID filter");
    }
    abi::checked(payload.err)
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;

    #[test]
    fn security_patch_follows_brene_month_boundary() {
        assert_eq!(
            security_patch(NaiveDate::from_ymd_opt(2026, 10, 1).unwrap()),
            ("2026-09-01".to_owned(), "2026-09-05".to_owned())
        );
        assert_eq!(
            security_patch(NaiveDate::from_ymd_opt(2026, 10, 5).unwrap()),
            ("2026-10-01".to_owned(), "2026-10-05".to_owned())
        );
        assert_eq!(
            security_patch(NaiveDate::from_ymd_opt(2026, 1, 3).unwrap()),
            ("2025-12-01".to_owned(), "2025-12-05".to_owned())
        );
    }

    #[test]
    fn abi_paths_reject_escape_and_control_bytes() {
        assert!(path_to_abi(Path::new("/data/local/tmp/x")).is_ok());
        assert!(path_to_abi(Path::new("/data/../x")).is_err());
        assert!(path_to_abi(Path::new("relative")).is_err());
        assert!(path_to_abi(Path::new("/data/a\nb")).is_err());
    }

    #[test]
    fn report_round_trips() {
        let report = Report {
            boot_id: "boot".to_owned(),
            stage: "boot-completed".to_owned(),
            applied: 3,
            skipped: vec!["hide_third_party_apps".to_owned()],
            failures: BTreeMap::from([("x".to_owned(), "y".to_owned())]),
        };
        let encoded = serde_json::to_string(&report).unwrap();
        let decoded: Report = serde_json::from_str(&encoded).unwrap();
        assert_eq!(decoded.applied, 3);
        assert_eq!(decoded.failures["x"], "y");
    }
}
