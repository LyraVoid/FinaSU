//! Host tests for the NoMount transport and wire encoding.

use std::cell::RefCell;
use std::rc::Rc;

use super::*;

/// How the fake kernel behaves.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Mode {
    /// Answers everything, keeps what it is given.
    Ready,
    /// Refuses every command, as an unregistered key type does.
    Silent,
    /// Accepts rules but reports none back.
    ForgetsRules,
    /// Answers the version but refuses the listing.
    ListingRefused,
}

struct FakeState {
    rules: Vec<Rule>,
    uids: Vec<u32>,
    isolated: bool,
    commands: Vec<Command>,
    last_delete: Vec<u8>,
}

struct FakeKernel {
    mode: Mode,
    version: &'static str,
    state: RefCell<FakeState>,
}

impl FakeKernel {
    fn new(mode: Mode) -> Self {
        Self {
            mode,
            version: "22",
            state: RefCell::new(FakeState {
                rules: Vec::new(),
                uids: Vec::new(),
                isolated: false,
                commands: Vec::new(),
                last_delete: Vec::new(),
            }),
        }
    }

    fn with_version(mut self, version: &'static str) -> Self {
        self.version = version;
        self
    }

    fn commands(&self) -> Vec<Command> {
        self.state.borrow().commands.clone()
    }

    fn written_rules(&self) -> Vec<Rule> {
        self.state.borrow().rules.clone()
    }
}

fn decode_command(raw: u32) -> Command {
    match raw {
        1 => Command::GetVersion,
        2 => Command::AddRule,
        3 => Command::DelRule,
        4 => Command::AddUid,
        5 => Command::DelUid,
        7 => Command::ClearRules,
        9 => Command::GetList,
        10 => Command::GetUids,
        11 => Command::BlockIsolatedUids,
        12 => Command::GetIsolatedState,
        other => panic!("unexpected command {other}"),
    }
}

/// Encode one rule the way the kernel would report it.
///
/// The client encoder only produces redirects and whiteouts; the kernel also
/// reports the intermediate directories it created, which carry no target.
fn kernel_record(rule: &Rule) -> Vec<u8> {
    let flags = if rule.whiteout {
        WHITEOUT_FLAG
    } else if rule.virtual_dir {
        VIRTUAL_DIR_FLAG
    } else if rule.opaque {
        OPAQUE_FLAG
    } else {
        0
    };
    let real = rule.real_path.as_deref().unwrap_or_default();
    let mut out = Vec::new();
    out.extend_from_slice(&flags.to_le_bytes());
    out.extend_from_slice(&rule.uid.to_le_bytes());
    out.extend_from_slice(&rule.uid_end.to_le_bytes());
    out.extend_from_slice(&rule.deny_start.to_le_bytes());
    out.extend_from_slice(&rule.deny_end.to_le_bytes());
    out.extend_from_slice(
        &u16::try_from(rule.virtual_path.len())
            .expect("path fits")
            .to_le_bytes(),
    );
    out.extend_from_slice(
        &u16::try_from(real.len())
            .expect("target fits")
            .to_le_bytes(),
    );
    out.extend_from_slice(rule.virtual_path.as_bytes());
    out.extend_from_slice(real.as_bytes());
    out
}

impl Transport for Rc<FakeKernel> {
    fn send(&self, payload: &mut Payload) -> Result<()> {
        self.dispatch(payload)
    }
}

impl FakeKernel {
    fn dispatch(&self, payload: &mut Payload) -> Result<()> {
        let magic = payload.magic;
        assert_eq!(magic, MAGIC, "payload magic must be stamped");
        let command = decode_command(payload.cmd);
        self.state.borrow_mut().commands.push(command);

        if self.mode == Mode::Silent {
            payload.status = -1;
            let command = decode_command(payload.cmd);
            bail!("nomount: command {command:?} failed with -1");
        }

        match command {
            Command::GetVersion => {
                let bytes = self.version.as_bytes();
                payload.buffer[..bytes.len()].copy_from_slice(bytes);
                payload.data_size = u32::try_from(bytes.len())?;
            }
            Command::GetList => {
                if self.mode == Mode::ListingRefused {
                    payload.status = -1;
                    bail!("nomount: listing refused");
                }
                let rules: Vec<Rule> = if self.mode == Mode::ForgetsRules {
                    Vec::new()
                } else {
                    self.state.borrow().rules.clone()
                };
                let start = usize::try_from(payload.arg1)?;
                let mut cursor = 0_usize;
                for rule in rules.iter().skip(start) {
                    let record = kernel_record(rule);
                    if cursor + record.len() > BUFFER_SIZE {
                        break;
                    }
                    payload.buffer[cursor..cursor + record.len()].copy_from_slice(&record);
                    cursor += record.len();
                }
                payload.data_size = u32::try_from(cursor)?;
                payload.arg1 = u32::try_from(start + rules.len().saturating_sub(start))?;
            }
            Command::AddRule => {
                let len = usize::try_from(payload.data_size)?;
                let decoded = parse_list_records(&payload.buffer[..len])?;
                let mut state = self.state.borrow_mut();
                // Mirror the kernel: a rule for the same virtual path and uid
                // replaces the previous one instead of stacking.
                for rule in decoded {
                    state.rules.retain(|existing| {
                        existing.virtual_path != rule.virtual_path
                            || existing.uid != rule.uid
                            || existing.uid_end != rule.uid_end
                            || existing.deny_start != rule.deny_start
                            || existing.deny_end != rule.deny_end
                    });
                    state.rules.push(rule);
                }
            }
            Command::DelRule => {
                let len = usize::try_from(payload.data_size)?;
                self.state.borrow_mut().last_delete = payload.buffer[..len].to_vec();
                let mut cursor = 0_usize;
                let mut doomed = Vec::new();
                while cursor + DELETE_HEADER_SIZE <= len {
                    let v_len = usize::from(u16::from_le_bytes([
                        payload.buffer[cursor + 16],
                        payload.buffer[cursor + 17],
                    ]));
                    doomed.push(
                        String::from_utf8_lossy(&payload.buffer[cursor + 18..cursor + 18 + v_len])
                            .to_string(),
                    );
                    cursor += DELETE_HEADER_SIZE + v_len;
                }
                self.state
                    .borrow_mut()
                    .rules
                    .retain(|rule| !doomed.contains(&rule.virtual_path));
            }
            Command::AddUid => self.state.borrow_mut().uids.push(payload.target_uid),
            Command::DelUid => self
                .state
                .borrow_mut()
                .uids
                .retain(|uid| *uid != payload.target_uid),
            Command::ClearRules => self.state.borrow_mut().rules.clear(),
            Command::GetUids => {
                let start = usize::try_from(payload.arg1)?;
                let mut cursor = 0_usize;
                let mut written = 0_usize;
                for uid in self.state.borrow().uids.iter().skip(start) {
                    if cursor + 4 > BUFFER_SIZE {
                        break;
                    }
                    payload.buffer[cursor..cursor + 4].copy_from_slice(&uid.to_le_bytes());
                    cursor += 4;
                    written += 1;
                }
                payload.data_size = u32::try_from(cursor)?;
                payload.arg1 = u32::try_from(start + written)?;
            }
            // The kernel replies with "1\n" or "0\n".
            Command::BlockIsolatedUids => {
                self.state.borrow_mut().isolated = payload.arg1 == 1;
            }
            Command::GetIsolatedState => {
                let isolated = self.state.borrow().isolated;
                payload.buffer[0] = if isolated { b'1' } else { b'0' };
                payload.buffer[1] = b'\n';
                payload.data_size = 2;
            }
        }
        payload.status = 0;
        Ok(())
    }
}

/// Build a driver plus a handle that can still inspect the fake kernel.
fn driver(mode: Mode) -> (Driver<Rc<FakeKernel>>, Rc<FakeKernel>) {
    let kernel = Rc::new(FakeKernel::new(mode));
    (Driver::new(Rc::clone(&kernel)), kernel)
}

/// As [`driver`], with a specific API version string.
fn driver_versioned(mode: Mode, version: &'static str) -> Driver<Rc<FakeKernel>> {
    Driver::new(Rc::new(FakeKernel::new(mode).with_version(version)))
}

#[test]
fn payload_is_exactly_one_page() {
    assert_eq!(size_of::<Payload>(), PAGE_SIZE);
    assert_eq!(std::mem::offset_of!(Payload, buffer), 28);
    assert_eq!(BUFFER_SIZE, 4068);
    assert_eq!(MAGIC.to_be_bytes(), *b"\0NOMOUNT");
}

#[test]
fn api_version_parses_the_leading_integer() {
    assert_eq!(parse_api_version("21"), Some(21));
    assert_eq!(parse_api_version("21 (1234)"), Some(21));
    assert_eq!(parse_api_version("22"), Some(22));
    assert_eq!(parse_api_version("22 (1234)"), Some(22));
    assert_eq!(parse_api_version(" 20\n"), Some(20));
    assert_eq!(parse_api_version("unknown"), None);
    assert_eq!(parse_api_version(""), None);
}

#[test]
fn only_an_absent_driver_is_worth_loading() {
    // A registered key type cannot be registered twice, so a ready or outdated
    // driver (kernel built-in included) must never be followed by a load.
    assert!(should_load(&DriverProbe {
        state: DriverState::Absent,
        version: None,
    }));
    assert!(!should_load(&DriverProbe {
        state: DriverState::Outdated,
        version: Some("20".to_string()),
    }));
    assert!(!should_load(&DriverProbe {
        state: DriverState::Ready,
        version: Some("22".to_string()),
    }));
    assert_eq!(DriverKind::Builtin.as_str(), "builtin");
    assert_eq!(DriverKind::Module.as_str(), "module");
}

#[test]
fn module_loaded_matches_proc_modules() {
    assert!(module_loaded(
        "nomount 123456 0 - Live 0x0\nother 1 0 - Live\n"
    ));
    assert!(!module_loaded("nomountx 123456 0 - Live\n"));
    assert!(!module_loaded(""));
}

#[test]
fn unfinished_load_record_blocks_reentry_in_the_same_cycle() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    mount_guard::begin(
        &attempt,
        mount_guard::Stage::Load,
        "nomount",
        "android12-5.10",
        &mount_guard::current_boot_id(),
        4,
    )
    .unwrap();
    let error = reject_unfinished_load(&attempt).unwrap_err();
    assert!(error.to_string().contains("unfinished"));
}

#[test]
fn legacy_load_record_is_preserved_before_injection_reuses_the_file() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Load,
        "nomount",
        "android12-5.10",
        "old-boot",
        2,
    )
    .unwrap();
    preserve_legacy_load_record(&attempt, &legacy).unwrap();
    let preserved = mount_guard::read(&attempt).unwrap();
    assert_eq!(preserved.stage, mount_guard::Stage::Load);
    assert_eq!(preserved.boot_id, "old-boot");
    assert_eq!(preserved.generation, 2);
    assert!(legacy.exists());

    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Inject,
        "builtin",
        "mode=nomount configured=nomount",
        "old-boot",
        2,
    )
    .unwrap();
    mount_guard::clear(&legacy);
    assert!(reject_unfinished_load(&attempt).is_err());
}

#[test]
fn legacy_injection_record_does_not_become_a_load_blocker() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Inject,
        "nomount",
        "mode=nomount",
        "old-boot",
        2,
    )
    .unwrap();
    preserve_legacy_load_record(&attempt, &legacy).unwrap();
    assert!(!attempt.exists());
}

#[test]
fn legacy_load_migration_failure_keeps_the_original_guard() {
    let dir = tempfile::tempdir().unwrap();
    let attempt_parent = dir.path().join("blocked");
    let attempt = attempt_parent.join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    let lock_path = dir.path().join("nomount_load.lock");
    std::fs::write(&attempt_parent, "not a directory").unwrap();
    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Load,
        "nomount",
        "android12-5.10",
        "old-boot",
        2,
    )
    .unwrap();
    assert!(preserve_legacy_load_guard_at(&legacy, &attempt, &lock_path).is_err());
    assert_eq!(
        mount_guard::read(&legacy).map(|record| record.stage),
        Some(mount_guard::Stage::Load)
    );
    assert!(!attempt.exists());
}

#[test]
fn load_lock_serializes_concurrent_attempts() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("nomount_load.lock");
    let lock = load_lock_at(&path).unwrap();
    let error = load_lock_at(&path).unwrap_err();
    assert!(error.to_string().contains("already in progress"));
    drop(lock);
    load_lock_at(&path).unwrap();
}

#[test]
fn explicit_retry_cannot_clear_an_active_load_guard() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    let lock_path = dir.path().join("nomount_load.lock");
    mount_guard::begin(
        &attempt,
        mount_guard::Stage::Load,
        "nomount",
        "android12-5.10",
        "boot",
        3,
    )
    .unwrap();
    let active = load_lock_at(&lock_path).unwrap();
    assert!(forget_inflight_at(&attempt, &legacy, &lock_path).is_err());
    assert!(attempt.exists());
    drop(active);
    forget_inflight_at(&attempt, &legacy, &lock_path).unwrap();
    assert!(!attempt.exists());
}

#[test]
fn explicit_retry_clears_the_nomount_guards_and_allows_the_next_load() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    let lock_path = dir.path().join("nomount_load.lock");
    mount_guard::begin(
        &attempt,
        mount_guard::Stage::Load,
        "nomount",
        "android12-5.10",
        "old-boot",
        3,
    )
    .unwrap();
    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Inject,
        "nomount",
        "mode=nomount configured=nomount",
        "old-boot",
        3,
    )
    .unwrap();

    assert!(reject_unfinished_load(&attempt).is_err());
    forget_inflight_at(&attempt, &legacy, &lock_path).unwrap();
    reject_unfinished_load(&attempt).unwrap();
    assert!(!attempt.exists());
    assert!(!legacy.exists());
}

#[test]
fn explicit_retry_does_not_clear_another_providers_guard() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    let lock_path = dir.path().join("nomount_load.lock");
    mount_guard::begin(
        &attempt,
        mount_guard::Stage::Load,
        "nomount",
        "android12-5.10",
        "old-boot",
        3,
    )
    .unwrap();
    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Inject,
        "builtin",
        "mode=builtin",
        "old-boot",
        3,
    )
    .unwrap();

    forget_inflight_at(&attempt, &legacy, &lock_path).unwrap();
    assert!(!attempt.exists());
    assert_eq!(
        mount_guard::read(&legacy).map(|record| record.provider),
        Some("builtin".to_string())
    );
}

#[test]
fn explicit_retry_reports_a_guard_clear_failure() {
    let dir = tempfile::tempdir().unwrap();
    let attempt = dir.path().join("nomount_load_inflight");
    let legacy = dir.path().join("mount_inflight");
    let lock_path = dir.path().join("nomount_load.lock");
    std::fs::create_dir(&attempt).unwrap();
    std::fs::write(attempt.join("cannot-remove"), "").unwrap();
    mount_guard::begin(
        &legacy,
        mount_guard::Stage::Inject,
        "nomount",
        "mode=nomount configured=nomount",
        "old-boot",
        3,
    )
    .unwrap();

    let error = forget_inflight_at(&attempt, &legacy, &lock_path).unwrap_err();
    assert!(error.to_string().contains("cannot clear the load guard"));
    assert!(attempt.exists());
    assert!(legacy.exists());
}

#[test]
fn accepted_rules_are_not_success_without_a_matching_vfs_path() {
    let dir = tempfile::tempdir().unwrap();
    let virtual_path = dir.path().join("visible");
    let real_path = dir.path().join("module-file");
    std::fs::write(&virtual_path, "same").unwrap();
    std::fs::write(&real_path, "same").unwrap();
    let mut rule = Rule::redirect(virtual_path.to_string_lossy(), real_path.to_string_lossy());

    let verified = verify_data_path(std::slice::from_ref(&rule)).unwrap();
    assert!(
        verified
            .as_deref()
            .is_some_and(|value| value.contains("visible") && value.contains("module-file"))
    );

    std::fs::write(&virtual_path, "different").unwrap();
    let error = verify_data_path(&[rule.clone()]).unwrap_err().to_string();
    assert!(error.contains("no data-plane path could be verified"));

    rule.real_path = Some(dir.path().join("missing").to_string_lossy().into_owned());
    assert!(verify_data_path(&[rule]).is_err());
}

#[test]
fn failed_data_verification_clears_the_rule_set_before_fallback() {
    let dir = tempfile::tempdir().unwrap();
    let virtual_path = dir.path().join("visible");
    let real_path = dir.path().join("module-file");
    std::fs::write(&virtual_path, "stock").unwrap();
    std::fs::write(&real_path, "module").unwrap();
    let rule = Rule::redirect(virtual_path.to_string_lossy(), real_path.to_string_lossy());
    let (driver, kernel) = driver(Mode::Ready);

    let error = reconcile_and_verify(&driver, &[rule]).unwrap_err();
    assert!(error.to_string().contains("rules were cleared"));
    assert!(kernel.written_rules().is_empty());
    assert!(kernel.commands().contains(&Command::ClearRules));
}

#[test]
fn an_installed_nomount_provider_owns_injection() {
    let dir = tempfile::tempdir().unwrap();
    let metamodule = dir.path().join("vendor-nomount");
    std::fs::create_dir_all(metamodule.join("bin")).unwrap();
    std::fs::write(metamodule.join("bin/nm"), "").unwrap();
    assert_eq!(
        installed_driver_module_at(&metamodule),
        Some("vendor-nomount".to_string())
    );

    std::fs::write(metamodule.join(crate::mount_plan::DISABLE_FILE_NAME), "").unwrap();
    assert_eq!(installed_driver_module_at(&metamodule), None);
}

#[test]
fn redirect_and_whiteout_records_follow_the_wire_layout() {
    let redirect = Rule::redirect("/system/etc/hosts", "/data/adb/modules/m/system/etc/hosts");
    let batches = encode_batches(std::slice::from_ref(&redirect)).unwrap();
    assert_eq!(batches.len(), 1);
    let bytes = &batches[0];
    assert_eq!(&bytes[..20], &[0; 20]);
    assert_eq!(
        usize::from(u16::from_le_bytes([bytes[20], bytes[21]])),
        "/system/etc/hosts".len()
    );
    let v_len = usize::from(u16::from_le_bytes([bytes[20], bytes[21]]));
    let r_len = usize::from(u16::from_le_bytes([bytes[22], bytes[23]]));
    assert_eq!(v_len, "/system/etc/hosts".len());
    assert_eq!(r_len, "/data/adb/modules/m/system/etc/hosts".len());
    assert_eq!(&bytes[24..24 + v_len], b"/system/etc/hosts");

    let whiteout = Rule::whiteout("/system/etc/removed");
    let batches = encode_batches(std::slice::from_ref(&whiteout)).unwrap();
    let bytes = &batches[0];
    assert_eq!(
        u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
        WHITEOUT_FLAG
    );
    let r_len = usize::from(u16::from_le_bytes([bytes[22], bytes[23]]));
    assert_eq!(r_len, 0);
    assert_eq!(bytes.len(), RULE_HEADER_SIZE + "/system/etc/removed".len());
}

#[test]
fn single_uid_rules_encode_a_matching_range_endpoint() {
    let mut rule = Rule::redirect("/system/etc/hosts", "/data/adb/modules/m/system/etc/hosts");
    rule.uid = 10_123;
    let bytes = encode_batches(std::slice::from_ref(&rule))
        .unwrap()
        .remove(0);
    assert_eq!(
        &bytes[4..20],
        [
            10_123_u32.to_le_bytes(),
            10_123_u32.to_le_bytes(),
            0_u32.to_le_bytes(),
            0_u32.to_le_bytes(),
        ]
        .concat()
    );
    assert_eq!(
        u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) & OPAQUE_FLAG,
        0
    );
    assert_eq!(parse_list_records(&bytes).unwrap()[0].uid_end, 10_123);
}

#[test]
fn encoder_rejects_unsafe_v22_flag_combinations_and_scopes() {
    let mut opaque_whiteout = Rule::whiteout("/system/etc/gone");
    opaque_whiteout.opaque = true;
    assert!(encode_batches(&[opaque_whiteout]).is_err());

    let mut reversed = Rule::redirect("/system/etc/a", "/data/a");
    reversed.uid = 2000;
    reversed.uid_end = 1000;
    assert!(encode_batches(&[reversed]).is_err());

    let mut outside = Rule::redirect("/system/etc/a", "/data/a");
    outside.uid = 1000;
    outside.uid_end = 2000;
    outside.deny_start = 900;
    outside.deny_end = 1200;
    assert!(encode_batches(&[outside]).is_err());
}

#[test]
fn batches_split_before_overflowing_the_payload_buffer() {
    let rules: Vec<Rule> = (0..80)
        .map(|index| {
            Rule::redirect(
                format!("/system/etc/file-{index:04}"),
                format!("/data/adb/modules/m/system/etc/file-{index:04}"),
            )
        })
        .collect();
    let batches = encode_batches(&rules).unwrap();
    assert!(batches.len() > 1, "expected the rules to be split");
    for batch in &batches {
        assert!(batch.len() <= BUFFER_SIZE);
    }
    let total: usize = batches.iter().map(Vec::len).sum();
    assert_eq!(total, rules.iter().map(record_len).sum::<usize>());
}

fn record_len(rule: &Rule) -> usize {
    RULE_HEADER_SIZE + rule.virtual_path.len() + rule.real_path.as_deref().unwrap_or_default().len()
}

#[test]
fn redirect_without_a_target_is_rejected() {
    let broken = Rule {
        virtual_path: "/system/etc/hosts".to_string(),
        real_path: None,
        whiteout: false,
        virtual_dir: false,
        opaque: false,
        uid: 0,
        uid_end: 0,
        deny_start: 0,
        deny_end: 0,
    };
    assert!(encode_batches(std::slice::from_ref(&broken)).is_err());
}

#[test]
fn rule_lists_round_trip_and_reject_truncation() {
    let rules = vec![
        Rule::redirect("/system/etc/hosts", "/data/adb/modules/m/system/etc/hosts"),
        Rule::whiteout("/system/etc/gone"),
    ];
    let bytes = encode_batches(&rules).unwrap().concat();
    assert_eq!(parse_list_records(&bytes).unwrap(), rules);
    assert!(parse_list_records(&bytes[..bytes.len() - 1]).is_err());
}

#[test]
fn apply_rules_reports_the_readback_count() {
    let (driver, kernel) = driver(Mode::Ready);
    let rules = vec![Rule::redirect(
        "/system/etc/hosts",
        "/data/adb/modules/m/system/etc/hosts",
    )];
    assert_eq!(driver.apply_rules(&rules).unwrap(), 1);
    assert_eq!(kernel.written_rules(), rules);
}

#[test]
fn apply_rules_fails_when_the_kernel_does_not_keep_them() {
    let (driver, _kernel) = driver(Mode::ForgetsRules);
    let rules = vec![Rule::redirect(
        "/system/etc/hosts",
        "/data/adb/modules/m/system/etc/hosts",
    )];
    let error = driver.apply_rules(&rules).unwrap_err().to_string();
    assert!(
        error.contains("did not accept"),
        "unexpected error: {error}"
    );
}

#[test]
fn probe_reports_absent_outdated_and_ready() {
    let (absent, _kernel) = driver(Mode::Silent);
    assert_eq!(absent.probe().state, DriverState::Absent);

    let outdated = driver_versioned(Mode::Ready, "21");
    let probe = outdated.probe();
    assert_eq!(probe.state, DriverState::Outdated);
    assert_eq!(probe.version.as_deref(), Some("21"));
    assert!(ensure_usable(&probe).is_err());

    let (refused, _kernel) = driver(Mode::ListingRefused);
    assert_eq!(refused.probe().state, DriverState::Outdated);

    let (ready, _kernel) = driver(Mode::Ready);
    let probe = ready.probe();
    assert!(probe.ready());
    assert_eq!(probe.version.as_deref(), Some("22"));
    assert!(ensure_usable(&probe).is_ok());
}

#[test]
fn delete_records_use_the_18_byte_v22_header() {
    let (driver, kernel) = driver(Mode::Ready);
    let path = "/system/etc/hosts";
    driver.delete_rules(&[path.to_string()]).unwrap();
    let record = kernel.state.borrow().last_delete.clone();
    assert_eq!(record.len(), DELETE_HEADER_SIZE + path.len());
    assert_eq!(&record[..16], &[0; 16]);
    assert_eq!(
        u16::from_le_bytes([record[16], record[17]]),
        u16::try_from(path.len()).unwrap()
    );
    assert_eq!(&record[18..], path.as_bytes());
}

#[test]
fn list_reply_parses_ranges_exclusions_and_opaque() {
    let range = Rule {
        virtual_path: "/system/etc/range".to_string(),
        real_path: Some("/data/adb/modules/m/system/etc/range".to_string()),
        whiteout: false,
        virtual_dir: false,
        opaque: false,
        uid: 1000,
        uid_end: 2000,
        deny_start: 1500,
        deny_end: 1600,
    };
    let opaque = Rule {
        virtual_path: "/product/fonts".to_string(),
        real_path: Some("/data/adb/modules/m/product/fonts".to_string()),
        whiteout: false,
        virtual_dir: false,
        opaque: true,
        uid: 1000,
        uid_end: 2000,
        deny_start: 0,
        deny_end: 0,
    };
    let mut bytes = kernel_record(&range);
    bytes.extend_from_slice(&kernel_record(&opaque));
    assert_eq!(parse_list_records(&bytes).unwrap(), vec![range, opaque]);
}

#[test]
fn uid_exclusions_are_applied_and_listed() {
    let (driver, kernel) = driver(Mode::Ready);
    driver.add_uids(&[1000, 10123]).unwrap();
    assert_eq!(driver.list_uids().unwrap(), vec![1000, 10123]);
    driver.del_uids(&[1000]).unwrap();
    assert_eq!(driver.list_uids().unwrap(), vec![10123]);
    driver.del_uids(&[10123]).unwrap();
    assert!(driver.list_uids().unwrap().is_empty());
    // Every step must have reached the kernel, whatever the paging did.
    assert!(kernel.commands().contains(&Command::AddUid));
    assert!(kernel.commands().contains(&Command::DelUid));
}

#[test]
fn deleting_rules_batches_and_clears_state() {
    let (driver, _kernel) = driver(Mode::Ready);
    let rules: Vec<Rule> = (0..60)
        .map(|index| {
            Rule::redirect(
                format!("/system/etc/file-{index:04}"),
                format!("/data/adb/modules/m/system/etc/file-{index:04}"),
            )
        })
        .collect();
    driver.apply_rules(&rules).unwrap();
    assert_eq!(driver.list_rules().unwrap().len(), rules.len());

    let paths: Vec<String> = rules.iter().map(|rule| rule.virtual_path.clone()).collect();
    driver.delete_rules(&paths).unwrap();
    assert!(driver.list_rules().unwrap().is_empty());
}

#[test]
fn reconcile_rules_drops_stale_paths() {
    let (driver, kernel) = driver(Mode::Ready);
    driver
        .apply_rules(&[
            Rule::redirect("/system/etc/old", "/data/adb/modules/m/system/etc/old"),
            Rule::redirect("/system/etc/keep", "/data/adb/modules/m/system/etc/keep"),
        ])
        .unwrap();

    let (applied, removed) = driver
        .reconcile_rules(&[Rule::redirect(
            "/system/etc/keep",
            "/data/adb/modules/m/system/etc/keep",
        )])
        .unwrap();
    assert_eq!((applied, removed), (1, 1));
    let paths: Vec<String> = kernel
        .written_rules()
        .into_iter()
        .map(|rule| rule.virtual_path)
        .collect();
    assert_eq!(paths, vec!["/system/etc/keep"]);
}

#[test]
fn a_later_rule_for_the_same_path_wins() {
    // The kernel replaces a rule with the same virtual path and uid, so the
    // last module to claim a path wins. Documented because the built-in engine
    // resolves the same conflict the other way (first wins).
    let (driver, kernel) = driver(Mode::Ready);
    driver
        .apply_rules(&[Rule::redirect(
            "/system/etc/hosts",
            "/data/adb/modules/first/system/etc/hosts",
        )])
        .unwrap();
    driver
        .apply_rules(&[Rule::redirect(
            "/system/etc/hosts",
            "/data/adb/modules/second/system/etc/hosts",
        )])
        .unwrap();
    let rules = kernel.written_rules();
    assert_eq!(rules.len(), 1);
    assert_eq!(
        rules[0].real_path.as_deref(),
        Some("/data/adb/modules/second/system/etc/hosts")
    );
}

#[test]
fn reconcile_keeps_kernel_created_virtual_directories() {
    let (driver, kernel) = driver(Mode::Ready);
    // The fake kernel reports one of our rules and one directory it made up.
    kernel.state.borrow_mut().rules.push(Rule {
        virtual_path: "/system/etc/hosts".to_string(),
        real_path: Some("/data/adb/modules/m/system/etc/hosts".to_string()),
        whiteout: false,
        virtual_dir: false,
        opaque: false,
        uid: 0,
        uid_end: 0,
        deny_start: 0,
        deny_end: 0,
    });
    kernel.state.borrow_mut().rules.push(Rule {
        virtual_path: "/system/etc/newdir".to_string(),
        real_path: None,
        whiteout: false,
        virtual_dir: true,
        opaque: false,
        uid: 0,
        uid_end: 0,
        deny_start: 0,
        deny_end: 0,
    });

    let (applied, removed) = driver
        .reconcile_rules(&[Rule::redirect(
            "/system/etc/hosts",
            "/data/adb/modules/m/system/etc/hosts",
        )])
        .unwrap();
    assert_eq!(
        (applied, removed),
        (1, 0),
        "the virtual directory must survive"
    );
    assert!(
        kernel
            .written_rules()
            .iter()
            .any(|rule| rule.virtual_dir && rule.virtual_path == "/system/etc/newdir")
    );
}

#[test]
fn visibility_config_round_trips_and_rejects_junk() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("nomount_uids");

    // Missing file: nothing wanted, nothing blocked.
    assert_eq!(read_uid_config(&path).unwrap(), UidConfig::default());

    write_uid_config(
        &path,
        &UidConfig {
            uids: vec![1000, 10123],
            block_isolated: true,
        },
    )
    .unwrap();
    let parsed = read_uid_config(&path).unwrap();
    assert_eq!(parsed.uids, vec![1000, 10123]);
    assert!(parsed.block_isolated);

    std::fs::write(&path, "isolated=on\nnot-a-uid\n").unwrap();
    assert!(read_uid_config(&path).is_err());
    std::fs::write(&path, "isolated=maybe\n").unwrap();
    assert!(read_uid_config(&path).is_err());
}

#[test]
fn sync_uids_applies_the_diff_and_the_isolated_flag() {
    let (driver, _kernel) = driver(Mode::Ready);
    driver.add_uids(&[1000, 2000]).unwrap();

    let (added, removed) = sync_uids(
        &driver,
        &UidConfig {
            uids: vec![2000, 3000],
            block_isolated: true,
        },
    )
    .unwrap();
    assert_eq!((added, removed), (1, 1));
    assert_eq!(driver.list_uids().unwrap(), vec![2000, 3000]);
    assert!(driver.isolated_state().unwrap());

    // A second identical call changes nothing.
    let (added, removed) = sync_uids(
        &driver,
        &UidConfig {
            uids: vec![2000, 3000],
            block_isolated: true,
        },
    )
    .unwrap();
    assert_eq!((added, removed), (0, 0));
}

#[test]
fn runtime_record_is_key_value_text() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("nomount_runtime");
    write_runtime_record(
        &path,
        &RuntimeRecord {
            boot_id: "boot-1".to_string(),
            driver_state: "ready".to_string(),
            driver_version: "22".to_string(),
            rules_applied: 42,
            rules_removed: 3,
            verified_path: Some(
                "/system/etc/hosts -> /data/adb/modules/m/system/etc/hosts".to_string(),
            ),
            uid_count: 2,
            isolated_blocked: true,
            error: None,
        },
    )
    .unwrap();
    let text = std::fs::read_to_string(&path).unwrap();
    for expected in [
        "boot_id=boot-1",
        "driver_state=ready",
        "driver_version=22",
        "rules_applied=42",
        "rules_removed=3",
        "verified_path=/system/etc/hosts -> /data/adb/modules/m/system/etc/hosts",
        "uid_count=2",
        "isolated_blocked=true",
        "error=",
    ] {
        assert!(text.contains(expected), "missing {expected} in {text}");
    }
}
