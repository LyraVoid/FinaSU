//! Host tests for the in-flight step guard and the bounded mount log.

use super::*;

#[test]
fn a_step_that_outlived_its_boot_is_reported_as_died() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("mount_inflight");
    let epoch = crate::startup_epoch::StartupEpoch {
        boot_id: current_boot_id(),
        generation: 3,
    };

    // Nothing recorded: every step is free to run.
    assert!(!died_at(&path, &epoch));
    assert!(read(&path).is_none());

    // A step recorded in this boot is in flight, not a failure.
    begin(
        &path,
        Stage::Inject,
        "nomount",
        "mode=nomount",
        &current_boot_id(),
        epoch.generation,
    )
    .unwrap();
    assert!(!died_at(&path, &epoch));
    let record = read(&path).unwrap();
    assert_eq!(record.stage, Stage::Inject);
    assert_eq!(record.provider, "nomount");
    assert_eq!(record.detail, "mode=nomount");
    assert!(
        record
            .describe()
            .contains("inject nomount (mode=nomount) in boot")
    );

    // A step that came back clears its own record.
    clear(&path);
    assert!(!died_at(&path, &epoch));

    // A record that survived its boot means the step never finished.
    std::fs::write(
        &path,
        "boot_id=00000000-0000-0000-0000-000000000000\nstage=load\nprovider=nomount\n\
         detail=android12-5.10\ntime=7\n",
    )
    .unwrap();
    assert!(died_at(&path, &epoch));
    assert_eq!(read(&path).unwrap().provider, "nomount");

    // Junk cannot be told apart from a step that is still running, so it must
    // not disable a provider.
    std::fs::write(&path, "provider=nomount\n").unwrap();
    assert!(!died_at(&path, &epoch));
    assert!(read(&path).is_none());
}

#[test]
fn a_soft_reboot_makes_an_unfinished_step_from_the_previous_cycle_stale() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("mount_inflight");
    let boot_id = current_boot_id();
    begin(&path, Stage::Inject, "nomount", "", &boot_id, 4).unwrap();
    assert!(!died_at(
        &path,
        &crate::startup_epoch::StartupEpoch {
            boot_id: boot_id.clone(),
            generation: 4,
        }
    ));
    assert!(died_at(
        &path,
        &crate::startup_epoch::StartupEpoch {
            boot_id,
            generation: 5,
        }
    ));
}

#[test]
fn a_stale_record_survives_boot_completed_and_our_own_does_not() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("mount_inflight");
    let epoch = crate::startup_epoch::StartupEpoch {
        boot_id: current_boot_id(),
        generation: 3,
    };

    // Safe mode or a different provider ran: the record from the earlier boot
    // is the evidence that a provider has to be skipped, so it stays.
    std::fs::write(
        &path,
        "boot_id=00000000-0000-0000-0000-000000000000\nstage=inject\nprovider=metamodule\n\
         detail=\ntime=7\n",
    )
    .unwrap();
    clear_if_ours_at(&path, &epoch).unwrap();
    assert!(died_at(&path, &epoch), "a stale record must survive");

    // This boot's own record is dropped: the step it describes just finished.
    clear(&path);
    begin(
        &path,
        Stage::Inject,
        "builtin",
        "",
        &current_boot_id(),
        epoch.generation,
    )
    .unwrap();
    clear_if_ours_at(&path, &epoch).unwrap();
    assert!(read(&path).is_none());
}

#[test]
fn the_mount_log_rotates_instead_of_growing() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("mount.log");
    let rotated = dir.path().join("mount.old");

    append_log(&path, "first block\n", 48).unwrap();
    append_log(&path, "second block\n", 48).unwrap();
    assert_eq!(
        std::fs::read_to_string(&path).unwrap(),
        "first block\nsecond block\n"
    );
    assert!(!rotated.exists());

    // Crossing the limit moves the current file aside and starts a new one, so
    // what a bug report carries stays bounded no matter how long the device ran.
    append_log(&path, "third block that pushes past the limit\n", 48).unwrap();
    assert_eq!(
        std::fs::read_to_string(&path).unwrap(),
        "third block that pushes past the limit\n"
    );
    assert_eq!(
        std::fs::read_to_string(&rotated).unwrap(),
        "first block\nsecond block\n"
    );
}

#[test]
fn the_capture_flag_defaults_and_stays_bounded() {
    // Touching the flag by hand is the documented way to arm the capture, so a
    // file without a delay still arms it with the default.
    assert_eq!(parse_capture_delay(""), CAPTURE_DELAY_SECS);
    assert_eq!(parse_capture_delay("arm me\n"), CAPTURE_DELAY_SECS);
    assert_eq!(parse_capture_delay("delay=60\n"), 60);

    // Junk and absurd values must not park a helper forever.
    assert_eq!(parse_capture_delay("delay=soon\n"), CAPTURE_DELAY_SECS);
    assert_eq!(
        parse_capture_delay(&format!("delay={}\n", CAPTURE_MAX_DELAY_SECS * 10)),
        CAPTURE_MAX_DELAY_SECS
    );
}
