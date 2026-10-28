use super::Config;
use anyhow::{Context, Result, bail};
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    os::{
        fd::AsRawFd,
        unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt},
    },
    path::Path,
};

const DIR: &str = "/data/adb/ksu/folksu-susfs";
const CONFIG: &str = "/data/adb/ksu/folksu-susfs/config.json";
const STATE: &str = "/data/adb/ksu/folksu-susfs/state.json";

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct State {
    pub boot_id: String,
    pub applied: BTreeMap<String, String>,
    #[serde(default)]
    pub uncertain: BTreeMap<String, String>,
    pub kstat_pending: Vec<String>,
    #[serde(default)]
    pub snapshots: BTreeMap<String, String>,
    #[serde(default)]
    pub pending_reboot: Vec<String>,
    pub failures: BTreeMap<String, String>,
    #[serde(default)]
    pub skipped: BTreeMap<String, SkippedControl>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SkippedControl {
    pub value: String,
    pub reason: String,
}

pub fn read_config() -> Result<Config> {
    match read(CONFIG, super::model::MAX_CONFIG) {
        Ok(input) => Config::parse(&input),
        Err(error)
            if error
                .downcast_ref::<std::io::Error>()
                .is_some_and(|e| e.kind() == std::io::ErrorKind::NotFound) =>
        {
            Ok(Config::default())
        }
        Err(error) => Err(error),
    }
}

fn read(path: &str, max: usize) -> Result<String> {
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW | libc::O_NONBLOCK)
        .open(path)?;
    let md = file.metadata()?;
    if !md.is_file() || md.uid() != 0 || md.mode() & 0o077 != 0 {
        bail!("Unsafe SUSFS file permissions");
    }
    let mut input = String::new();
    file.take((max + 1) as u64).read_to_string(&mut input)?;
    if input.len() > max {
        bail!("SUSFS file exceeds size limit");
    }
    Ok(input)
}

pub fn read_state() -> Result<State> {
    let boot_id = fs::read_to_string("/proc/sys/kernel/random/boot_id")?
        .trim()
        .to_owned();
    let state = match read(STATE, 256 * 1024) {
        Ok(input) => serde_json::from_str::<State>(&input)?,
        Err(error)
            if error
                .downcast_ref::<std::io::Error>()
                .is_some_and(|e| e.kind() == std::io::ErrorKind::NotFound) =>
        {
            State::default()
        }
        Err(error) => return Err(error),
    };
    Ok(if state.boot_id == boot_id {
        state
    } else {
        State {
            boot_id,
            ..State::default()
        }
    })
}

fn ensure_dir() -> Result<()> {
    match fs::symlink_metadata(DIR) {
        Ok(md) => {
            if !md.is_dir() || md.uid() != 0 || md.mode() & 0o077 != 0 {
                bail!("Unsafe SUSFS config directory");
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            std::os::unix::fs::DirBuilderExt::mode(&mut fs::DirBuilder::new(), 0o700)
                .create(DIR)?;
        }
        Err(error) => return Err(error.into()),
    }
    Ok(())
}

pub struct Lock(File);
impl Drop for Lock {
    fn drop(&mut self) {
        unsafe {
            libc::flock(self.0.as_raw_fd(), libc::LOCK_UN);
        }
    }
}

pub fn lock() -> Result<Lock> {
    ensure_dir()?;
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(format!("{DIR}/lock"))?;
    let md = file.metadata()?;
    if !md.is_file() || md.uid() != 0 || md.mode() & 0o077 != 0 {
        bail!("Unsafe SUSFS lock");
    }
    // No unbounded waits during boot or interactive operations.
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        bail!("SUSFS management is busy; retry later");
    }
    Ok(Lock(file))
}

fn atomic(path: &str, contents: &[u8]) -> Result<()> {
    ensure_dir()?;
    let mut temp = tempfile::NamedTempFile::new_in(DIR)?;
    temp.as_file()
        .set_permissions(fs::Permissions::from_mode(0o600))?;
    temp.write_all(contents)?;
    temp.as_file().sync_all()?;
    temp.persist(Path::new(path))
        .context("Persist SUSFS configuration")?;
    File::open(DIR)?.sync_all()?;
    Ok(())
}

pub fn write_config(config: &Config) -> Result<()> {
    atomic(CONFIG, &serde_json::to_vec_pretty(config)?)
}
pub fn write_state(state: &State) -> Result<()> {
    atomic(STATE, &serde_json::to_vec(state)?)
}

pub fn summary(config: &Config) -> Result<&'static str> {
    summary_with_deferred(config, false)
}

pub fn summary_with_deferred(config: &Config, deferred: bool) -> Result<&'static str> {
    let state = read_state()?;
    let desired = super::apply::operations(config)?;
    Ok(summarize(&state, &desired, deferred))
}

pub fn summarize(
    state: &State,
    desired: &BTreeMap<String, String>,
    deferred: bool,
) -> &'static str {
    let failed = state.uncertain.keys().any(|key| desired.contains_key(key))
        || state.failures.keys().any(|key| desired.contains_key(key));
    let applied = desired
        .iter()
        .filter(|(key, value)| {
            !state.failures.contains_key(*key)
                && !state.uncertain.contains_key(*key)
                && state.applied.get(*key) == Some(*value)
        })
        .count();
    if failed {
        if applied > 0 { "partial" } else { "failed" }
    } else if !state.kstat_pending.is_empty()
        || state.pending_reboot.iter().any(|k| desired.contains_key(k))
        || state.applied.iter().any(|(k, v)| {
            !desired.contains_key(k) || (k.contains(':') && desired.get(k) != Some(v))
        })
    {
        "pending_reboot"
    } else if deferred && desired.contains_key("uname") {
        "deferred"
    } else if !desired.is_empty()
        && desired.iter().all(|(key, value)| {
            state.applied.get(key) == Some(value)
                || state
                    .skipped
                    .get(key)
                    .is_some_and(|skipped| skipped.value == *value)
        })
    {
        if applied == 0 { "skipped" } else { "applied" }
    } else if desired.is_empty() {
        "idle"
    } else {
        "saved"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn status_never_claims_saved_is_applied() {
        let mut state = State::default();
        let desired = BTreeMap::from([("path:/x".to_owned(), "hash".to_owned())]);
        assert_eq!(summarize(&state, &desired, false), "saved");
        state.applied = desired.clone();
        assert_eq!(summarize(&state, &desired, false), "applied");
        assert_eq!(summarize(&state, &BTreeMap::new(), false), "pending_reboot");
        let changed = BTreeMap::from([("path:/x".to_owned(), "changed".to_owned())]);
        assert_eq!(summarize(&state, &changed, false), "pending_reboot");
        state
            .failures
            .insert("path:/x".to_owned(), "126".to_owned());
        assert_eq!(summarize(&state, &desired, false), "failed");
        state.failures.clear();
        state.applied.clear();
        state.skipped.insert(
            "option".to_owned(),
            SkippedControl {
                value: "false".to_owned(),
                reason: "unsupported".to_owned(),
            },
        );
        let option = BTreeMap::from([("option".to_owned(), "false".to_owned())]);
        assert_eq!(summarize(&state, &option, false), "skipped");
        state.skipped.clear();
        state.applied = desired.clone();
        state.failures.insert("option".to_owned(), "126".to_owned());
        let mixed = BTreeMap::from([
            ("path:/x".to_owned(), "hash".to_owned()),
            ("option".to_owned(), "true".to_owned()),
        ]);
        assert_eq!(summarize(&state, &mixed, false), "partial");
        state.failures.clear();
        state
            .uncertain
            .insert("path:/x".to_owned(), "hash".to_owned());
        assert_eq!(summarize(&state, &desired, false), "failed");
        state.uncertain.clear();
        state.applied.clear();
        assert_eq!(summarize(&state, &BTreeMap::new(), false), "idle");
        state.kstat_pending.push("kstat:/old".to_owned());
        assert_eq!(summarize(&state, &BTreeMap::new(), false), "pending_reboot");
    }

    #[test]
    fn deferred_requires_a_matching_desired_operation() {
        let state = State::default();
        let uname = BTreeMap::from([("uname".to_owned(), "spoofed".to_owned())]);
        let path = BTreeMap::from([("path:/x".to_owned(), "hash".to_owned())]);

        assert_eq!(summarize(&state, &uname, true), "deferred");
        assert_eq!(summarize(&state, &path, true), "saved");
        assert_eq!(summarize(&state, &BTreeMap::new(), true), "idle");
    }

    #[test]
    fn write_ahead_uncertainty_round_trips() {
        let mut state = State {
            boot_id: "boot".to_owned(),
            ..State::default()
        };
        state
            .uncertain
            .insert("uname".to_owned(), "runtime".to_owned());
        state.skipped.insert(
            "avc_spoof".to_owned(),
            SkippedControl {
                value: "false".to_owned(),
                reason: "unsupported".to_owned(),
            },
        );

        let encoded = serde_json::to_string(&state).unwrap();
        let decoded: State = serde_json::from_str(&encoded).unwrap();

        assert_eq!(
            decoded.uncertain.get("uname").map(String::as_str),
            Some("runtime")
        );
        assert_eq!(
            decoded
                .skipped
                .get("avc_spoof")
                .map(|skipped| skipped.reason.as_str()),
            Some("unsupported")
        );
    }
}
