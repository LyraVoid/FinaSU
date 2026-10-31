use crate::feature_id::FeatureId;
use crate::ksu_uapi;
use crate::uts_view_config::{
    ConfigFile, ConfigSnapshot, FIELD_VALID_MASK, MAX_CONFIG_BYTES, MAX_TARGETS, UtsMode,
    parse_snapshot, write_snapshot_at,
};
use crate::{feature, ksucalls, susfs};
use anyhow::{Context, Result, bail};
use log::{info, warn};
use std::fs::{File, OpenOptions};
use std::io::Read;
use std::os::fd::AsRawFd;
use std::os::raw::c_char;
use std::os::unix::fs::{DirBuilderExt, MetadataExt, OpenOptionsExt, PermissionsExt};
use std::path::Path;

const CONFIG_PATH: &str = "/data/adb/ksu/uts_view.json";
const LOCK_PATH: &str = "/data/adb/ksu/.uts_view.lock";
const RUNTIME_ERROR_PATH: &str = "/data/adb/ksu/uts_view.runtime_error";
const WORKING_DIR: &str = "/data/adb/ksu";
const MAX_SNAPSHOT_BYTES: usize = 1024 * 1024;
const MAX_RUNTIME_ERROR_BYTES: usize = 4096;

#[derive(Debug, Clone, clap::Subcommand)]
pub enum Command {
    /// Read kernel and persisted UTS View state
    Status,
    /// Validate a JSON template from stdin; does not change enabled state
    Set,
    /// Enable the current non-empty template
    Enable,
    /// Disable UTS View while retaining the template
    Disable,
    /// Disable, clear the kernel template, and remove persisted state
    Clear,
    /// Hand the uname provider role from SUSFS to UTS View
    TakeOver,
}

struct ConfigLock(File);

impl Drop for ConfigLock {
    fn drop(&mut self) {
        unsafe {
            libc::flock(self.0.as_raw_fd(), libc::LOCK_UN);
        }
    }
}

struct KernelSnapshot {
    config: ksu_uapi::ksu_uts_view_config,
    feature: u64,
}

fn ensure_root() -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        bail!("Root is required for UTS View management");
    }
    Ok(())
}

fn ensure_working_dir() -> Result<()> {
    match std::fs::symlink_metadata(WORKING_DIR) {
        Ok(metadata) => {
            // The shared ksud directory is normally 0755. Only group/other write access would
            // let an unprivileged process replace the root-owned config or lock entries.
            if !metadata.is_dir() || metadata.uid() != 0 {
                bail!("Unsafe UTS View working directory");
            }
            if metadata.mode() & 0o022 != 0 {
                repair_working_dir_mode(Path::new(WORKING_DIR), metadata.mode())?;
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            std::fs::DirBuilder::new()
                .mode(0o755)
                .create(WORKING_DIR)
                .context("Create UTS View working directory")?;
        }
        Err(error) => return Err(error).context("Inspect UTS View working directory"),
    }
    Ok(())
}

fn repair_working_dir_mode(path: &Path, mode: u32) -> Result<()> {
    let mode = mode & 0o7777 & !0o022;
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(mode))
        .context("Repair UTS View working directory permissions")?;
    let metadata =
        std::fs::symlink_metadata(path).context("Inspect repaired UTS View directory")?;
    if !metadata.is_dir() || metadata.mode() & 0o022 != 0 {
        bail!("Unsafe UTS View working directory");
    }
    Ok(())
}

fn acquire_lock() -> Result<ConfigLock> {
    ensure_working_dir()?;
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(LOCK_PATH)
        .context("Open UTS View lock")?;
    let metadata = file.metadata().context("Inspect UTS View lock")?;
    if !metadata.is_file() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
        bail!("Unsafe UTS View lock");
    }
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        bail!("UTS View is busy; retry later");
    }
    Ok(ConfigLock(file))
}

fn read_config_snapshot() -> Result<Option<ConfigSnapshot>> {
    let file = match OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW | libc::O_NONBLOCK)
        .open(CONFIG_PATH)
    {
        Ok(file) => file,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error).context("Open UTS View config"),
    };
    let metadata = file.metadata().context("Inspect UTS View config")?;
    if !metadata.is_file() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
        bail!("Unsafe UTS View config permissions");
    }
    let mut bytes = Vec::new();
    file.take((MAX_SNAPSHOT_BYTES + 1) as u64)
        .read_to_end(&mut bytes)
        .context("Read UTS View config")?;
    if bytes.len() > MAX_SNAPSHOT_BYTES {
        bail!("UTS View config is too large to snapshot safely");
    }
    Ok(Some(ConfigSnapshot {
        bytes,
        mode: metadata.mode() & 0o777,
    }))
}

fn config_snapshot_too_large() -> Result<bool> {
    match std::fs::symlink_metadata(CONFIG_PATH) {
        Ok(metadata) => {
            if !metadata.is_file() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
                bail!("Unsafe UTS View config permissions");
            }
            Ok(metadata.len() > MAX_SNAPSHOT_BYTES as u64)
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(error).context("Inspect UTS View config size"),
    }
}

fn read_config_snapshot_and_parse() -> Result<Option<(ConfigSnapshot, ConfigFile)>> {
    read_config_snapshot()?
        .map(|snapshot| {
            let config = parse_snapshot(&snapshot)?;
            Ok((snapshot, config))
        })
        .transpose()
}

fn read_config_file() -> Result<Option<ConfigFile>> {
    read_config_snapshot()?
        .as_ref()
        .map(parse_snapshot)
        .transpose()
}

fn write_config_bytes(payload: &[u8], mode: u32) -> Result<()> {
    ensure_working_dir()?;
    write_config_bytes_at(WORKING_DIR, CONFIG_PATH, payload, mode)
}

fn write_config_bytes_at(dir: &str, path: &str, payload: &[u8], mode: u32) -> Result<()> {
    write_snapshot_at(
        dir,
        path,
        &ConfigSnapshot {
            bytes: payload.to_vec(),
            mode,
        },
    )
}

fn write_config_file(config: &ConfigFile) -> Result<()> {
    let payload = serde_json::to_vec_pretty(config).context("Serialize UTS View config")?;
    write_config_bytes(&payload, 0o600)
}

fn remove_config_file() -> Result<()> {
    match std::fs::remove_file(CONFIG_PATH) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error).context("Remove UTS View config"),
    }
    File::open(WORKING_DIR)
        .context("Open UTS View directory")?
        .sync_all()
        .context("Sync UTS View directory")?;
    Ok(())
}

fn read_runtime_error() -> Result<Option<String>> {
    let file = match OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW | libc::O_NONBLOCK)
        .open(RUNTIME_ERROR_PATH)
    {
        Ok(file) => file,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error).context("Open UTS View runtime error"),
    };
    let metadata = file.metadata().context("Inspect UTS View runtime error")?;
    if !metadata.is_file() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
        bail!("Unsafe UTS View runtime error permissions");
    }
    let mut bytes = Vec::new();
    file.take((MAX_RUNTIME_ERROR_BYTES + 1) as u64)
        .read_to_end(&mut bytes)
        .context("Read UTS View runtime error")?;
    if bytes.len() > MAX_RUNTIME_ERROR_BYTES {
        bail!("UTS View runtime error is too large");
    }
    let text = String::from_utf8(bytes).context("UTS View runtime error is not UTF-8")?;
    let text = text.trim();
    Ok((!text.is_empty()).then(|| text.to_owned()))
}

fn write_runtime_error(error: &str) -> Result<()> {
    let mut bytes = error.as_bytes().to_vec();
    if bytes.len() > MAX_RUNTIME_ERROR_BYTES {
        bytes.truncate(MAX_RUNTIME_ERROR_BYTES);
        if let Some(last) = bytes.last_mut() {
            *last = b'.';
        }
    }
    write_config_bytes(&bytes, 0o600)
}

fn clear_runtime_error() {
    match std::fs::remove_file(RUNTIME_ERROR_PATH) {
        Ok(()) => {
            if let Err(error) = File::open(WORKING_DIR).and_then(|dir| dir.sync_all()) {
                warn!("uts-view: clear-runtime-error sync failed: {error:#}");
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => warn!("uts-view: clear-runtime-error failed: {error:#}"),
    }
}

fn discard_unreadable_config() -> Result<()> {
    if !supported()? {
        remove_config_file()?;
        info!("uts-view: event=discard-unreadable kernel=unsupported");
        return Ok(());
    }

    let old_kernel = snapshot_kernel()?;
    let empty = ConfigFile::empty(false);
    if let Err(error) = apply_kernel(&empty, false) {
        if let Err(rollback) = restore_kernel(&old_kernel) {
            bail!("{error:#}; residual=uts-kernel:{rollback:#}");
        }
        return Err(error);
    }
    if let Err(error) = remove_config_file() {
        if let Err(rollback) = restore_kernel(&old_kernel) {
            bail!("{error:#}; residual=uts-kernel:{rollback:#}");
        }
        return Err(error);
    }
    clear_runtime_error();
    warn!("uts-view: event=discard-unreadable config=oversized runtime=disabled");
    Ok(())
}

fn record_runtime_error(error: &anyhow::Error) {
    warn!("uts-view: {error:#}");
    if let Err(write_error) = write_runtime_error(&format!("{error:#}")) {
        warn!("uts-view: persist-runtime-error failed: {write_error:#}");
    }
}

fn restore_file(snapshot: Option<&ConfigSnapshot>) -> Result<()> {
    snapshot.map_or_else(remove_config_file, |snapshot| {
        write_config_bytes(&snapshot.bytes, snapshot.mode)
    })
}

fn write_field(dst: &mut [c_char; 65], value: &str) -> Result<()> {
    if value.len() >= dst.len() {
        bail!("UTS View field exceeds 64 bytes");
    }
    for (target, source) in dst.iter_mut().zip(value.bytes()) {
        *target = source as c_char;
    }
    Ok(())
}

fn to_kernel_config(config: &ConfigFile) -> Result<ksu_uapi::ksu_uts_view_config> {
    config.validate()?;
    let mut output = unsafe { std::mem::zeroed::<ksu_uapi::ksu_uts_view_config>() };
    output.mode = match config.mode {
        UtsMode::Global => ksu_uapi::KSU_UTS_MODE_GLOBAL,
        UtsMode::Selected => ksu_uapi::KSU_UTS_MODE_SELECTED,
    };
    output.field_mask = config.field_mask;
    output.target_count = u32::try_from(config.targets.len()).unwrap_or(u32::MAX);
    if config.targets.len() > MAX_TARGETS {
        bail!("UTS View supports at most {MAX_TARGETS} target apps");
    }
    for (slot, target) in output.target_uids.iter_mut().zip(&config.targets) {
        *slot = target.uid;
    }
    write_field(&mut output.sysname, &config.sysname)?;
    write_field(&mut output.nodename, &config.nodename)?;
    write_field(&mut output.release, &config.release)?;
    write_field(&mut output.version, &config.version)?;
    write_field(&mut output.machine, &config.machine)?;
    write_field(&mut output.domainname, &config.domainname)?;
    Ok(output)
}

const fn feature_id() -> u32 {
    FeatureId::UtsView as u32
}

fn supported() -> Result<bool> {
    feature::feature_supported(FeatureId::UtsView)
}

pub fn provider_active() -> Result<bool> {
    if !supported()? {
        return Ok(false);
    }
    Ok(ksucalls::get_uts_view_config()?.state & ksu_uapi::KSU_UTS_STATE_ACTIVE != 0)
}

fn require_supported() -> Result<()> {
    if !supported()? {
        bail!("Kernel does not provide the UTS View feature");
    }
    Ok(())
}

fn feature_value() -> Result<u64> {
    Ok(ksucalls::get_feature(feature_id())?.value)
}

fn snapshot_kernel() -> Result<KernelSnapshot> {
    Ok(KernelSnapshot {
        config: ksucalls::get_uts_view_config()?,
        feature: feature_value()?,
    })
}

fn verify_kernel_config(
    readback: &ksu_uapi::ksu_uts_view_config,
    expected: &ksu_uapi::ksu_uts_view_config,
) -> Result<()> {
    if readback.mode != expected.mode
        || readback.field_mask != expected.field_mask
        || readback.target_count != expected.target_count
        || readback.target_uids != expected.target_uids
        || readback.sysname != expected.sysname
        || readback.nodename != expected.nodename
        || readback.release != expected.release
        || readback.version != expected.version
        || readback.machine != expected.machine
        || readback.domainname != expected.domainname
    {
        bail!("Kernel UTS View config readback mismatch");
    }
    Ok(())
}

fn verify_feature_value(expected: u64) -> Result<()> {
    let actual = feature_value()?;
    if actual != expected {
        bail!("Kernel UTS View feature readback mismatch: expected {expected}, got {actual}");
    }
    Ok(())
}

fn apply_kernel(config: &ConfigFile, enabled: bool) -> Result<()> {
    let kernel = to_kernel_config(config)?;
    ksucalls::set_uts_view_config(&kernel)?;
    ksucalls::set_feature(feature_id(), u64::from(enabled && config.field_mask != 0))?;
    let readback = ksucalls::get_uts_view_config()?;
    verify_kernel_config(&readback, &kernel)?;
    verify_feature_value(u64::from(enabled && config.field_mask != 0))?;
    let active = readback.state & ksu_uapi::KSU_UTS_STATE_ACTIVE != 0;
    if active != (enabled && config.field_mask != 0) {
        bail!("Kernel UTS View active-state readback mismatch");
    }
    Ok(())
}

fn restore_kernel(snapshot: &KernelSnapshot) -> Result<()> {
    let mut residuals = Vec::new();
    if let Err(error) = ksucalls::set_uts_view_config(&snapshot.config) {
        residuals.push(format!("config:{error:#}"));
    }
    if let Err(error) = ksucalls::set_feature(feature_id(), snapshot.feature) {
        residuals.push(format!("feature-set:{error:#}"));
    }
    match ksucalls::get_uts_view_config() {
        Ok(readback) => {
            if let Err(error) = verify_kernel_config(&readback, &snapshot.config) {
                residuals.push(format!("config-readback:{error:#}"));
            }
            let expected_active =
                snapshot.feature != 0 && snapshot.config.field_mask & FIELD_VALID_MASK != 0;
            if (readback.state & ksu_uapi::KSU_UTS_STATE_ACTIVE != 0) != expected_active {
                residuals.push("active-readback:mismatch".to_owned());
            }
        }
        Err(error) => residuals.push(format!("config-get:{error:#}")),
    }
    if let Err(error) = verify_feature_value(snapshot.feature) {
        residuals.push(format!("feature-readback:{error:#}"));
    }
    if residuals.is_empty() {
        Ok(())
    } else {
        bail!("Kernel UTS View restore residuals: {}", residuals.join(","))
    }
}

fn prepare_takeover_template(config: &ConfigFile) -> Result<()> {
    ksucalls::set_feature(feature_id(), 0)?;
    let kernel = to_kernel_config(config)?;
    ksucalls::set_uts_view_config(&kernel)?;
    let readback = ksucalls::get_uts_view_config()?;
    verify_kernel_config(&readback, &kernel)?;
    verify_feature_value(0)?;
    if readback.state & ksu_uapi::KSU_UTS_STATE_ACTIVE != 0 {
        bail!("Kernel UTS View remained active while installing takeover template");
    }
    Ok(())
}

fn enable_takeover(config: &ConfigFile) -> Result<()> {
    ksucalls::set_feature(feature_id(), 1)?;
    let readback = ksucalls::get_uts_view_config()?;
    let kernel = to_kernel_config(config)?;
    verify_kernel_config(&readback, &kernel)?;
    verify_feature_value(1)?;
    if readback.state & ksu_uapi::KSU_UTS_STATE_ACTIVE == 0 {
        bail!("Kernel UTS View did not become active after takeover");
    }
    Ok(())
}

fn provider_state() -> Result<susfs::UnameProviderState> {
    susfs::uname_provider_state()
}

fn reject_provider_for_enable(provider: susfs::UnameProviderState) -> Result<()> {
    if provider.uncertain {
        bail!("SUSFS uname state is unknown; verify the active owner before enabling UTS View");
    }
    if provider.blocks_uts() {
        bail!("SUSFS uname spoofing is already applied; use take-over first");
    }
    Ok(())
}

fn commit_locked(config: Option<&ConfigFile>, old_file: Option<&ConfigSnapshot>) -> Result<()> {
    require_supported()?;
    let _susfs_lock = susfs::lock_store().context("Lock SUSFS state")?;
    if config.is_some_and(|candidate| candidate.enabled) {
        reject_provider_for_enable(provider_state()?)?;
    }
    let old_kernel = snapshot_kernel()?;

    let result = (|| -> Result<()> {
        if let Some(candidate) = config {
            apply_kernel(candidate, candidate.enabled)?;
            write_config_file(candidate)?;
        } else {
            let empty = ConfigFile::empty(false);
            apply_kernel(&empty, false)?;
            remove_config_file()?;
        }
        Ok(())
    })();

    if let Err(error) = result {
        let mut residuals = Vec::new();
        if let Err(rollback) = restore_kernel(&old_kernel) {
            residuals.push(format!("uts-kernel:{rollback:#}"));
        }
        if let Err(rollback) = restore_file(old_file) {
            residuals.push(format!("uts-file:{rollback:#}"));
        }
        if residuals.is_empty() {
            bail!("{error:#}");
        }
        bail!("{error:#}; residual={}", residuals.join(","));
    }
    clear_runtime_error();
    Ok(())
}

fn read_stdin_limited() -> Result<String> {
    let mut input = String::new();
    std::io::stdin()
        .take((MAX_CONFIG_BYTES + 1) as u64)
        .read_to_string(&mut input)
        .context("Read UTS View input")?;
    if input.len() > MAX_CONFIG_BYTES {
        bail!("UTS View input is too large");
    }
    Ok(input)
}

fn parse_input() -> Result<ConfigFile> {
    let input = read_stdin_limited()?;
    let config = serde_json::from_str::<ConfigFile>(&input).context("Parse UTS View input")?;
    config.validate()?;
    Ok(config)
}

pub fn run(command: &Command) -> Result<()> {
    ensure_root()?;
    match command {
        Command::Status => {
            status();
            Ok(())
        }
        Command::Set => {
            let mut config = parse_input()?;
            let _lock = acquire_lock()?;
            let old_file = read_config_snapshot_and_parse()?;
            config.enabled = old_file
                .as_ref()
                .is_some_and(|(_, current)| current.enabled);
            commit_locked(
                Some(&config),
                old_file.as_ref().map(|(snapshot, _)| snapshot),
            )
        }
        Command::Enable => {
            require_supported()?;
            let _lock = acquire_lock()?;
            let old_file =
                read_config_snapshot_and_parse()?.context("UTS View template is not configured")?;
            if old_file.1.field_mask == 0 {
                bail!("UTS View template has no fields");
            }
            let mut config = old_file.1;
            config.enabled = true;
            commit_locked(Some(&config), Some(&old_file.0))
        }
        Command::Disable => {
            let _lock = acquire_lock()?;
            if config_snapshot_too_large()? {
                return discard_unreadable_config();
            }
            let old_snapshot = read_config_snapshot()?;
            let old_file = old_snapshot.as_ref().and_then(|snapshot| {
                parse_snapshot(snapshot)
                    .map_err(|error| {
                        warn!("uts-view: event=disable config=invalid error={error:#}");
                    })
                    .ok()
            });
            if !supported()? {
                if let Some(mut config) = old_file {
                    config.enabled = false;
                    if let Err(error) = write_config_file(&config) {
                        if let Err(rollback) = restore_file(old_snapshot.as_ref()) {
                            bail!("{error:#}; residual=uts-file:{rollback:#}");
                        }
                        return Err(error);
                    }
                }
                return Ok(());
            }
            let Some(mut config) = old_file else {
                ksucalls::set_feature(feature_id(), 0)?;
                warn!("uts-view: event=disable config=invalid runtime=disabled");
                return Ok(());
            };
            config.enabled = false;
            commit_locked(Some(&config), old_snapshot.as_ref())
        }
        Command::Clear => {
            let _lock = acquire_lock()?;
            if config_snapshot_too_large()? {
                return discard_unreadable_config();
            }
            let old_file = read_config_snapshot()?;
            if !supported()? {
                if let Err(error) = remove_config_file() {
                    if let Err(rollback) = restore_file(old_file.as_ref()) {
                        bail!("{error:#}; residual=uts-file:{rollback:#}");
                    }
                    return Err(error);
                }
                info!("uts-view: event=clear kernel=unsupported");
                return Ok(());
            }
            commit_locked(None, old_file.as_ref())
        }
        Command::TakeOver => take_over(),
    }
}

fn status() {
    let (snapshot, read_error) = match read_config_snapshot() {
        Ok(snapshot) => (snapshot, None),
        Err(error) => (None, Some(format!("{error:#}"))),
    };
    let (file, parse_error) = match snapshot.as_ref().map(parse_snapshot).transpose() {
        Ok(file) => (file, None),
        Err(error) => (None, Some(format!("{error:#}"))),
    };
    let config_error = read_error.or(parse_error);
    let supported = supported().unwrap_or(false);
    let (provider, provider_error) = match provider_state() {
        Ok(provider) => (provider, None),
        Err(error) => (
            susfs::UnameProviderState::default(),
            Some(format!("{error:#}")),
        ),
    };
    let provider_error = provider_error.or_else(|| {
        provider
            .uncertain
            .then(|| "SUSFS uname application result is unknown".to_owned())
    });
    let runtime_error = match read_runtime_error() {
        Ok(error) => error,
        Err(error) => Some(format!("{error:#}")),
    };
    let (kernel, kernel_error) = if supported {
        match ksucalls::get_uts_view_config() {
            Ok(config) => (Some(config), None),
            Err(error) => (None, Some(format!("{error:#}"))),
        }
    } else {
        (None, None)
    };
    let active = kernel
        .as_ref()
        .is_some_and(|config| config.state & ksu_uapi::KSU_UTS_STATE_ACTIVE != 0);
    let matched = kernel.as_ref().map_or(0, |config| config.matched);
    let provider_active = provider.blocks_uts();
    let enabled = file.as_ref().is_some_and(|config| config.enabled);
    let field_mask = file.as_ref().map_or(0, |config| config.field_mask);
    let mode = kernel
        .as_ref()
        .map(|config| config.mode)
        .or_else(|| {
            file.as_ref().map(|config| match config.mode {
                UtsMode::Global => ksu_uapi::KSU_UTS_MODE_GLOBAL,
                UtsMode::Selected => ksu_uapi::KSU_UTS_MODE_SELECTED,
            })
        })
        .unwrap_or(ksu_uapi::KSU_UTS_MODE_SELECTED);
    let mode_name = if mode == ksu_uapi::KSU_UTS_MODE_GLOBAL {
        "global"
    } else {
        "selected"
    };
    let targets = file
        .as_ref()
        .map(|config| {
            config
                .targets
                .iter()
                .map(|target| {
                    serde_json::json!({
                        "package": target.package,
                        "uid": target.uid,
                    })
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();
    let target_count = targets.len();
    let state = if !supported {
        "unsupported"
    } else if config_error.is_some()
        || kernel_error.is_some()
        || provider_error.is_some()
        || runtime_error.is_some()
    {
        "error"
    } else if enabled && provider_active {
        "blocked"
    } else if active && matched > 0 {
        "observed"
    } else if active {
        "enabled"
    } else if field_mask != 0 {
        "configured"
    } else {
        "not_configured"
    };
    let source = if active {
        "uts"
    } else if provider_active {
        "susfs"
    } else if provider_error.is_some() || runtime_error.is_some() {
        "unknown"
    } else {
        "none"
    };
    let file_fields = file.as_ref().map_or_else(
        || ConfigFile::empty(false).field_values(),
        ConfigFile::field_values,
    );
    let output = serde_json::json!({
        "state": state,
        "supported": supported,
        "enabled": enabled,
        "active": active,
        "mode": mode_name,
        "target_count": target_count,
        "targets": targets,
        "field_mask": field_mask,
        "fields": file_fields,
        "source": source,
        "configuration": {
            "present": snapshot.is_some(),
            "valid": file.is_some(),
            "enabled": enabled,
            "field_mask": field_mask,
            "mode": mode_name,
            "target_count": target_count,
            "targets": targets,
        },
        "runtime": {
            "supported": supported,
            "active": active,
            "mode": mode_name,
        },
        "observation": {
            "generation": kernel.as_ref().map_or(0, |config| config.generation),
            "matched": matched,
            "discarded": kernel.as_ref().map_or(0, |config| config.discarded),
            "last_pid": kernel.as_ref().map_or(0, |config| config.last_pid),
            "last_uid": kernel.as_ref().map_or(0, |config| config.last_uid),
            "skip_not_app": kernel.as_ref().map_or(0, |config| config.skip_not_app),
            "skip_isolated": kernel.as_ref().map_or(0, |config| config.skip_isolated),
            "skip_not_target": kernel.as_ref().map_or(0, |config| config.skip_not_target),
            "skip_not_zygote": kernel.as_ref().map_or(0, |config| config.skip_not_zygote),
            "skip_private_ns": kernel.as_ref().map_or(0, |config| config.skip_private_ns),
            "unshare_failed": kernel.as_ref().map_or(0, |config| config.unshare_failed),
            "merge_missing_ns": kernel.as_ref().map_or(0, |config| config.merge_missing_ns),
        },
        "provider": {
            "uname_applied": provider.applied,
            "uname_scheduled": provider.scheduled,
            "uname_uncertain": provider.uncertain,
        },
        "provider_error": provider_error,
        "runtime_error": runtime_error,
        "config_error": config_error,
        "kernel_error": kernel_error,
    });
    println!("{output}");
}

fn take_over() -> Result<()> {
    let mut candidate = parse_input()?;
    if candidate.field_mask == 0 {
        bail!("UTS View takeover requires at least one configured field");
    }
    candidate.schema = 1;
    candidate.enabled = true;
    candidate.validate()?;
    require_supported()?;

    let _uts_lock = acquire_lock()?;
    let _susfs_lock = susfs::lock_store().context("Lock SUSFS state")?;

    let old_file = read_config_snapshot()?;
    let old_kernel = snapshot_kernel()?;
    let susfs_snapshot =
        susfs::uname_takeover_snapshot().context("Snapshot SUSFS uname provider")?;

    let result = (|| -> Result<()> {
        susfs::take_uname_provider(&susfs_snapshot)?;
        prepare_takeover_template(&candidate)?;
        write_config_file(&candidate)?;
        enable_takeover(&candidate)
    })();

    if let Err(error) = result {
        let mut residuals = Vec::new();
        if let Err(rollback) = ksucalls::set_feature(feature_id(), 0) {
            residuals.push(format!("uts-feature:{rollback:#}"));
        }
        if let Err(rollback) = restore_kernel(&old_kernel) {
            residuals.push(format!("uts-kernel:{rollback:#}"));
        }
        if let Err(rollback) = restore_file(old_file.as_ref()) {
            residuals.push(format!("uts-file:{rollback:#}"));
        }
        match provider_active() {
            Ok(false) => {
                if let Err(rollback) = susfs::restore_uname_provider(&susfs_snapshot) {
                    residuals.push(format!("susfs:{rollback:#}"));
                }
            }
            Ok(true) => residuals.push("susfs:not-restored-uts-active".to_owned()),
            Err(rollback) => {
                residuals.push(format!("susfs:not-restored-uts-state-unknown:{rollback:#}"));
            }
        }
        if residuals.is_empty() {
            bail!("{error:#}");
        }
        bail!("{error:#}; residual={}", residuals.join(","));
    }

    info!("uts-view: event=take-over susfs=default uts=enabled");
    Ok(())
}

const fn boot_action(configured: bool, provider: susfs::UnameProviderState) -> BootAction {
    if !configured || provider.blocks_uts() {
        BootAction::Disable
    } else if provider.needs_boot_resolution() {
        BootAction::ResolveBeforeEnable
    } else {
        BootAction::Enable
    }
}

const fn reconcile_enables_uts(provider: susfs::UnameProviderState) -> bool {
    provider.known_not_applied()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum BootAction {
    Enable,
    ResolveBeforeEnable,
    Disable,
}

pub fn boot_apply() {
    let result = (|| -> Result<()> {
        if !supported()? {
            info!("uts-view: kernel feature unavailable");
            return Ok(());
        }
        let _lock = acquire_lock()?;
        let _susfs_lock = susfs::lock_store().context("Lock SUSFS state")?;
        let config = match read_config_file() {
            Ok(Some(config)) => config,
            Ok(None) => ConfigFile::empty(false),
            Err(error) => {
                warn!("uts-view: event=boot-apply source=invalid error={error:#}");
                ConfigFile::empty(false)
            }
        };
        let configured = config.enabled && config.field_mask != 0 && !crate::utils::is_safe_mode();
        let provider = match provider_state() {
            Ok(provider) => provider,
            Err(error) => {
                warn!("uts-view: event=provider-check result=failed error={error:#}");
                susfs::UnameProviderState {
                    scheduled: configured,
                    ..susfs::UnameProviderState::default()
                }
            }
        };
        let enabled = match boot_action(configured, provider) {
            BootAction::Enable => true,
            BootAction::ResolveBeforeEnable => {
                info!("uts-view: event=boot-apply provider=susfs result=deferred");
                false
            }
            BootAction::Disable if configured => {
                warn!("uts-view: event=provider-conflict provider=susfs result=applied");
                false
            }
            BootAction::Disable => false,
        };
        apply_kernel(&config, enabled)?;
        info!(
            "uts-view: event=boot-apply enabled={} mask=0x{:x} source={CONFIG_PATH}",
            enabled, config.field_mask
        );
        Ok(())
    })();
    if let Err(error) = result {
        record_runtime_error(&error);
    }
}

pub fn resume_after_susfs() {
    let result = (|| -> Result<()> {
        if !supported()? || crate::utils::is_safe_mode() {
            return Ok(());
        }
        let _lock = acquire_lock()?;
        let _susfs_lock = susfs::lock_store().context("Lock SUSFS state")?;
        let config = match read_config_file() {
            Ok(Some(config)) => config,
            Ok(None) => return Ok(()),
            Err(error) => {
                warn!("uts-view: event=post-susfs source=invalid error={error:#}");
                return Ok(());
            }
        };
        if !config.enabled || config.field_mask == 0 {
            return Ok(());
        }
        let provider = provider_state()?;
        let enabled = reconcile_enables_uts(provider);
        apply_kernel(&config, enabled)?;
        info!(
            "uts-view: event=post-susfs enabled={enabled} provider_applied={}",
            provider.blocks_uts()
        );
        clear_runtime_error();
        Ok(())
    })();
    if let Err(error) = result {
        record_runtime_error(&error);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn enabling_uts_rejects_unknown_or_applied_susfs_uname() {
        assert!(reject_provider_for_enable(susfs::UnameProviderState::default()).is_ok());
        assert!(
            reject_provider_for_enable(susfs::UnameProviderState {
                uncertain: true,
                ..susfs::UnameProviderState::default()
            })
            .is_err()
        );
        assert!(
            reject_provider_for_enable(susfs::UnameProviderState {
                applied: true,
                ..susfs::UnameProviderState::default()
            })
            .is_err()
        );
    }

    #[test]
    fn repairs_group_or_other_writable_working_directory() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("ksu");
        std::fs::create_dir(&path).unwrap();
        std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o777)).unwrap();

        repair_working_dir_mode(&path, 0o777).unwrap();

        let mode = std::fs::metadata(path).unwrap().mode() & 0o777;
        assert_eq!(mode, 0o755);
    }
}
