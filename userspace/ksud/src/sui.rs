//! Built-in Sui extension: payload staging, dependency gating and lifecycle.
//!
//! Sui is a Zygisk module, so its payload stays in a module directory that any provider
//! discovers. ksud owns install/enable/disable/uninstall, the SELinux rule and daemon
//! supervision instead of Sui's own Magisk-style scripts.

use anyhow::{Context, Result, bail, ensure};
use serde::{Deserialize, Serialize};
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::os::fd::AsRawFd;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::os::unix::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{Child, Command as Process, Stdio};
use std::time::{Duration, Instant};

pub const MODULE_ID: &str = "finasu-sui";
const MODULES: &str = "/data/adb/modules";
const MODULE_DIR: &str = "/data/adb/modules/finasu-sui";
const STATE_DIR: &str = "/data/adb/ksu/sui";
const STATE: &str = "/data/adb/ksu/sui/state.json";
const BOOT_MARKER: &str = "/data/adb/ksu/sui/boot";
const DAEMON_LOG: &str = "/data/adb/ksu/sui/daemon.log";
const WATCHDOG_LOCK: &str = "/data/adb/ksu/sui/watchdog.lock";
const MANAGER_IDENTITY: &str = "/data/adb/ksu/sui/manager.json";
/// Only the FinaSU manager may manage Sui; the signed package is the one the kernel trusts.
const MANAGER_PACKAGES: [&str; 2] = ["me.yuki.fina", "me.yuki.fina.pr"];
const DAEMON_READY_TIMEOUT: Duration = Duration::from_secs(120);
/// The zygote may only map and execute libraries labelled this way, and the injected processes
/// have to read the dex and metadata below the module directory.
const LIB_CON: &str = "u:object_r:system_lib_file:s0";
const SYSTEM_CON: &str = "u:object_r:system_file:s0";
const LOCK: &str = "/data/adb/ksu/sui.lock";
const CONFLICTING_MODULE: &str = "zygisk-sui";
const DAEMON: &str = "sui";
const SHELL_DAEMON: &str = "sui_shell";
const DISABLE_MARKER: &str = "disable";
/// Placeholders in the installed rule are resolved, so its bytes differ from the asset.
const MUTATED_FILES: [&str; 1] = ["sepolicy.rule"];

#[derive(clap::Subcommand, Debug)]
pub enum Command {
    /// Report the Sui extension state and its dependencies
    Status {
        #[arg(long)]
        json: bool,
    },
    /// Install the payload and select Sui for the next boot
    Enable,
    /// Stop Sui and keep it out of the next boot
    Disable,
    /// Reinstall the payload from the embedded assets
    Repair,
    /// Record the FinaSU manager identity that Sui may trust for management
    RegisterManager,
    /// Keep the Sui daemons alive while Sui stays enabled
    Watchdog,
    /// Remove Sui; --purge also deletes its permission database
    Uninstall {
        #[arg(long)]
        purge: bool,
    },
}

#[derive(Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct State {
    schema_version: u32,
    enabled: bool,
    payload_version: String,
    payload_hash: String,
}

impl Default for State {
    fn default() -> Self {
        Self {
            schema_version: 1,
            enabled: false,
            payload_version: String::new(),
            payload_hash: String::new(),
        }
    }
}

impl State {
    fn load() -> Result<Self> {
        match fs::read(STATE) {
            Ok(bytes) => serde_json::from_slice(&bytes).context("Invalid Sui state"),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(Self::default()),
            Err(error) => Err(error).context("Read Sui state"),
        }
    }

    fn save(&self) -> Result<()> {
        fs::create_dir_all(STATE_DIR)?;
        let mut file = tempfile::NamedTempFile::new_in(STATE_DIR)?;
        serde_json::to_writer(&mut file, self).context("Serialize Sui state")?;
        file.write_all(b"\n")?;
        file.persist(STATE).context("Persist Sui state")?;
        Ok(())
    }
}

fn lock() -> Result<File> {
    fs::create_dir_all(STATE_DIR)?;
    let lock = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(LOCK)?;
    ensure!(
        unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } == 0,
        "Another Sui operation is already running"
    );
    Ok(lock)
}

#[derive(Deserialize)]
struct Manifest {
    version: String,
    version_code: u32,
    primary_abi: String,
    files: Vec<ManifestFile>,
}

#[derive(Deserialize, Serialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
struct ManagerIdentity {
    schema_version: u32,
    package: String,
    uid: u32,
    cert_size: u32,
    cert_sha256: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    previous_uid: Option<u32>,
}

#[derive(Deserialize)]
struct ManifestFile {
    name: String,
    mode: u32,
    sha256: String,
}

struct Payload {
    manifest: Manifest,
    files: Vec<(String, Vec<u8>)>,
}

impl Payload {
    fn embedded() -> Result<Self> {
        let files = crate::assets::sui_payload()?;
        let manifest = files
            .iter()
            .find(|(name, _)| name == "payload.json")
            .map(|(_, data)| serde_json::from_slice(data))
            .context("The embedded Sui payload has no payload.json")?
            .context("Invalid Sui payload manifest")?;
        let payload = Self { manifest, files };
        for file in &payload.manifest.files {
            let data = payload.contents(&file.name)?;
            ensure!(
                sha256::digest(data) == file.sha256,
                "Sui payload hash mismatch: {}",
                file.name
            );
        }
        Ok(payload)
    }

    fn contents(&self, name: &str) -> Result<&[u8]> {
        self.files
            .iter()
            .find(|(entry, _)| entry == name)
            .map(|(_, data)| data.as_slice())
            .with_context(|| format!("Sui payload is missing {name}"))
    }

    fn hash(&self) -> String {
        let mut identity = Vec::new();
        for file in &self.manifest.files {
            identity.extend_from_slice(file.name.as_bytes());
            identity.extend_from_slice(file.sha256.as_bytes());
        }
        sha256::digest(&identity)
    }

    fn destination(&self, name: &str) -> String {
        if name == "zygisk.so" {
            return format!("zygisk/{}.so", self.manifest.primary_abi);
        }
        name.strip_prefix("zygisk.")
            .and_then(|rest| rest.strip_suffix(".so"))
            .map_or_else(|| name.to_owned(), |abi| format!("zygisk/{abi}.so"))
    }

    fn verify_installed(&self) -> bool {
        for file in &self.manifest.files {
            if MUTATED_FILES.contains(&file.name.as_str()) {
                continue;
            }
            let path = Path::new(MODULE_DIR).join(self.destination(&file.name));
            let Ok(data) = fs::read(&path) else {
                return false;
            };
            if sha256::digest(&data) != file.sha256 {
                return false;
            }
        }
        true
    }
}

fn su_type() -> Result<String> {
    let context = fs::read_to_string("/proc/self/attr/current")
        .context("Read the current SELinux context")?;
    let context = context.trim();
    let parts: Vec<&str> = context.split(':').collect();
    ensure!(parts.len() >= 3, "Unexpected SELinux context: {context}");
    Ok(parts[2].to_owned())
}

fn adb_data_type() -> String {
    let fallback = "adb_data_file".to_owned();
    let Ok(output) = Process::new("/system/bin/ls")
        .args(["-Zd", "/data/adb"])
        .output()
    else {
        return fallback;
    };
    let text = String::from_utf8_lossy(&output.stdout);
    let Some(first) = text.split_whitespace().next() else {
        return fallback;
    };
    let parts: Vec<&str> = first.split(':').collect();
    parts
        .get(2)
        .map(|value| (*value).to_owned())
        .unwrap_or(fallback)
}

fn module_prop(manifest: &Manifest) -> String {
    format!(
        "id={MODULE_ID}\nname=Sui (FinaSU)\nversion=v{}\nversionCode={}\n\
         author=Sui Contributors, FinaSU\n\
         description=Sui built into FinaSU: Shizuku API for apps that support it. \
         Requires a working Zygisk provider.\n",
        manifest.version, manifest.version_code
    )
}

fn stage(root: &Path, payload: &Payload) -> Result<()> {
    for file in &payload.manifest.files {
        let path = root.join(payload.destination(&file.name));
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(&path, payload.contents(&file.name)?)
            .with_context(|| format!("Write {}", path.display()))?;
        fs::set_permissions(&path, fs::Permissions::from_mode(file.mode))?;
    }

    // The root implementation's domain and /data/adb's type are only known on device.
    let rule = root.join("sepolicy.rule");
    let contents = fs::read_to_string(&rule).context("Read the Sui sepolicy rule")?;
    let resolved = contents
        .replace("%su_secon%", &su_type()?)
        .replace("%adb_data_file_secon%", &adb_data_type());
    fs::write(&rule, resolved)?;
    fs::set_permissions(&rule, fs::Permissions::from_mode(0o644))?;

    let prop = root.join("module.prop");
    fs::write(&prop, module_prop(&payload.manifest))?;
    fs::set_permissions(&prop, fs::Permissions::from_mode(0o644))?;
    label_tree(root)?;
    Ok(())
}

fn label_tree(root: &Path) -> Result<()> {
    label(root, SYSTEM_CON)?;
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        if entry.file_type()?.is_dir() {
            label_tree(&path)?;
            continue;
        }
        let context = if path.extension().is_some_and(|extension| extension == "so") {
            LIB_CON
        } else {
            SYSTEM_CON
        };
        label(&path, context)?;
    }
    Ok(())
}

#[cfg(target_os = "android")]
fn label(path: &Path, context: &str) -> Result<()> {
    crate::restorecon::lsetfilecon(path, context)
        .with_context(|| format!("Label {}", path.display()))
}

#[cfg(not(target_os = "android"))]
fn label(_path: &Path, _context: &str) -> Result<()> {
    Ok(())
}

fn publish(payload: &Payload, disabled: bool) -> Result<()> {
    fs::create_dir_all(MODULES)?;
    let pid = std::process::id();
    let target = PathBuf::from(MODULE_DIR);
    let incoming = Path::new(MODULES).join(format!(".finasu-sui.new.{pid}"));
    let outgoing = Path::new(MODULES).join(format!(".finasu-sui.old.{pid}"));
    if incoming.exists() {
        fs::remove_dir_all(&incoming)?;
    }
    stage(&incoming, payload)?;
    if disabled {
        fs::write(incoming.join(DISABLE_MARKER), b"")?;
    }

    let previous = target.exists();
    if previous {
        fs::rename(&target, &outgoing).context("Detach the installed Sui module")?;
    }
    if let Err(error) = fs::rename(&incoming, &target) {
        if previous {
            let _ = fs::rename(&outgoing, &target);
        }
        return Err(error).context("Publish the Sui module directory");
    }
    if previous {
        fs::remove_dir_all(&outgoing)?;
    }
    Ok(())
}

fn conflicts() -> Vec<String> {
    let mut found = Vec::new();
    for root in [MODULES, "/data/adb/modules_update"] {
        if Path::new(root).join(CONFLICTING_MODULE).is_dir() {
            found.push(format!("{root}/{CONFLICTING_MODULE}"));
        }
    }
    found
}

#[derive(Debug, PartialEq)]
enum Gate {
    Ready,
    PendingReboot,
    ExternalUnverified,
    Blocked(&'static str, String),
}

fn gate(status: &crate::zygisk::ProviderStatus, conflicts: &[String]) -> Gate {
    if !conflicts.is_empty() {
        return Gate::Blocked("conflict_existing_sui", conflicts.join(", "));
    }
    if !status.external_providers.is_empty() {
        return Gate::ExternalUnverified;
    }
    if !status.enabled {
        return Gate::Blocked("zygisk_disabled", String::new());
    }
    if !status.engine_available || !status.enable_blockers.is_empty() {
        return Gate::Blocked("zygisk_engine_missing", status.enable_blockers.join(", "));
    }
    if status.startup_blocked {
        return Gate::Blocked("zygisk_startup_protection", String::new());
    }
    match status.injection_state {
        "injected" => Gate::Ready,
        "unverified" | "starting" => Gate::PendingReboot,
        other => Gate::Blocked("zygisk_injection_failed", other.to_owned()),
    }
}

fn process_ids(name: &str) -> Vec<i32> {
    // The injected servers share argv[0], so ask pidof the same way the upstream watchdog does.
    let Ok(output) = Process::new("/system/bin/pidof").arg(name).output() else {
        return Vec::new();
    };
    let mut pids: Vec<i32> = String::from_utf8_lossy(&output.stdout)
        .split_whitespace()
        .filter_map(|value| value.parse().ok())
        .filter(|pid| live_process(*pid, name))
        .collect();
    pids.sort_unstable();
    pids
}

fn live_process(pid: i32, name: &str) -> bool {
    let Ok(stat) = fs::read_to_string(format!("/proc/{pid}/stat")) else {
        return false;
    };
    if !process_running(&stat) {
        return false;
    }
    let Ok(status) = fs::read_to_string(format!("/proc/{pid}/status")) else {
        return false;
    };
    let expected_uid = if name == SHELL_DAEMON { "2000" } else { "0" };
    status.lines().any(|line| {
        line.strip_prefix("Uid:")
            .and_then(|uids| uids.split_whitespace().next())
            == Some(expected_uid)
    })
}

fn process_running(stat: &str) -> bool {
    stat.rsplit_once(") ")
        .and_then(|(_, fields)| fields.split_whitespace().next())
        .is_some_and(|state| !matches!(state, "Z" | "X" | "x"))
}

fn boot_marker_matches() -> bool {
    fs::read_to_string(BOOT_MARKER)
        .is_ok_and(|stored| stored.trim() == crate::nomount::current_boot_id())
}

fn write_boot_marker() -> Result<()> {
    fs::create_dir_all(STATE_DIR)?;
    let mut file = tempfile::NamedTempFile::new_in(STATE_DIR)?;
    file.as_file()
        .set_permissions(fs::Permissions::from_mode(0o600))?;
    file.write_all(crate::nomount::current_boot_id().as_bytes())?;
    file.write_all(b"\n")?;
    file.persist(BOOT_MARKER)
        .context("Persist the Sui boot marker")?;
    Ok(())
}

#[cfg(target_os = "android")]
fn boot_completed() -> bool {
    crate::utils::getprop("sys.boot_completed").as_deref() == Some("1")
}

#[cfg(not(target_os = "android"))]
fn boot_completed() -> bool {
    false
}

#[cfg(target_os = "android")]
fn apply_sepolicy_rule() -> Result<()> {
    crate::sepolicy::apply_file(Path::new(MODULE_DIR).join("sepolicy.rule"))
}

#[cfg(not(target_os = "android"))]
fn apply_sepolicy_rule() -> Result<()> {
    Ok(())
}

#[cfg(target_os = "android")]
fn leave_service_cgroup() -> std::io::Result<()> {
    crate::zygisk::detach_from_service_cgroup()
}

#[cfg(not(target_os = "android"))]
fn leave_service_cgroup() -> std::io::Result<()> {
    Ok(())
}

fn start_daemon() -> Result<Child> {
    let module = Path::new(MODULE_DIR);
    let binary = module.join("bin/sui");
    ensure!(binary.is_file(), "The Sui payload is not installed");
    // The daemon's own output is the only diagnostics available from the boot stage, and a
    // pipeline into logcat would take the daemon down with it if the reader ever exits.
    fs::create_dir_all(STATE_DIR)?;
    let log = OpenOptions::new()
        .create(true)
        .append(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(DAEMON_LOG)
        .context("Open the Sui daemon log")?;
    log.set_permissions(fs::Permissions::from_mode(0o600))?;
    let mut command = Process::new(&binary);
    command
        .arg(module)
        .env("SUI_FOREGROUND", "1")
        .stdin(Stdio::null())
        .stdout(log.try_clone()?)
        .stderr(log);
    unsafe {
        command.pre_exec(|| {
            // init SIGKILLs the whole post-fs-data cgroup when that service exits.
            leave_service_cgroup()?;
            libc::setsid();
            Ok(())
        });
    }
    command.spawn().context("Start the Sui daemon")
}

fn stop_daemon() {
    let running: Vec<i32> = process_ids(DAEMON)
        .into_iter()
        .chain(process_ids(SHELL_DAEMON))
        .collect();
    if running.is_empty() {
        return;
    }
    for pid in &running {
        unsafe { libc::kill(*pid, libc::SIGTERM) };
    }
    std::thread::sleep(Duration::from_millis(500));
    for pid in process_ids(DAEMON)
        .into_iter()
        .chain(process_ids(SHELL_DAEMON))
    {
        unsafe { libc::kill(pid, libc::SIGKILL) };
    }
}

#[derive(Serialize)]
#[allow(clippy::struct_excessive_bools)]
pub struct Status {
    schema_version: u32,
    state: &'static str,
    reason: &'static str,
    detail: String,
    expectation: bool,
    installed: bool,
    payload_version: String,
    payload_current: bool,
    module_disabled: bool,
    manager_registered: bool,
    reboot_required: bool,
    conflicts: Vec<String>,
    zygisk_enabled: bool,
    engine_available: bool,
    injection_state: String,
    external_providers: Vec<String>,
    root_pids: Vec<i32>,
    shell_pids: Vec<i32>,
    daemon_healthy: bool,
    boot_marker_matches: bool,
    manager_identity: Option<ManagerIdentity>,
    manager_diagnostics: serde_json::Value,
}

fn manager_diagnostics(uid: Option<u32>) -> serde_json::Value {
    let uid = uid.map_or_else(|| "-1".to_owned(), |uid| uid.to_string());
    let output = (|| -> Result<std::process::Output> {
        let mut child = Process::new("/system/bin/app_process")
            .env("CLASSPATH", format!("{MODULE_DIR}/sui.dex"))
            .args(["/system/bin", "rikka.sui.server.SuiDiagnostics", &uid])
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()?;
        let started = Instant::now();
        while child.try_wait()?.is_none() {
            if started.elapsed() >= Duration::from_secs(5) {
                let _ = child.kill();
                let _ = child.wait();
                bail!("Sui diagnostics timed out");
            }
            std::thread::sleep(Duration::from_millis(50));
        }
        Ok(child.wait_with_output()?)
    })();
    match output {
        Ok(output) if output.status.success() => serde_json::from_slice(&output.stdout)
            .unwrap_or_else(
                |error| serde_json::json!({"state": "unknown", "error": error.to_string()}),
            ),
        Ok(output) => {
            serde_json::json!({"state": "unknown", "error": String::from_utf8_lossy(&output.stderr)})
        }
        Err(error) => serde_json::json!({"state": "unknown", "error": error.to_string()}),
    }
}

fn collect() -> Result<Status> {
    let provider = crate::zygisk::provider_status()?;
    let state = State::load()?;
    let payload = Payload::embedded().ok();
    let installed = payload.as_ref().is_some_and(Payload::verify_installed);
    let payload_current = payload
        .as_ref()
        .is_some_and(|payload| payload.hash() == state.payload_hash);
    let module_disabled = Path::new(MODULE_DIR).join(DISABLE_MARKER).exists();
    let conflicts = conflicts();
    let root_pids = process_ids(DAEMON);
    let shell_pids = process_ids(SHELL_DAEMON);
    let daemon_healthy = root_pids.len() == 1 && shell_pids.len() == 1;
    let provider_gate = gate(&provider, &conflicts);
    let manager_identity: Option<ManagerIdentity> = fs::read(MANAGER_IDENTITY)
        .ok()
        .and_then(|raw| serde_json::from_slice(&raw).ok());
    let manager_diagnostics = if installed {
        manager_diagnostics(manager_identity.as_ref().map(|identity| identity.uid))
    } else {
        serde_json::json!({"state": "skipped", "reason": "payload_not_installed"})
    };

    let (name, reason): (&'static str, &'static str) = if !conflicts.is_empty() {
        ("error", "conflict_existing_sui")
    } else if state.enabled {
        if !installed {
            ("error", "payload_missing")
        } else if module_disabled {
            ("error", "module_disabled")
        } else {
            let booted = boot_marker_matches();
            match provider_gate {
                Gate::Blocked(code, _) => ("unavailable", code),
                Gate::ExternalUnverified if daemon_healthy && booted => {
                    ("enabled_external_unverified", "")
                }
                Gate::Ready if daemon_healthy && booted => ("enabled", ""),
                Gate::Ready | Gate::PendingReboot | Gate::ExternalUnverified => {
                    ("enabled_pending_reboot", "")
                }
            }
        }
    } else if !installed {
        ("disabled", "not_installed")
    } else if module_disabled {
        ("disabled", "")
    } else {
        ("error", "enabled_externally")
    };

    let detail = match &provider_gate {
        Gate::Blocked(_, detail) => detail.clone(),
        _ => String::new(),
    };
    Ok(Status {
        schema_version: 1,
        state: name,
        reason,
        detail,
        expectation: state.enabled,
        installed,
        payload_version: state.payload_version,
        payload_current,
        module_disabled,
        manager_registered: manager_identity.is_some(),
        reboot_required: name == "enabled_pending_reboot",
        conflicts,
        zygisk_enabled: provider.enabled,
        engine_available: provider.engine_available,
        injection_state: provider.injection_state.to_owned(),
        external_providers: provider.external_providers,
        root_pids,
        shell_pids,
        daemon_healthy,
        boot_marker_matches: boot_marker_matches(),
        manager_identity,
        manager_diagnostics,
    })
}

fn enable() -> Result<()> {
    let payload = Payload::embedded()
        .context("The embedded Sui payload is unavailable for this architecture")?;
    let provider = crate::zygisk::provider_status()?;
    let conflicts = conflicts();
    if let Gate::Blocked(code, detail) = gate(&provider, &conflicts) {
        let suffix = if detail.is_empty() {
            String::new()
        } else {
            format!(": {detail}")
        };
        bail!("SUI_BLOCKED: {code}{suffix}");
    }

    publish(&payload, false)?;
    let mut state = State::load()?;
    state.enabled = true;
    state.payload_version.clone_from(&payload.manifest.version);
    state.payload_hash = payload.hash();
    state.save()?;

    if let Err(error) = register_manager() {
        log::warn!("Sui manager registration failed: {error:#}");
    }
    if let Err(error) = apply_sepolicy_rule() {
        log::warn!("Sui sepolicy rule failed to apply: {error:#}");
    }
    if boot_completed() {
        spawn_watchdog()?;
    }
    Ok(())
}

fn disable() -> Result<()> {
    stop_daemon();
    let module = Path::new(MODULE_DIR);
    if module.is_dir() {
        fs::write(module.join(DISABLE_MARKER), b"")?;
    }
    let mut state = State::load()?;
    state.enabled = false;
    state.save()?;
    Ok(())
}

fn repair() -> Result<()> {
    let payload = Payload::embedded()
        .context("The embedded Sui payload is unavailable for this architecture")?;
    let mut state = State::load()?;
    publish(&payload, !state.enabled)?;
    state.payload_version.clone_from(&payload.manifest.version);
    state.payload_hash = payload.hash();
    state.save()?;
    if let Err(error) = register_manager() {
        log::warn!("Sui manager registration failed: {error:#}");
    }
    Ok(())
}

fn uninstall(purge: bool) -> Result<()> {
    stop_daemon();
    let module = Path::new(MODULE_DIR);
    if module.is_dir() {
        let binary = module.join("bin/sui");
        if binary.is_file() {
            let status = Process::new(&binary)
                .arg0("uninstall")
                .arg(module)
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status();
            if let Ok(status) = status
                && !status.success()
            {
                log::warn!("The Sui uninstaller exited with {status}");
            }
        }
        let removed =
            Path::new(MODULES).join(format!(".finasu-sui.removed.{}", std::process::id()));
        fs::rename(module, &removed).context("Detach the Sui module directory")?;
        fs::remove_dir_all(&removed)?;
    }

    if let Ok(entries) = fs::read_dir("/data/local/tmp") {
        for entry in entries.flatten() {
            let name = entry.file_name();
            let Some(name) = name.to_str() else { continue };
            if name != "sui_shell" && !name.starts_with("sui_shell_") {
                continue;
            }
            let path = entry.path();
            let _ = if path.is_dir() {
                fs::remove_dir_all(&path)
            } else {
                fs::remove_file(&path)
            };
        }
    }

    if purge {
        for path in ["/data/adb/sui", "/data/system/sui"] {
            if Path::new(path).exists() {
                let _ = fs::remove_dir_all(path);
            }
        }
    }
    let _ = fs::remove_file(STATE);
    Ok(())
}

fn pm(args: &[&str]) -> Result<String> {
    let output = Process::new("/system/bin/pm")
        .args(args)
        .output()
        .context("Run pm")?;
    ensure!(output.status.success(), "pm {} failed", args.join(" "));
    Ok(String::from_utf8_lossy(&output.stdout).into_owned())
}

fn manager_package() -> Result<&'static str> {
    for package in MANAGER_PACKAGES {
        if pm(&["path", package]).is_ok_and(|out| out.contains("package:")) {
            return Ok(package);
        }
    }
    bail!("No FinaSU manager package is installed")
}

fn manager_uid(package: &str) -> Result<u32> {
    let expected = format!("package:{package}");
    for line in pm(&["list", "packages", "-U", "--user", "0"])?.lines() {
        let mut fields = line.split_whitespace();
        if fields.next() != Some(expected.as_str()) {
            continue;
        }
        if let Some(uid) = fields.next().and_then(|field| field.strip_prefix("uid:")) {
            return uid.parse().context("Parse the manager uid");
        }
    }
    bail!("Cannot resolve the uid of {package}")
}

fn register_manager() -> Result<()> {
    let package = manager_package()?;
    let apk = pm(&["path", package])?
        .lines()
        .find_map(|line| line.strip_prefix("package:"))
        .context("Cannot resolve the manager APK path")?
        .trim()
        .to_owned();
    let (cert_size, cert_sha256) =
        crate::apk_sign::get_apk_signature(&apk).context("Read the manager APK signature")?;
    let previous: Option<ManagerIdentity> = fs::read(MANAGER_IDENTITY)
        .ok()
        .and_then(|raw| serde_json::from_slice(&raw).ok());
    let uid = manager_uid(package)?;
    let previous_uid = previous.as_ref().and_then(|identity| {
        if identity.uid == uid {
            identity.previous_uid
        } else {
            Some(identity.uid)
        }
    });
    let identity = ManagerIdentity {
        schema_version: 1,
        package: package.to_owned(),
        uid,
        cert_size,
        cert_sha256,
        previous_uid,
    };
    if previous.as_ref() == Some(&identity) {
        return Ok(());
    }
    fs::create_dir_all(STATE_DIR)?;
    let mut file = tempfile::NamedTempFile::new_in(STATE_DIR)?;
    file.as_file()
        .set_permissions(fs::Permissions::from_mode(0o600))?;
    serde_json::to_writer(&mut file, &identity).context("Serialize the manager identity")?;
    file.write_all(b"\n")?;
    file.as_file().sync_all()?;
    file.persist(MANAGER_IDENTITY)
        .context("Persist the manager identity")?;
    log::info!(
        "Sui manager identity: {} uid={} cert={} bytes",
        identity.package,
        identity.uid,
        identity.cert_size
    );
    Ok(())
}

pub fn run(command: &Command) -> Result<()> {
    match command {
        Command::Status { json } => {
            let status = collect()?;
            if *json {
                println!("{}", serde_json::to_string(&status)?);
            } else {
                println!("{}", serde_json::to_string_pretty(&status)?);
            }
            Ok(())
        }
        Command::Enable => {
            let _lock = lock()?;
            enable()
        }
        Command::Disable => {
            let _lock = lock()?;
            disable()
        }
        Command::Repair => {
            let _lock = lock()?;
            repair()
        }
        Command::RegisterManager => {
            let _lock = lock()?;
            register_manager()
        }
        Command::Watchdog => watchdog(),
        Command::Uninstall { purge } => {
            let _lock = lock()?;
            uninstall(*purge)
        }
    }
}

pub fn post_fs_data(safe_mode: bool) -> Result<()> {
    if safe_mode || !active()? {
        return Ok(());
    }
    spawn_watchdog()
}

pub fn services() -> Result<()> {
    if !active()? {
        return Ok(());
    }
    spawn_watchdog()
}

fn daemon_healthy() -> bool {
    process_ids(DAEMON).len() == 1 && process_ids(SHELL_DAEMON).len() == 1
}

/// One supervisor per device: it restarts the daemon pair while Sui stays enabled and exits
/// as soon as Sui is disabled, so it never outlives the extension it watches.
fn watchdog() -> Result<()> {
    let lock = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(WATCHDOG_LOCK)?;
    ensure!(
        unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } == 0,
        "Another Sui watchdog is already running"
    );
    let mut child: Option<Child> = None;
    let mut started = Instant::now();
    let mut refreshed: Option<Instant> = None;
    loop {
        if !active()? {
            if let Some(mut child) = child {
                let _ = child.kill();
                let _ = child.wait();
            }
            return Ok(());
        }
        if boot_completed()
            && refreshed.is_none_or(|time| time.elapsed() >= Duration::from_secs(30))
        {
            if let Err(error) = register_manager() {
                log::warn!("Sui manager refresh failed: {error:#}");
            }
            refreshed = Some(Instant::now());
        }
        if let Some(process) = child.as_mut()
            && process.try_wait()?.is_some()
        {
            child = None;
        }
        if daemon_healthy() {
            if !boot_marker_matches() {
                write_boot_marker()?;
            }
        } else if (child.is_none()
            || (boot_completed() && started.elapsed() >= DAEMON_READY_TIMEOUT))
            && let Ok(_operation) = self::lock()
            && active()?
        {
            stop_daemon();
            if let Some(mut previous) = child.take() {
                let _ = previous.kill();
                let _ = previous.wait();
            }
            match start_daemon() {
                Ok(process) => {
                    child = Some(process);
                    started = Instant::now();
                }
                Err(error) => log::warn!("Sui watchdog restart failed: {error:#}"),
            }
        }
        std::thread::sleep(Duration::from_secs(1));
    }
}

fn spawn_watchdog() -> Result<()> {
    let mut command = Process::new("/data/adb/ksud");
    command
        .args(["sui", "watchdog"])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    unsafe {
        command.pre_exec(|| {
            leave_service_cgroup()?;
            libc::setsid();
            Ok(())
        });
    }
    let mut child = command.spawn().context("Start the Sui watchdog")?;
    std::thread::spawn(move || {
        let _ = child.wait();
    });
    Ok(())
}

fn active() -> Result<bool> {
    if !State::load()?.enabled {
        return Ok(false);
    }
    let module = Path::new(MODULE_DIR);
    Ok(module.join("sui.dex").is_file() && !module.join(DISABLE_MARKER).exists())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::zygisk::ProviderStatus;

    #[test]
    fn process_state_rejects_zombies_and_malformed_records() {
        assert!(process_running("123 (sui) S 1 2 3"));
        assert!(process_running("123 (name with ) parentheses) R 1 2 3"));
        assert!(!process_running("123 (sui) Z 1 2 3"));
        assert!(!process_running("123 (sui) X 1 2 3"));
        assert!(!process_running("123 (sui) x 1 2 3"));
        assert!(!process_running("123 (sui) "));
        assert!(!process_running("invalid"));
    }

    fn provider(enabled: bool, engine: bool, injection: &'static str) -> ProviderStatus {
        ProviderStatus {
            schema_version: 1,
            enabled,
            engine_available: engine,
            enable_blockers: Vec::new(),
            external_providers: Vec::new(),
            injection_state: injection,
            reboot_required: None,
            runtime: None,
            startup_failures: 0,
            startup_blocked: false,
            startup_failure_code: None,
            startup_failure_detail: None,
            startup_generation: 1,
        }
    }

    #[test]
    fn gate_requires_an_injected_provider() {
        assert_eq!(
            gate(&provider(false, true, "unverified"), &[]),
            Gate::Blocked("zygisk_disabled", String::new())
        );
        assert_eq!(
            gate(&provider(true, false, "unverified"), &[]),
            Gate::Blocked("zygisk_engine_missing", String::new())
        );
        assert_eq!(gate(&provider(true, true, "injected"), &[]), Gate::Ready);
        assert_eq!(
            gate(&provider(true, true, "failed"), &[]),
            Gate::Blocked("zygisk_injection_failed", "failed".to_owned())
        );
    }

    #[test]
    fn gate_accepts_pending_reboot_and_external_providers() {
        assert_eq!(
            gate(&provider(true, true, "unverified"), &[]),
            Gate::PendingReboot
        );
        let mut external = provider(false, false, "unverified");
        external.external_providers = vec!["zygisknext".to_owned()];
        assert_eq!(gate(&external, &[]), Gate::ExternalUnverified);
    }

    #[test]
    fn gate_refuses_a_conflicting_install() {
        let conflicts = vec!["/data/adb/modules/zygisk-sui".to_owned()];
        assert_eq!(
            gate(&provider(true, true, "injected"), &conflicts),
            Gate::Blocked(
                "conflict_existing_sui",
                "/data/adb/modules/zygisk-sui".to_owned()
            )
        );
    }
}
