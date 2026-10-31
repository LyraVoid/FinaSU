use super::{
    Config, Detection, abi,
    model::Rule,
    store::{self, State},
};
use anyhow::{Context, Result, bail};
use serde::Serialize;
use std::{collections::BTreeMap, fs, io::Write, os::unix::fs::MetadataExt};

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Stage {
    Live,
    PreMount,
    PostMount,
    Deferred,
}

#[derive(Serialize)]
pub struct Report {
    pub status: &'static str,
    pub applied: Vec<String>,
    pub failures: BTreeMap<String, String>,
    pub skipped: BTreeMap<String, String>,
    pub deferred: Vec<String>,
    pub pending_reboot: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum Outcome {
    Applied,
    Deferred,
    Skipped(String),
}

pub fn operations(config: &Config) -> Result<BTreeMap<String, String>> {
    let mut ops = BTreeMap::new();
    for (key, value) in [
        ("hide_mounts", config.settings.hide_mounts),
        ("logging", config.settings.logging),
        ("avc_spoof", config.settings.avc_spoof),
    ] {
        if let Some(value) = value {
            ops.insert(key.to_owned(), value.to_string());
        }
    }
    if let Some(value) = &config.settings.uname {
        ops.insert("uname".to_owned(), serde_json::to_string(value)?);
    }
    if let Some(value) = &config.settings.cmdline {
        ops.insert("cmdline".to_owned(), value.clone());
    }
    for rule in &config.rules {
        let (kind, path) = match rule {
            Rule::Path { path, .. } => ("path", path),
            Rule::Map { path } => ("map", path),
            Rule::Redirect { path, .. } => ("redirect", path),
            Rule::Kstat { path, .. } => ("kstat", path),
            Rule::StaticKstat { path, .. } => ("static_kstat", path),
        };
        ops.insert(format!("{kind}:{path}"), serde_json::to_string(rule)?);
    }
    for name in &config.lkm.hidden_modules {
        ops.insert(format!("module:{name}"), name.clone());
    }
    for path in &config.lkm.mount_prefixes {
        ops.insert(format!("prefix:{path}"), path.clone());
    }
    Ok(ops)
}

fn require(detection: &Detection, feature: &str) -> Result<()> {
    if !detection.supports(feature) {
        bail!("Capability unavailable: {feature}");
    }
    Ok(())
}

fn proc_write(detection: &Detection, node: &str, command: &str) -> Result<()> {
    if !detection.proc(node) {
        bail!("LKM proc extension unavailable: {node}");
    }
    let mut file = fs::OpenOptions::new()
        .write(true)
        .open(format!("/proc/{node}"))?;
    // One write: proc handlers parse a complete command. Never shell-evaluate rule input.
    let bytes = format!("{command}\n");
    if file.write(bytes.as_bytes())? != bytes.len() {
        bail!("Short SUSFS proc write");
    }
    Ok(())
}

fn unavailable(enabled: bool, detail: String) -> Result<Outcome> {
    if enabled {
        bail!("{detail}");
    }
    Ok(Outcome::Skipped(detail))
}

fn toggle_command(command: u32, enabled: bool, unavailable_detail: &str) -> Result<Outcome> {
    let mut payload = abi::Toggle { enabled, err: 126 };
    abi::call(command, &mut payload);
    match payload.err {
        0 => Ok(Outcome::Applied),
        126 => unavailable(enabled, unavailable_detail.to_owned()),
        err => bail!("SUSFS returned error {err}"),
    }
}

fn toggle(detection: &Detection, capability: &str, command: u32, enabled: bool) -> Result<Outcome> {
    if !detection.supports(capability) {
        return unavailable(enabled, format!("Capability unavailable: {capability}"));
    }
    toggle_command(
        command,
        enabled,
        &format!("Capability unavailable: {capability}"),
    )
}

fn setting(config: &Config, detection: &Detection, key: &str) -> Result<Outcome> {
    match key {
        "hide_mounts" => toggle(
            detection,
            "CONFIG_KSU_SUSFS_SUS_MOUNT",
            0x0005_5561,
            config.settings.hide_mounts.unwrap_or(false),
        ),
        "logging" => toggle(
            detection,
            "CONFIG_KSU_SUSFS_ENABLE_LOG",
            0x0005_55a0,
            config.settings.logging.unwrap_or(false),
        ),
        "avc_spoof" => {
            // LKM dispatches AVC but does not advertise an upstream feature for it.
            if detection.implementation == "lkm" {
                let enabled = config.settings.avc_spoof == Some(true);
                if !detection.proc("susfs_avc_spoof") {
                    return unavailable(
                        enabled,
                        "LKM proc extension unavailable: susfs_avc_spoof".to_owned(),
                    );
                }
                proc_write(
                    detection,
                    "susfs_avc_spoof",
                    if enabled { "1" } else { "0" },
                )
                .map(|()| Outcome::Applied)
            } else {
                // This command has no stable upstream CONFIG_* advertisement.
                toggle_command(
                    0x0006_0010,
                    config.settings.avc_spoof.unwrap_or(false),
                    "SUSFS AVC log spoofing is unavailable",
                )
            }
        }
        "uname" => {
            require(detection, "CONFIG_KSU_SUSFS_SPOOF_UNAME")?;
            let value = config
                .settings
                .uname
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("Missing uname"))?;
            let mut payload = abi::Uname {
                release: abi::bytes(&value.release)?,
                version: abi::bytes(&value.version)?,
                err: 126,
            };
            abi::call(0x0005_5590, &mut payload);
            abi::checked(payload.err).map(|()| Outcome::Applied)
        }
        "cmdline" => {
            require(detection, "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG")?;
            let mut payload = abi::Text::<8192> {
                text: abi::bytes(config.settings.cmdline.as_deref().unwrap_or_default())?,
                err: 126,
            };
            abi::call(0x0005_55b0, &mut payload);
            abi::checked(payload.err).map(|()| Outcome::Applied)
        }
        _ if key.starts_with("module:") => proc_write(
            detection,
            "susfs_hide_modules",
            &format!("add {}", &key[7..]),
        )
        .map(|()| Outcome::Applied),
        _ if key.starts_with("prefix:") => proc_write(
            detection,
            "susfs_hide_mounts",
            &format!("add {}", &key[7..]),
        )
        .map(|()| Outcome::Applied),
        _ => bail!("Unknown SUSFS operation"),
    }
}

fn kstat(path: &str, full_clone: bool, detection: &Detection, update: bool) -> Result<()> {
    require(detection, "CONFIG_KSU_SUSFS_SUS_KSTAT")?;
    if update && full_clone && detection.implementation == "lkm" {
        return proc_write(
            detection,
            "susfs_kstat",
            &format!("update_sus_kstat_full_clone {path}"),
        );
    }
    if full_clone && detection.implementation == "lkm" && !detection.proc("susfs_kstat") {
        bail!("LKM full-clone requires the proc extension; supercall ignores flags");
    }
    let md = fs::metadata(path)?;
    let mut payload = abi::Kstat {
        path: abi::bytes(path)?,
        target_ino: md.ino() as libc::c_ulong,
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
        flags: if full_clone && update { 0x0fff } else { 0x0ff3 },
        ..abi::Kstat::default()
    };
    abi::call(if update { 0x0005_5571 } else { 0x0005_5570 }, &mut payload);
    abi::checked(payload.err)
}

fn rule(rule: &Rule, detection: &Detection) -> Result<()> {
    match rule {
        Rule::Path { path, .. } | Rule::Map { path } => {
            let (feature, command) = match rule {
                Rule::Path { looping: true, .. } => ("CONFIG_KSU_SUSFS_SUS_PATH", 0x0005_5553),
                Rule::Path { .. } => ("CONFIG_KSU_SUSFS_SUS_PATH", 0x0005_5550),
                _ => ("CONFIG_KSU_SUSFS_SUS_MAP", 0x0006_0020),
            };
            require(detection, feature)?;
            let mut payload = abi::Text::<256> {
                text: abi::bytes(path)?,
                err: 126,
            };
            abi::call(command, &mut payload);
            abi::checked(payload.err)
        }
        Rule::Redirect {
            path,
            destination,
            uid_scheme,
        } => {
            require(detection, "CONFIG_KSU_SUSFS_OPEN_REDIRECT")?;
            if detection.implementation == "lkm" && *uid_scheme > 2 {
                bail!("LKM UID schemes 3/4 are not equivalent to built-in SUSFS");
            }
            let mut payload = abi::Redirect {
                target: abi::bytes(path)?,
                destination: abi::bytes(destination)?,
                uid_scheme: *uid_scheme as i32,
                err: 126,
            };
            abi::call(0x0005_55c0, &mut payload);
            abi::checked(payload.err)
        }
        Rule::Kstat { .. } => bail!("Kstat requires paired pre/post mount stages"),
        Rule::StaticKstat { path, fields } => {
            if !abi::static_kstat_profile(detection) {
                bail!("Static kstat provider is not verified or its control is unavailable");
            }
            let table = fs::read_to_string("/proc/susfs_kstat")?;
            if !kstat_table_empty(&table) {
                bail!("Static kstat requires an empty rule table; existing rules were not changed");
            }
            let metadata = fs::metadata(path)?;
            let mut payload = abi::static_kstat_payload(path, fields, &metadata)?;
            abi::call(0x0005_5572, &mut payload);
            abi::checked(payload.err)
        }
    }
}

fn kstat_table_empty(table: &str) -> bool {
    table
        .lines()
        .next()
        .is_some_and(|line| line.trim() == "(empty)")
}

pub fn apply(config: &Config, detection: &Detection, phase: Stage) -> Result<Report> {
    store::read_ownership()?.require_builtin()?;
    config.validate()?;
    if super::safe_mode() {
        bail!("SUSFS apply is disabled in safe mode");
    }
    if detection.status != "compatible" {
        bail!("SUSFS is unavailable or protocol is read-only");
    }
    #[cfg(target_os = "android")]
    crate::utils::switch_mnt_ns(1).context("Enter PID 1 mount namespace")?;
    apply_with_state(
        config,
        phase,
        store::read_state().context("Read SUSFS state")?,
        store::write_state,
        |key, value, state, report| {
            if key.starts_with("kstat:") {
                let parsed: Rule = serde_json::from_str(value)?;
                let Rule::Kstat { path, full_clone } = parsed else {
                    bail!("Invalid kstat rule");
                };
                apply_kstat(key, &path, full_clone, detection, phase, state, report).map(
                    |applied| {
                        if applied {
                            Outcome::Applied
                        } else {
                            Outcome::Deferred
                        }
                    },
                )
            } else if key.starts_with("path:")
                || key.starts_with("map:")
                || key.starts_with("redirect:")
                || key.starts_with("static_kstat:")
            {
                rule(&serde_json::from_str(value)?, detection).map(|()| Outcome::Applied)
            } else {
                setting(config, detection, key)
            }
        },
    )
}

fn apply_with_state(
    config: &Config,
    phase: Stage,
    mut state: State,
    mut persist: impl FnMut(&State) -> Result<()>,
    mut execute: impl FnMut(&str, &str, &mut State, &mut Report) -> Result<Outcome>,
) -> Result<Report> {
    let desired = operations(config)?;
    let mut report = Report {
        status: "saved",
        applied: Vec::new(),
        failures: BTreeMap::new(),
        skipped: BTreeMap::new(),
        deferred: Vec::new(),
        pending_reboot: Vec::new(),
    };
    state.failures.retain(|key, _| desired.contains_key(key));
    state
        .skipped
        .retain(|key, skipped| desired.get(key) == Some(&skipped.value));
    for key in state
        .applied
        .keys()
        .filter(|key| !desired.contains_key(*key))
    {
        report.pending_reboot.push(key.clone());
    }
    for (key, value) in &desired {
        let other_kstat = key
            .strip_prefix("kstat:")
            .map(|path| format!("static_kstat:{path}"))
            .or_else(|| {
                key.strip_prefix("static_kstat:")
                    .map(|path| format!("kstat:{path}"))
            });
        if other_kstat.as_ref().is_some_and(|other| {
            state.applied.contains_key(other)
                || state.snapshots.contains_key(other)
                || state.uncertain.contains_key(other)
                || state.kstat_pending.contains(other)
        }) {
            report.pending_reboot.push(key.clone());
            continue;
        }
        if state.applied.get(key) == Some(value) {
            continue;
        }
        if state.applied.contains_key(key) && key.contains(':') {
            report.pending_reboot.push(key.clone());
            continue;
        }
        if state
            .snapshots
            .get(key)
            .is_some_and(|snapshot| snapshot != value)
        {
            report.pending_reboot.push(key.clone());
            continue;
        }
        if state
            .skipped
            .get(key)
            .is_some_and(|skipped| skipped.value == *value)
        {
            continue;
        }
        if !key.starts_with("kstat:") && (phase == Stage::PreMount || phase == Stage::PostMount) {
            continue;
        }
        state.uncertain.insert(key.clone(), value.clone());
        if let Err(error) = persist(&state) {
            state.uncertain.remove(key);
            return Err(error).context("Persist SUSFS write-ahead state");
        }
        let result = execute(key, value, &mut state, &mut report);
        match result {
            Ok(Outcome::Applied) => {
                state.uncertain.remove(key);
                state.applied.insert(key.clone(), value.clone());
                state.failures.remove(key);
                state.skipped.remove(key);
                report.applied.push(key.clone());
            }
            Ok(Outcome::Deferred) => {
                state.uncertain.remove(key);
            }
            Ok(Outcome::Skipped(reason)) => {
                state.uncertain.remove(key);
                state.failures.remove(key);
                state.skipped.insert(
                    key.clone(),
                    store::SkippedControl {
                        value: value.clone(),
                        reason,
                    },
                );
            }
            Err(error) => {
                state.uncertain.remove(key);
                state.skipped.remove(key);
                state.failures.insert(key.clone(), error.to_string());
            }
        }
        // Persist after each successful mutation: replay does not silently duplicate rules.
        persist(&state).context("Persist SUSFS operation result")?;
    }
    report.failures.clone_from(&state.failures);
    report.skipped = state
        .skipped
        .iter()
        .map(|(key, skipped)| (key.clone(), skipped.reason.clone()))
        .collect();
    state.pending_reboot.clone_from(&report.pending_reboot);
    report.status = if !report.failures.is_empty() {
        if desired.iter().any(|(key, value)| {
            !state.failures.contains_key(key)
                && !state.uncertain.contains_key(key)
                && state.applied.get(key) == Some(value)
        }) {
            "partial"
        } else {
            "failed"
        }
    } else if !report.pending_reboot.is_empty() {
        "pending_reboot"
    } else {
        store::summarize(&state, &desired, false)
    };
    persist(&state).context("Persist SUSFS summary")?;
    Ok(report)
}

fn apply_kstat(
    key: &str,
    path: &str,
    full_clone: bool,
    detection: &Detection,
    phase: Stage,
    state: &mut State,
    report: &mut Report,
) -> Result<bool> {
    #[cfg(target_os = "android")]
    let late = crate::ksucalls::is_late_load();
    #[cfg(not(target_os = "android"))]
    let late = false;
    let captured = state.kstat_pending.iter().any(|k| k == key);
    if kstat_phase(phase, captured, late) == Some(false) {
        kstat(path, full_clone, detection, false)?;
        state.snapshots.insert(
            key.to_owned(),
            serde_json::to_string(&Rule::Kstat {
                path: path.to_owned(),
                full_clone,
            })?,
        );
        state.kstat_pending.push(key.to_owned());
        Ok(false)
    } else if kstat_phase(phase, captured, late) == Some(true) {
        kstat(path, full_clone, detection, true)?;
        state.kstat_pending.retain(|k| k != key);
        Ok(true)
    } else {
        report.pending_reboot.push(key.to_owned());
        Ok(false)
    }
}

// Some(false): capture original; Some(true): retarget; None: cannot safely apply.
const fn kstat_phase(phase: Stage, captured: bool, late: bool) -> Option<bool> {
    match (phase, captured, late) {
        (Stage::PreMount, false, false) => Some(false),
        (Stage::PostMount, true, false) => Some(true),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;

    #[test]
    fn static_kstat_only_accepts_an_explicitly_empty_rule_table() {
        assert!(kstat_table_empty("(empty)\nmaps: armed=0\n"));
        assert!(!kstat_table_empty(""));
        assert!(!kstat_table_empty("/data/test ino=1 dev=1 flags=0x8\n"));
        assert!(!kstat_table_empty("unexpected output"));
    }

    #[test]
    fn changing_kstat_kind_requires_reboot_before_writing() {
        let mut config = Config {
            schema: 2,
            ..Config::default()
        };
        config.rules.push(Rule::StaticKstat {
            path: "/data/test".to_owned(),
            fields: super::super::model::StaticKstat {
                size: Some(1),
                ..Default::default()
            },
        });
        let mut state = State::default();
        state
            .snapshots
            .insert("kstat:/data/test".to_owned(), "old".to_owned());
        let report = apply_with_state(
            &config,
            Stage::Live,
            state,
            |_| Ok(()),
            |_, _, _, _| panic!("must not replace an existing kstat rule"),
        )
        .unwrap();
        assert_eq!(report.status, "pending_reboot");
        assert_eq!(report.pending_reboot, ["static_kstat:/data/test"]);
    }

    #[test]
    fn boot_stages_only_mark_attempted_operations_uncertain_and_retry_failures() {
        let mut config = Config::default();
        config.settings.logging = Some(true);
        config.rules.push(Rule::Kstat {
            path: "/data/test".to_owned(),
            full_clone: false,
        });
        let persisted = RefCell::new(State::default());
        let snapshots = RefCell::new(Vec::new());
        let mut attempts = Vec::new();
        for phase in [
            Stage::PreMount,
            Stage::PostMount,
            Stage::PostMount,
            Stage::Deferred,
            Stage::Deferred,
        ] {
            let state = persisted.borrow().clone();
            let report = apply_with_state(
                &config,
                phase,
                state,
                |state| {
                    snapshots.borrow_mut().push(state.clone());
                    *persisted.borrow_mut() = state.clone();
                    Ok(())
                },
                |key, value, state, _| {
                    assert_eq!(
                        persisted.borrow().uncertain.get(key),
                        Some(&value.to_owned())
                    );
                    attempts.push((phase, key.to_owned()));
                    if key.starts_with("kstat:") {
                        if phase == Stage::PreMount {
                            state.kstat_pending.push(key.to_owned());
                            state.snapshots.insert(key.to_owned(), value.to_owned());
                            Ok(Outcome::Deferred)
                        } else {
                            state.kstat_pending.retain(|pending| pending != key);
                            Ok(Outcome::Applied)
                        }
                    } else if attempts.iter().filter(|(_, key)| key == "logging").count() == 1 {
                        bail!("temporary error")
                    } else {
                        Ok(Outcome::Applied)
                    }
                },
            )
            .unwrap();
            if phase == Stage::PreMount || phase == Stage::PostMount {
                assert!(!persisted.borrow().uncertain.contains_key("logging"));
                assert_ne!(report.status, "failed");
            } else if attempts.iter().filter(|(_, key)| key == "logging").count() == 1 {
                assert!(report.failures.contains_key("logging"));
            } else {
                assert!(report.failures.is_empty());
            }
        }
        assert_eq!(
            attempts
                .iter()
                .filter(|(_, key)| key.starts_with("kstat:"))
                .count(),
            2
        );
        assert_eq!(
            attempts.iter().filter(|(_, key)| key == "logging").count(),
            2
        );
        let state = persisted.borrow();
        assert!(state.failures.is_empty());
        assert!(state.uncertain.is_empty());
        assert_eq!(
            store::summarize(&state, &operations(&config).unwrap(), false),
            "applied"
        );
        assert!(
            snapshots
                .borrow()
                .iter()
                .any(|state| state.uncertain.contains_key("logging"))
        );
    }

    #[test]
    fn failed_write_ahead_persistence_prevents_execution() {
        let mut config = Config::default();
        config.settings.logging = Some(true);
        let mut executed = false;
        let result = apply_with_state(
            &config,
            Stage::Deferred,
            State::default(),
            |_| bail!("storage unavailable"),
            |_, _, _, _| {
                executed = true;
                Ok(Outcome::Applied)
            },
        );
        assert!(result.is_err());
        assert!(!executed);
    }

    #[test]
    fn deferred_stage_does_not_erase_existing_uncertainty() {
        let mut config = Config::default();
        config.settings.logging = Some(true);
        let mut state = State::default();
        state
            .uncertain
            .insert("logging".to_owned(), "true".to_owned());
        let saved = RefCell::new(State::default());
        let report = apply_with_state(
            &config,
            Stage::PreMount,
            state,
            |state| {
                *saved.borrow_mut() = state.clone();
                Ok(())
            },
            |_, _, _, _| panic!("deferred operation must not run"),
        )
        .unwrap();
        assert_eq!(report.status, "failed");
        assert!(saved.borrow().uncertain.contains_key("logging"));
    }

    #[test]
    fn dynamic_kstat_requires_the_real_pre_post_pair() {
        assert_eq!(kstat_phase(Stage::PreMount, false, false), Some(false));
        assert_eq!(kstat_phase(Stage::PostMount, true, false), Some(true));
        assert_eq!(kstat_phase(Stage::PostMount, false, false), None);
        assert_eq!(kstat_phase(Stage::PreMount, true, false), None);
        for phase in [Stage::Live, Stage::Deferred] {
            assert_eq!(kstat_phase(phase, false, false), None);
            assert_eq!(kstat_phase(phase, true, false), None);
        }
        for phase in [
            Stage::PreMount,
            Stage::PostMount,
            Stage::Deferred,
            Stage::Live,
        ] {
            assert_eq!(kstat_phase(phase, false, true), None);
            assert_eq!(kstat_phase(phase, true, true), None);
        }
    }

    #[test]
    fn extension_controls_need_the_explicit_marker_and_proc_node() {
        let mut detection = Detection {
            status: "compatible",
            version: "v2.3.0".to_owned(),
            variant: "GKI".to_owned(),
            implementation: "builtin",
            features: vec!["CONFIG_KSU_SUSFS_SUS_KSTAT".to_owned()],
            proc_nodes: vec!["susfs_kstat".to_owned()],
        };
        assert!(!detection.proc("susfs_kstat"));
        detection.implementation = "lkm";
        assert!(detection.proc("susfs_kstat"));
        detection.proc_nodes.clear();
        assert!(!detection.proc("susfs_kstat"));
        detection.status = "unknown_protocol";
        assert!(!detection.supports("CONFIG_KSU_SUSFS_SUS_KSTAT"));
    }

    #[test]
    fn disabling_an_unavailable_control_is_skipped_not_failed() {
        assert_eq!(
            unavailable(false, "Capability unavailable".to_owned()).unwrap(),
            Outcome::Skipped("Capability unavailable".to_owned())
        );
        assert!(unavailable(true, "Capability unavailable".to_owned()).is_err());
    }
}
