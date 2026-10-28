#[cfg(target_os = "android")]
use std::{path::Path, time::Duration};

#[cfg(target_os = "android")]
use crate::{defs, mount_guard, utils};

fn delay(content: &str) -> Option<u64> {
    if content.lines().any(|line| line.trim() == "off") {
        return None;
    }
    Some(
        content
            .lines()
            .filter_map(|line| line.strip_prefix("delay=")?.parse::<u64>().ok())
            .next_back()
            .unwrap_or(180)
            .clamp(30, 900),
    )
}

fn ready(boot: Option<&str>, surface: Option<&str>, animation: Option<&str>) -> bool {
    boot == Some("1") && surface == Some("running") && animation == Some("stopped")
}

/// What an earlier boot's UI observation means for this boot.
#[cfg(any(target_os = "android", test))]
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Previous {
    /// No marker, this boot's own marker, or a marker we cannot attribute.
    None,
    /// The observation never produced a verdict: the boot ended before the
    /// deadline, or the helper died. A fast reboot is not evidence of a UI
    /// failure, so NoMount may run; the caller drops the stale marker.
    Inconclusive(String),
    /// The observation ran and readiness was not confirmed.
    Failed(String),
}

#[cfg(target_os = "android")]
#[must_use]
pub fn previous(boot_id: &str) -> Previous {
    let generation = crate::startup_epoch::current_or_create().map_or(0, |epoch| epoch.generation);
    previous_at(Path::new(defs::MOUNT_UI_PENDING), boot_id, generation)
}

#[cfg(any(target_os = "android", test))]
fn previous_at(path: &std::path::Path, boot_id: &str, generation: u64) -> Previous {
    let Some(record) = crate::mount_guard::read(path) else {
        return Previous::None;
    };
    if (record.boot_id == boot_id && (record.generation == 0 || record.generation == generation))
        || record.provider != "nomount"
    {
        return Previous::None;
    }
    if record.detail == "ui-failed" {
        return Previous::Failed(format!(
            "NoMount UI readiness was not confirmed in boot {}; using builtin this boot (not proof of a NoMount fault)",
            record.boot_id
        ));
    }
    Previous::Inconclusive(format!(
        "UI observation from boot {} never completed (the boot ended before the deadline?); keeping NoMount",
        record.boot_id
    ))
}

/// Leave one line in the shared mount log without involving the boot warning.
#[cfg(target_os = "android")]
pub fn note(text: &str) {
    let _ = mount_guard::append_log(
        Path::new(defs::MOUNT_LOG),
        &format!("--- nomount UI\n{text}\n"),
        defs::MOUNT_LOG_MAX_BYTES,
    );
}

#[cfg(target_os = "android")]
pub fn clear() {
    mount_guard::clear(Path::new(defs::MOUNT_UI_PENDING));
}

#[cfg(target_os = "android")]
pub fn arm(boot_id: &str, generation: u64, lock_fd: std::os::fd::RawFd) {
    let content = std::fs::read_to_string(defs::MOUNT_UI_CONFIG).unwrap_or_default();
    let Some(seconds) = delay(&content) else {
        return;
    };
    if let Err(error) = mount_guard::begin(
        Path::new(defs::MOUNT_UI_PENDING),
        mount_guard::Stage::Inject,
        "nomount",
        "ui-pending",
        boot_id,
        generation,
    ) {
        log::warn!("nomount: cannot arm UI guard: {error:#}");
        return;
    }
    let _ = mount_guard::append_log(
        Path::new(defs::MOUNT_LOG),
        &format!("--- nomount UI boot={boot_id}\nobservation armed delay={seconds}s\n"),
        defs::MOUNT_LOG_MAX_BYTES,
    );
    if !utils::create_daemon_with(true, || {
        unsafe { libc::close(lock_fd) };
        Ok(())
    })
    .unwrap_or(false)
    {
        return;
    }
    std::thread::sleep(Duration::from_secs(seconds));
    let boot = utils::getprop("sys.boot_completed");
    let surface = utils::getprop("init.svc.surfaceflinger");
    let animation = utils::getprop("init.svc.bootanim");
    if mount_guard::read(Path::new(defs::MOUNT_UI_PENDING)).is_some_and(|record| {
        record.boot_id == boot_id && (record.generation == 0 || record.generation == generation)
    }) {
        let healthy = ready(boot.as_deref(), surface.as_deref(), animation.as_deref());
        let message = format!(
            "UI observation after {seconds}s: boot_completed={boot:?} surfaceflinger={surface:?} bootanim={animation:?}; {}",
            if healthy {
                "readiness signals observed (not a display health guarantee)"
            } else {
                "readiness unconfirmed; next boot uses builtin"
            }
        );
        let _ = mount_guard::append_log(
            Path::new(defs::MOUNT_LOG),
            &format!("--- nomount UI boot={boot_id}\n{message}\n"),
            defs::MOUNT_LOG_MAX_BYTES,
        );
        if healthy {
            clear();
        } else {
            // Record the verdict itself. A marker that still says `ui-pending`
            // means the observation never ran (fast reboot, killed helper),
            // which must not be read as a UI failure on the next boot.
            let _ = mount_guard::begin(
                Path::new(defs::MOUNT_UI_PENDING),
                mount_guard::Stage::Inject,
                "nomount",
                "ui-failed",
                boot_id,
                generation,
            );
            crate::magic_mount::record_ui_warning(boot_id, generation, &message);
        }
    }
    unsafe { libc::_exit(0) };
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deadline_is_bounded_and_can_be_disabled() {
        assert_eq!(delay(""), Some(180));
        assert_eq!(delay("delay=1"), Some(30));
        assert_eq!(delay("delay=9999"), Some(900));
        assert_eq!(delay("off"), None);
    }

    #[test]
    fn boot_completed_alone_is_not_ui_readiness() {
        assert!(!ready(Some("1"), Some("stopped"), Some("stopped")));
        assert!(!ready(Some("1"), Some("running"), Some("running")));
        assert!(!ready(None, Some("running"), Some("stopped")));
        assert!(ready(Some("1"), Some("running"), Some("stopped")));
    }

    #[test]
    fn a_pending_observation_is_inconclusive_not_a_failure() {
        let temp = tempfile::tempdir().unwrap();
        let pending = temp.path().join("ui");
        let inflight = temp.path().join("inflight");
        let boot_id = crate::mount_guard::current_boot_id();
        let epoch = crate::startup_epoch::StartupEpoch {
            boot_id: boot_id.clone(),
            generation: 1,
        };
        for path in [&pending, &inflight] {
            crate::mount_guard::begin(
                path,
                crate::mount_guard::Stage::Inject,
                "nomount",
                "ui-pending",
                &boot_id,
                1,
            )
            .unwrap();
        }
        crate::mount_guard::clear_if_ours_at(&inflight, &epoch).unwrap();
        assert!(!inflight.exists());
        // This boot's own marker is not a verdict about an earlier boot.
        assert_eq!(previous_at(&pending, &boot_id, 1), Previous::None);
        // A marker that still says `ui-pending` means the observation never ran
        // (the device rebooted before the deadline), so NoMount keeps its turn.
        let Previous::Inconclusive(note) = previous_at(&pending, "new", 2) else {
            panic!("a pending observation must not be read as a failure");
        };
        assert!(note.contains("keeping NoMount"));
        crate::mount_guard::clear(&pending);
        assert_eq!(previous_at(&pending, "new", 2), Previous::None);
    }

    #[test]
    fn a_completed_unconfirmed_observation_blocks_the_next_boot() {
        let temp = tempfile::tempdir().unwrap();
        let pending = temp.path().join("ui");
        let boot_id = crate::mount_guard::current_boot_id();
        crate::mount_guard::begin(
            &pending,
            crate::mount_guard::Stage::Inject,
            "nomount",
            "ui-failed",
            &boot_id,
            1,
        )
        .unwrap();
        // The same boot that wrote the verdict is not affected by it.
        assert_eq!(previous_at(&pending, &boot_id, 1), Previous::None);
        let Previous::Failed(note) = previous_at(&pending, "next-boot", 2) else {
            panic!("a recorded ui-failed verdict must block the next boot");
        };
        assert!(note.contains("builtin"));
    }
}
