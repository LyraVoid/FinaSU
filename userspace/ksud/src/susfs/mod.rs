//! FinaSU-owned SUSFS management. Never installs ksu_susfs or edits module configs.
#![cfg_attr(not(target_os = "android"), allow(dead_code))]
mod abi;
mod apply;
mod model;
mod store;

use anyhow::{Context, Result, bail};
use serde::Serialize;
use std::io::Read;

pub use model::Config;

#[derive(Clone, Copy, Default, PartialEq, Eq, Serialize)]
pub struct UnameProviderState {
    pub applied: bool,
    pub scheduled: bool,
    pub uncertain: bool,
}

impl UnameProviderState {
    pub const fn blocks_uts(self) -> bool {
        self.applied
    }

    pub const fn needs_boot_resolution(self) -> bool {
        self.scheduled || self.uncertain
    }

    pub const fn known_not_applied(self) -> bool {
        !self.applied && !self.uncertain
    }
}

#[derive(Clone)]
pub struct UnameTakeoverSnapshot {
    config: Config,
    state: store::State,
    applied: Option<model::Uname>,
}

#[derive(Debug, Clone, clap::Subcommand)]
pub enum Command {
    /// Query the root-only control ABI and the FinaSU management state
    Status,
    /// Export the independent, versioned FinaSU configuration
    Export,
    /// Validate and atomically save JSON from stdin; does not apply it
    Import,
    /// Apply supported live operations; never fabricates pre-mount kstat
    Apply,
    /// Begin an ownership switch; confirmation is required after a reboot
    Switch { target: String },
    /// Confirm the pending switch after checking external writers and runtime state
    ConfirmSwitch,
}

#[derive(Debug, Clone, Serialize)]
pub struct Detection {
    pub status: &'static str,
    pub version: String,
    pub variant: String,
    pub implementation: &'static str,
    pub features: Vec<String>,
    pub proc_nodes: Vec<String>,
}

impl Detection {
    pub fn supports(&self, feature: &str) -> bool {
        self.status == "compatible" && self.features.iter().any(|f| f == feature)
    }

    pub fn proc(&self, node: &str) -> bool {
        self.status == "compatible"
            && self.implementation == "lkm"
            && self.proc_nodes.iter().any(|n| n == node)
    }
}

pub const PROC_NODES: [&str; 7] = [
    "susfs_kstat",
    "susfs_open_redirect",
    "susfs_enable_log",
    "susfs_avc_spoof",
    "susfs_hide_modules",
    "susfs_hide_mounts",
    "susfs_path",
];

fn query<const N: usize>(command: u32) -> (i32, Option<String>) {
    let mut payload = abi::Text::<N>::empty();
    abi::call(command, &mut payload);
    (payload.err, abi::string(&payload.text))
}

fn classify(
    version: &(i32, Option<String>),
    features: &(i32, Option<String>),
    variant: &(i32, Option<String>),
) -> Detection {
    let detected = version.0 != 126 || features.0 != 126 || variant.0 != 126;
    let tokens: Vec<String> = features
        .1
        .as_deref()
        .unwrap_or_default()
        .lines()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .collect();
    // GKI-LKM is upstream's clearer label for the same v2.3.0 wire layout since r18.4.
    // Other revisions remain visible/read-only.
    let compatible = version.0 == 0
        && features.0 == 0
        && variant.0 == 0
        && version.1.as_deref() == Some("v2.3.0")
        && matches!(variant.1.as_deref(), Some("GKI" | "NON-GKI" | "GKI-LKM"))
        && !tokens.is_empty()
        && tokens
            .iter()
            .all(|s| s == "SUSFS_LKM_MODULES" || s.starts_with("CONFIG_KSU_SUSFS_"));
    let lkm = tokens.iter().any(|s| s == "SUSFS_LKM_MODULES");
    Detection {
        status: if !detected {
            "not_detected"
        } else if compatible {
            "compatible"
        } else {
            "unknown_protocol"
        },
        version: version.1.clone().unwrap_or_default(),
        variant: variant.1.clone().unwrap_or_default(),
        implementation: if lkm {
            "lkm"
        } else if compatible {
            "builtin"
        } else {
            "unknown"
        },
        features: tokens,
        proc_nodes: Vec::new(),
    }
}

pub fn detect() -> Detection {
    if unsafe { libc::geteuid() } != 0 {
        return Detection {
            status: "root_unavailable",
            version: String::new(),
            variant: String::new(),
            implementation: "unknown",
            features: Vec::new(),
            proc_nodes: Vec::new(),
        };
    }
    let mut detection = classify(
        &query::<16>(0x0005_55e1),
        &query::<8192>(0x0005_55e2),
        &query::<16>(0x0005_55e3),
    );
    if detection.status == "compatible" && detection.implementation == "lkm" {
        detection.proc_nodes = PROC_NODES
            .iter()
            .filter(|node| {
                std::fs::OpenOptions::new()
                    .write(true)
                    .open(format!("/proc/{node}"))
                    .is_ok()
            })
            .map(|node| (*node).to_owned())
            .collect();
    }
    detection
}

pub fn lock_store() -> Result<store::Lock> {
    store::lock()
}

pub fn uname_provider_state() -> Result<UnameProviderState> {
    if !detect().supports("CONFIG_KSU_SUSFS_SPOOF_UNAME") {
        return Ok(UnameProviderState::default());
    }
    let config = store::read_config()?;
    let state = store::read_state()?;
    let mut provider = uname_provider(&config, &state);
    if store::read_ownership()?.require_builtin().is_err() {
        provider.scheduled = false;
        provider.uncertain = true;
    }
    Ok(provider)
}

fn uname_provider(config: &Config, state: &store::State) -> UnameProviderState {
    let applied = state
        .applied
        .get("uname")
        .and_then(|value| serde_json::from_str::<model::Uname>(value).ok())
        .is_some_and(|uname| !uname.is_default());
    UnameProviderState {
        applied,
        scheduled: config.automatic
            && config
                .settings
                .uname
                .as_ref()
                .is_some_and(|uname| !uname.is_default()),
        uncertain: state.uncertain.contains_key("uname"),
    }
}

pub fn uname_takeover_snapshot() -> Result<UnameTakeoverSnapshot> {
    store::read_ownership()?.require_builtin()?;
    let detection = detect();
    if !detection.supports("CONFIG_KSU_SUSFS_SPOOF_UNAME") {
        bail!("SUSFS uname spoofing is unavailable");
    }
    let config = store::read_config()?;
    let state = store::read_state()?;
    let provider = uname_provider(&config, &state);
    if !(provider.applied || provider.uncertain || provider.scheduled) {
        bail!("SUSFS uname provider is not active or scheduled");
    }
    let applied = state
        .applied
        .get("uname")
        .and_then(|value| serde_json::from_str::<model::Uname>(value).ok());
    Ok(UnameTakeoverSnapshot {
        config,
        state,
        applied,
    })
}

fn set_uname_runtime(release: &str, version: &str) -> Result<()> {
    let detection = detect();
    if !detection.supports("CONFIG_KSU_SUSFS_SPOOF_UNAME") {
        bail!("SUSFS uname spoofing is unavailable");
    }
    let mut payload = abi::Uname {
        release: abi::bytes(release)?,
        version: abi::bytes(version)?,
        err: 126,
    };
    abi::call(0x0005_5590, &mut payload);
    abi::checked(payload.err)
}

pub fn take_uname_provider(snapshot: &UnameTakeoverSnapshot) -> Result<()> {
    store::read_ownership()?.require_builtin()?;
    let mut config = snapshot.config.clone();
    config.settings.uname = None;
    store::write_config(&config).context("Persist SUSFS provider handoff")?;
    set_uname_runtime("default", "default").context("Reset SUSFS uname runtime")?;

    let mut state = store::read_state()?;
    state.applied.remove("uname");
    state.uncertain.remove("uname");
    state.failures.remove("uname");
    state.pending_reboot.retain(|key| key != "uname");
    store::write_state(&state).context("Persist SUSFS uname state handoff")
}

pub fn restore_uname_provider(snapshot: &UnameTakeoverSnapshot) -> Result<()> {
    store::read_ownership()?.require_builtin()?;
    let mut residuals = Vec::new();
    if let Err(error) = store::write_config(&snapshot.config) {
        residuals.push(format!("config:{error:#}"));
    }
    let runtime = snapshot.applied.as_ref().map_or_else(
        || set_uname_runtime("default", "default"),
        |uname| set_uname_runtime(&uname.release, &uname.version),
    );
    if let Err(error) = runtime {
        residuals.push(format!("runtime:{error:#}"));
    }
    if let Err(error) = store::write_state(&snapshot.state) {
        residuals.push(format!("state:{error:#}"));
    }
    if residuals.is_empty() {
        Ok(())
    } else {
        bail!("SUSFS uname restore residuals: {}", residuals.join(","));
    }
}

#[cfg(target_os = "android")]
fn uts_provider_active() -> Result<bool> {
    crate::uts_view::provider_active()
}

#[cfg(not(target_os = "android"))]
fn uts_provider_active() -> Result<bool> {
    Ok(false)
}

fn uname_conflicts(config: &Config, uts_active: bool) -> bool {
    config
        .settings
        .uname
        .as_ref()
        .is_some_and(|uname| !uname.is_default())
        && uts_active
}

fn uts_arbitration_active(config: &Config, query: impl FnOnce() -> Result<bool>) -> Result<bool> {
    if config
        .settings
        .uname
        .as_ref()
        .is_some_and(|uname| !uname.is_default())
    {
        query()
    } else {
        Ok(false)
    }
}

fn status_summary(config: &Config, uts_active: bool) -> Result<&'static str> {
    store::summary_with_deferred(config, uname_conflicts(config, uts_active))
}

fn apply_with_uts_arbitration(
    config: &Config,
    detection: &Detection,
    stage: apply::Stage,
    uts_active: bool,
) -> Result<apply::Report> {
    let defer_uname = uname_conflicts(config, uts_active);
    let mut effective = config.clone();
    if defer_uname {
        effective.settings.uname = None;
    }
    let mut report = apply::apply(&effective, detection, stage)?;
    if defer_uname {
        report.deferred.push("uname".to_owned());
        if matches!(report.status, "applied" | "saved") {
            report.status = "deferred";
        }
    }
    Ok(report)
}

pub fn run(command: Command) -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        bail!("Root is required for SUSFS management");
    }
    match command {
        Command::Status => {
            let detection = detect();
            let uname_provider = uname_provider_state().unwrap_or_default();
            let config = store::read_config().and_then(|config| {
                let ownership = store::read_ownership()?;
                let uts_active =
                    uts_arbitration_active(&config, uts_provider_active).unwrap_or(false);
                let summary = status_summary(&config, uts_active)?;
                Ok((config, summary, ownership))
            });
            let status = match config {
                Ok((config, summary, ownership)) => serde_json::json!({
                    "detection": detection, "automatic": config.automatic,
                    "ownership": ownership,
                    "management": summary, "config_error": null,
                    "uname_applied": uname_provider.applied,
                    "uname_scheduled": uname_provider.scheduled,
                    "uname_uncertain": uname_provider.uncertain,
                }),
                Err(error) => serde_json::json!({
                    "detection": detection, "automatic": false, "management": "failed",
                    "config_error": error.to_string(),
                    "uname_applied": uname_provider.applied,
                    "uname_scheduled": uname_provider.scheduled,
                    "uname_uncertain": uname_provider.uncertain,
                }),
            };
            println!("{status}");
        }
        Command::Export => println!("{}", serde_json::to_string_pretty(&store::read_config()?)?),
        Command::Import => {
            let mut input = String::new();
            std::io::stdin()
                .take((model::MAX_CONFIG + 1) as u64)
                .read_to_string(&mut input)?;
            let config = Config::parse(&input)?;
            let _lock = store::lock()?;
            store::read_ownership()?.require_builtin()?;
            store::write_config(&config)?;
            println!(
                "{}",
                serde_json::json!({"status": "saved", "management": store::summary(&config)?})
            );
        }
        Command::Apply => {
            // UTS transactions take this store lock before changing either
            // provider. Holding it here serializes arbitration without making
            // unrelated SUSFS operations depend on UTS config storage.
            let _lock = store::lock()?;
            store::read_ownership()?.require_builtin()?;
            let config = store::read_config()?;
            let uts_active = uts_arbitration_active(&config, uts_provider_active)?;
            let result =
                apply_with_uts_arbitration(&config, &detect(), apply::Stage::Live, uts_active)?;
            println!("{}", serde_json::to_string(&result)?);
        }
        Command::Switch { target } => {
            let target = match target.as_str() {
                "builtin" => model::ManagementMode::Builtin,
                "external" => model::ManagementMode::External,
                _ => bail!("Expected builtin or external"),
            };
            let _lock = store::lock()?;
            let mut ownership = store::read_ownership()?;
            if ownership.pending.is_some() || ownership.mode == target {
                bail!("SUSFS ownership switch is already pending or active");
            }
            let boot_id = std::fs::read_to_string("/proc/sys/kernel/random/boot_id")?
                .trim()
                .to_owned();
            if boot_id.is_empty() {
                bail!("Boot ID is unavailable");
            }
            ownership.pending = Some(model::ManagementSwitch {
                target,
                requested_boot_id: boot_id,
            });
            store::write_ownership(&ownership)?;
            println!("{}", serde_json::to_string(&ownership)?);
        }
        Command::ConfirmSwitch => {
            let _lock = store::lock()?;
            let mut ownership = store::read_ownership()?;
            let pending = ownership
                .pending
                .as_ref()
                .context("No pending ownership switch")?;
            let boot_id = std::fs::read_to_string("/proc/sys/kernel/random/boot_id")?;
            if !pending.may_confirm(boot_id.trim()) {
                bail!("A normal reboot is required before confirmation");
            }
            ownership.mode = pending.target;
            ownership.pending = None;
            store::write_ownership(&ownership)?;
            println!("{}", serde_json::to_string(&ownership)?);
        }
    }
    Ok(())
}

pub fn boot(stage: &str) {
    let phase = match stage {
        "pre-mount" => apply::Stage::PreMount,
        "post-mount" => apply::Stage::PostMount,
        "service" | "boot-completed" => apply::Stage::Deferred,
        _ => return,
    };
    let result = (|| -> Result<()> {
        // Keep the shared provider state stable for this transaction. UTS
        // commits take the same lock, so a separate UTS config lock would only
        // duplicate serialization and couple SUSFS to UTS file permissions.
        let _lock = store::lock().context("Lock SUSFS state")?;
        let config = store::read_config().context("Read SUSFS configuration")?;
        if store::read_ownership()?.require_builtin().is_err() {
            return Ok(());
        }
        if !config.automatic || safe_mode() {
            return Ok(());
        }
        let uts_active =
            uts_arbitration_active(&config, uts_provider_active).context("Check UTS provider")?;
        let report = apply_with_uts_arbitration(&config, &detect(), phase, uts_active)
            .context("Apply SUSFS operations")?;
        log::info!("SUSFS {stage}: {}", serde_json::to_string(&report)?);
        Ok(())
    })();
    if let Err(error) = result {
        log::warn!("SUSFS {stage} apply: {error:#}");
    }
    #[cfg(target_os = "android")]
    if phase == apply::Stage::Deferred {
        crate::uts_view::resume_after_susfs();
    }
}

fn safe_mode() -> bool {
    #[cfg(target_os = "android")]
    {
        crate::utils::is_safe_mode()
    }
    #[cfg(not(target_os = "android"))]
    {
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn response(value: &str) -> (i32, Option<String>) {
        (0, Some(value.to_owned()))
    }
    #[test]
    fn detection_requires_all_queries_and_audited_protocol() {
        let absent = (126, Some(String::new()));
        assert_eq!(classify(&absent, &absent, &absent).status, "not_detected");
        assert_eq!(
            classify(&response("v2.3.0"), &absent, &absent).status,
            "unknown_protocol"
        );
        assert_eq!(
            classify(
                &response("v9.0.0"),
                &response("CONFIG_KSU_SUSFS_SUS_PATH"),
                &response("GKI")
            )
            .status,
            "unknown_protocol"
        );
        let builtin = classify(
            &response("v2.3.0"),
            &response("CONFIG_KSU_SUSFS_SUS_PATH"),
            &response("GKI"),
        );
        assert_eq!(builtin.implementation, "builtin");
        let lkm = classify(
            &response("v2.3.0"),
            &response("SUSFS_LKM_MODULES\nCONFIG_KSU_SUSFS_SUS_PATH"),
            &response("GKI-LKM"),
        );
        assert_eq!(lkm.status, "compatible");
        assert_eq!(lkm.implementation, "lkm");
        assert_eq!(lkm.variant, "GKI-LKM");
        assert!(!lkm.proc("susfs_kstat"));
        assert_eq!(
            classify(
                &response("v2.3.0"),
                &response("SUSFS_LKM_MODULES\nCONFIG_KSU_SUSFS_SUS_PATH"),
                &response("GKI-LKM-NEXT")
            )
            .status,
            "unknown_protocol"
        );
    }

    #[test]
    fn uname_requires_exclusive_provider_only_when_saved() {
        let mut config = Config::default();
        assert!(!uname_conflicts(&config, true));
        config.settings.uname = Some(model::Uname {
            release: "default".to_owned(),
            version: "default".to_owned(),
        });
        assert!(!uname_conflicts(&config, true));
        config.settings.uname = Some(model::Uname {
            release: "6.1.0-test".to_owned(),
            version: "#1".to_owned(),
        });
        assert!(uname_conflicts(&config, true));
        assert!(!uname_conflicts(&config, false));
    }

    #[test]
    fn status_reports_saved_uname_as_deferred_while_uts_is_active() {
        let mut config = Config::default();
        assert_eq!(status_summary(&config, true).unwrap(), "idle");

        config.settings.uname = Some(model::Uname {
            release: "6.1.0-test".to_owned(),
            version: "#1".to_owned(),
        });
        assert_eq!(status_summary(&config, true).unwrap(), "deferred");
        assert_eq!(status_summary(&config, false).unwrap(), "saved");
    }

    #[test]
    fn susfs_without_uname_does_not_query_uts_provider_state() {
        let mut queried = false;
        let active = uts_arbitration_active(&Config::default(), || {
            queried = true;
            Ok(true)
        })
        .unwrap();

        assert!(!queried);
        assert!(!active);

        let mut default_uname = Config::default();
        default_uname.settings.uname = Some(model::Uname {
            release: "default".to_owned(),
            version: "default".to_owned(),
        });
        let active = uts_arbitration_active(&default_uname, || {
            queried = true;
            Ok(true)
        })
        .unwrap();

        assert!(!queried);
        assert!(!active);
    }

    #[test]
    fn only_applied_uname_blocks_the_uts_provider() {
        let saved = UnameProviderState::default();
        let scheduled = UnameProviderState {
            scheduled: true,
            ..UnameProviderState::default()
        };
        let applied = UnameProviderState {
            applied: true,
            scheduled: true,
            uncertain: false,
        };
        let uncertain = UnameProviderState {
            applied: false,
            scheduled: false,
            uncertain: true,
        };

        assert!(!saved.blocks_uts());
        assert!(!saved.needs_boot_resolution());
        assert!(!scheduled.blocks_uts());
        assert!(scheduled.needs_boot_resolution());
        assert!(uncertain.needs_boot_resolution());
        assert!(applied.blocks_uts());
        assert!(saved.known_not_applied());
        assert!(scheduled.known_not_applied());
        assert!(!uncertain.known_not_applied());
        assert!(!applied.known_not_applied());
    }

    #[test]
    fn restored_default_uname_is_not_a_provider() {
        let mut config = Config {
            automatic: true,
            ..Config::default()
        };
        config.settings.uname = Some(model::Uname {
            release: "default".to_owned(),
            version: "default".to_owned(),
        });
        let mut state = store::State::default();
        state.applied.insert(
            "uname".to_owned(),
            serde_json::to_string(config.settings.uname.as_ref().unwrap()).unwrap(),
        );

        let provider = uname_provider(&config, &state);

        assert!(!provider.applied);
        assert!(!provider.scheduled);
        assert!(!provider.blocks_uts());
    }

    #[test]
    fn uts_conflict_defers_only_uname() {
        let mut config = Config {
            automatic: true,
            ..Config::default()
        };
        config.settings.uname = Some(model::Uname {
            release: "6.6.0-spoof".to_owned(),
            version: "#1".to_owned(),
        });
        config.settings.hide_mounts = Some(true);

        let mut effective = config.clone();
        let defer_uname = uname_conflicts(&config, true);
        if defer_uname {
            effective.settings.uname = None;
        }

        assert!(defer_uname);
        assert_eq!(effective.settings.hide_mounts, Some(true));
        assert!(effective.settings.uname.is_none());
        assert!(config.settings.uname.is_some());
    }
}
