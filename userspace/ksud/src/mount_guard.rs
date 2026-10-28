//! Durable record of the last risky module-mount step a boot performed.
//!
//! Two steps can stop a boot that no userspace code gets to observe afterwards:
//! loading an embedded kernel module (`module_init` can wedge the kernel, and
//! then the loader call simply never returns) and handing the module tree to a
//! provider (a bad injection can break path resolution before the boot reaches
//! `boot_completed`). Neither leaves a trace the next boot could read, so the
//! step is written down *before* it runs and removed once it is known to have
//! finished. Finding an earlier boot's record means that step never finished,
//! and the next boot must not repeat it.

use std::io::Write as _;
use std::path::Path;

use anyhow::{Context, Result};

/// Which risky step a record was written for.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Stage {
    /// The embedded NoMount module is being loaded into the kernel.
    Load,
    /// A provider is injecting the module tree.
    Inject,
}

/// How long a capture waits for the boot to finish before it dumps.
///
/// The kernel reports a task blocked for 120s on its own, so the default sits
/// just past that: whatever the kernel noticed is already in the log.
pub const CAPTURE_DELAY_SECS: u64 = 150;

/// Upper bound for a requested delay, so a typo cannot park a helper for hours.
pub const CAPTURE_MAX_DELAY_SECS: u64 = 900;

/// Read the capture delay out of a flag file.
///
/// A file without a `delay=` line still arms the capture; that is the point of
/// touching the flag by hand, so the caller only has to know whether it exists.
#[must_use]
pub fn parse_capture_delay(content: &str) -> u64 {
    content
        .lines()
        .filter_map(|line| line.split_once('='))
        .filter(|(key, _)| *key == "delay")
        .filter_map(|(_, value)| value.trim().parse::<u64>().ok())
        .next_back()
        .unwrap_or(CAPTURE_DELAY_SECS)
        .min(CAPTURE_MAX_DELAY_SECS)
}

impl Stage {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Load => "load",
            Self::Inject => "inject",
        }
    }

    #[must_use]
    pub fn parse(value: &str) -> Option<Self> {
        match value.trim() {
            "load" => Some(Self::Load),
            "inject" => Some(Self::Inject),
            _ => None,
        }
    }
}

/// One recorded step, read back by the next boot.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Inflight {
    pub boot_id: String,
    /// Android userspace startup generation within the kernel boot.
    pub generation: u64,
    pub stage: Stage,
    /// Provider the step belonged to (`builtin`, `metamodule`, `nomount`).
    pub provider: String,
    /// What the step was about: a KMI, a module id or a mount mode.
    pub detail: String,
}

impl Inflight {
    /// One line for the boot log, so a skipped provider can say what it skipped.
    #[must_use]
    pub fn describe(&self) -> String {
        let detail = if self.detail.is_empty() {
            String::new()
        } else {
            format!(" ({})", self.detail)
        };
        format!(
            "{} {}{detail} in boot {} generation {}",
            self.stage.as_str(),
            self.provider,
            self.boot_id,
            self.generation,
        )
    }
}

/// Current boot id; empty when it cannot be read.
#[must_use]
pub fn current_boot_id() -> String {
    std::fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .map_or_else(|_| String::new(), |value| value.trim().to_string())
}

/// Record a step *before* it runs, durably.
///
/// The file is fsynced: a step that wedges the kernel never returns and never
/// gets to fix anything up, so this record is the only trace the next boot can
/// read.
///
/// # Errors
/// Returns an error when the record cannot be written or synced.
pub fn begin(
    path: &Path,
    stage: Stage,
    provider: &str,
    detail: &str,
    boot_id: &str,
    generation: u64,
) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
    }
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |since| since.as_secs());
    let mut file =
        std::fs::File::create(path).with_context(|| format!("create {}", path.display()))?;
    file.write_all(
        format!(
            "boot_id={boot_id}\ngeneration={generation}\nstage={}\nprovider={}\ndetail={}\ntime={stamp}\n",
            stage.as_str(),
            sanitize(provider),
            sanitize(detail),
        )
        .as_bytes(),
    )
    .with_context(|| format!("write {}", path.display()))?;
    file.sync_all()
        .with_context(|| format!("sync {}", path.display()))
}

/// The recorded step, when there is one.
#[must_use]
pub fn read(path: &Path) -> Option<Inflight> {
    let content = std::fs::read_to_string(path).ok()?;
    let mut boot_id = None;
    let mut generation = None;
    let mut stage = None;
    let mut provider = None;
    let mut detail = None;
    for line in content.lines() {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        match key {
            "boot_id" => boot_id = Some(value.to_string()),
            "generation" => generation = value.parse().ok(),
            "stage" => stage = Stage::parse(value),
            "provider" => provider = Some(value.to_string()),
            "detail" => detail = Some(value.to_string()),
            _ => {}
        }
    }
    Some(Inflight {
        boot_id: boot_id?,
        generation: generation.unwrap_or(0),
        stage: stage?,
        provider: provider?,
        detail: detail.unwrap_or_default(),
    })
}

/// True when a record was written in an earlier boot.
///
/// That step never finished: the kernel wedged inside it, or the device was
/// reset before it completed. Repeating it has to be a deliberate choice rather
/// than an automatic part of the next boot.
#[must_use]
pub fn died(path: &Path) -> bool {
    let Ok(epoch) = crate::startup_epoch::current_or_create() else {
        return false;
    };
    died_at(path, &epoch)
}

fn died_at(path: &Path, epoch: &crate::startup_epoch::StartupEpoch) -> bool {
    read(path).is_some_and(|record| {
        record.boot_id != epoch.boot_id
            || (record.generation != 0 && record.generation != epoch.generation)
    })
}

/// Drop the record; failures only warn.
pub fn clear(path: &Path) {
    if let Err(error) = try_clear(path) {
        log::warn!("mount guard: {error:#}");
    }
}

pub fn try_clear(path: &Path) -> Result<()> {
    remove(path, "")
}

/// Drop the record only when it was written in this boot, i.e. when the step it
/// describes is the one that just completed.
///
/// A stale record has to survive: it is what tells the next boot not to repeat
/// a step that stopped an earlier one (booting into safe mode, for instance,
/// skips mounting and must not swallow the evidence).
pub fn clear_if_ours(path: &Path) -> Result<()> {
    let epoch = crate::startup_epoch::current_or_create()?;
    clear_if_ours_at(path, &epoch)
}

pub fn clear_if_ours_at(path: &Path, epoch: &crate::startup_epoch::StartupEpoch) -> Result<()> {
    let ours = read(path).is_none_or(|record| {
        record.boot_id == epoch.boot_id
            && (record.generation == 0 || record.generation == epoch.generation)
    });
    if ours {
        try_clear(path)?;
    }
    Ok(())
}

fn remove(path: &Path, note: &str) -> Result<()> {
    match std::fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => {
            Err(error).with_context(|| format!("could not clear {}{note}", path.display()))
        }
    }
}

fn sanitize(value: &str) -> String {
    value.replace(['\n', '\r'], " ")
}

/// Append one block to a bounded boot log.
///
/// The log is written for crash reports, so it has to stay small: once it grows
/// past `max_bytes` the current file is rotated to `<path>.old`, which keeps the
/// packaged size predictable no matter how long a device runs.
///
/// # Errors
/// Returns an error when the block cannot be written.
pub fn append_log(path: &Path, block: &str, max_bytes: u64) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
    }
    let size = std::fs::metadata(path).map_or(0, |meta| meta.len());
    if size > 0 && size.saturating_add(block.len() as u64) > max_bytes {
        let rotated = path.with_extension("old");
        let _ = std::fs::remove_file(&rotated);
        std::fs::rename(path, &rotated)
            .with_context(|| format!("rotate {} to {}", path.display(), rotated.display()))?;
    }
    let mut file = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .with_context(|| format!("open {}", path.display()))?;
    file.write_all(block.as_bytes())
        .with_context(|| format!("append {}", path.display()))?;
    // Best effort: the log is a diagnostic, never a reason to fail a boot.
    let _ = file.sync_all();
    Ok(())
}

#[cfg(test)]
#[path = "mount_guard_tests.rs"]
mod tests;
