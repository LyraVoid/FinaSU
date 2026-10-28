//! NoMount backend: driver transport, probing and rule application.
//!
//! NoMount is a VFS path redirection module: instead of mounting anything it
//! hijacks lookups and directory iteration, so ksud talks to it through a
//! single custom key type. Every command is one `add_key()` call whose payload
//! is a page-aligned 4096-byte structure the kernel reads and writes back in
//! place; `add_key` itself always fails, so the answer is the `status` field.
//!
//! The protocol is pinned to the vendored module revision: see
//! `userspace/nomount/UPSTREAM.md` for provenance and the payload layout this
//! module encodes. Nothing here is derived from upstream userspace code, which
//! is deliberately not vendored.

use std::fmt::Write as _;
use std::io::{Read, Write};
#[cfg(any(target_os = "android", test))]
use std::os::unix::fs::MetadataExt;

use anyhow::{Context, Result, bail, ensure};

#[cfg(any(target_os = "android", test))]
use crate::mount_guard;
#[cfg(any(target_os = "android", test))]
use std::{fs::OpenOptions, os::fd::AsRawFd, os::unix::fs::OpenOptionsExt, path::Path};

/// Kernel/userspace payload size; the kernel maps exactly one page.
pub const PAGE_SIZE: usize = 4096;

/// Magic stamped into every payload (`"NOMOUNT"` as a big-endian word).
pub const MAGIC: u64 = u64::from_be_bytes(*b"\0NOMOUNT");

/// Bytes of the payload available to command data.
pub const BUFFER_SIZE: usize = PAGE_SIZE - PAYLOAD_HEADER_SIZE;

const PAYLOAD_HEADER_SIZE: usize = 28;

/// Oldest kernel API this backend accepts. The vendored module (v2.2.0)
/// reports `22`; v21 uses an incompatible rule header.
pub const MIN_API_VERSION: u32 = 22;

/// Base name of the module inside the embedded assets.
pub const ASSET_SUFFIX: &str = "_nomount.ko";

/// Whiteout marker: a character device with `rdev == 0`.
pub const WHITEOUT_FLAG: u32 = 1 << 2;

/// Intermediate directory the kernel created to host a deeper rule.
pub const VIRTUAL_DIR_FLAG: u32 = 1 << 1;

/// Resolve a directory as a native replacement instead of merging it.
pub const OPAQUE_FLAG: u32 = 1 << 3;

/// Wire header preceding a rule in add and list replies.
const RULE_HEADER_SIZE: usize = 24;

/// Wire header preceding a path in a delete request.
const DELETE_HEADER_SIZE: usize = 18;

/// Driver state as reported by [`probe`], never inferred from a load attempt.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DriverState {
    /// The key type is not registered: no built-in support and no module loaded.
    Absent,
    /// The API answered but its version is below [`MIN_API_VERSION`].
    Outdated,
    /// The API answered and is usable.
    Ready,
}

impl DriverState {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Absent => "absent",
            Self::Outdated => "outdated",
            Self::Ready => "ready",
        }
    }
}

/// How the driver got into the kernel.
///
/// Both work identically from here on: everything below talks to the key API,
/// so a kernel with `CONFIG_NOMOUNT=y` needs no load and no special case.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DriverKind {
    /// Built into the kernel; nothing to load or unload.
    Builtin,
    /// A loadable module is present in `/proc/modules`.
    Module,
}

impl DriverKind {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Builtin => "builtin",
            Self::Module => "module",
        }
    }
}

/// Whether loading the embedded module can help.
///
/// Only when the API is absent: a registered key type means something already
/// provides it (built into the kernel, or a module another loader put there),
/// and registering it again fails. That also means an outdated built-in driver
/// cannot be fixed from userspace, so callers must say so instead of trying.
#[must_use]
pub const fn should_load(probe: &DriverProbe) -> bool {
    matches!(probe.state, DriverState::Absent)
}

/// A probe result: the driver state plus the version string when one was read.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DriverProbe {
    pub state: DriverState,
    pub version: Option<String>,
}

impl DriverProbe {
    #[must_use]
    pub const fn ready(&self) -> bool {
        matches!(self.state, DriverState::Ready)
    }
}

/// Commands understood by the kernel side.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u32)]
pub enum Command {
    GetVersion = 1,
    AddRule = 2,
    DelRule = 3,
    AddUid = 4,
    DelUid = 5,
    ClearRules = 7,
    GetList = 9,
    GetUids = 10,
    BlockIsolatedUids = 11,
    GetIsolatedState = 12,
}

/// One redirection: `real_path` is served at `virtual_path`, or the path is
/// hidden when `whiteout` is set.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Rule {
    pub virtual_path: String,
    pub real_path: Option<String>,
    pub whiteout: bool,
    /// True for directories the kernel created to host deeper rules.
    pub virtual_dir: bool,
    /// A native replacement hides entries that are not in the module tree.
    pub opaque: bool,
    pub uid: u32,
    pub uid_end: u32,
    pub deny_start: u32,
    pub deny_end: u32,
}

impl Rule {
    #[must_use]
    pub fn whiteout(virtual_path: impl Into<String>) -> Self {
        Self {
            virtual_path: virtual_path.into(),
            real_path: None,
            whiteout: true,
            virtual_dir: false,
            opaque: false,
            uid: 0,
            uid_end: 0,
            deny_start: 0,
            deny_end: 0,
        }
    }

    #[must_use]
    pub fn redirect(virtual_path: impl Into<String>, real_path: impl Into<String>) -> Self {
        Self {
            virtual_path: virtual_path.into(),
            real_path: Some(real_path.into()),
            whiteout: false,
            virtual_dir: false,
            opaque: false,
            uid: 0,
            uid_end: 0,
            deny_start: 0,
            deny_end: 0,
        }
    }
}

/// Wire layout shared with the kernel module. Packed and page-sized on purpose:
/// the kernel rejects a payload that straddles two pages.
#[repr(C, packed)]
pub struct Payload {
    pub magic: u64,
    pub cmd: u32,
    pub target_uid: u32,
    pub status: i32,
    pub arg1: u32,
    pub data_size: u32,
    pub buffer: [u8; BUFFER_SIZE],
}

const _: () = assert!(size_of::<Payload>() == PAGE_SIZE);

/// A zeroed, page-aligned payload.
///
/// The kernel maps exactly the page the payload starts on and writes results
/// back through it, so the payload has to be aligned and writable. Keeping it
/// on the stack also makes construction infallible.
#[repr(C, align(4096))]
pub struct PageBuffer {
    bytes: [u8; PAGE_SIZE],
}

impl PageBuffer {
    #[must_use]
    pub const fn zeroed() -> Self {
        Self {
            bytes: [0; PAGE_SIZE],
        }
    }

    #[must_use]
    pub const fn as_mut(&mut self) -> &mut Payload {
        // SAFETY: `bytes` is page aligned and `PAGE_SIZE` bytes long, which is
        // exactly the size of `Payload`, so the cast is layout-correct.
        unsafe { &mut *self.bytes.as_mut_ptr().cast::<Payload>() }
    }

    #[must_use]
    pub fn data_size(&self) -> u32 {
        // Volatile: the kernel produced this value through its own mapping.
        // SAFETY: the payload pointer is valid and `data_size` is a plain field.
        unsafe { std::ptr::read_volatile(std::ptr::addr_of!((*self.as_ptr()).data_size)) }
    }

    const fn as_ptr(&self) -> *const Payload {
        self.bytes.as_ptr().cast()
    }

    /// Copy `len` bytes of command data the kernel wrote into `buffer`.
    ///
    /// # Errors
    /// Returns an error when `len` exceeds the payload buffer.
    pub fn data(&self, len: usize) -> Result<Vec<u8>> {
        ensure!(
            len <= BUFFER_SIZE,
            "nomount: kernel reported {len} bytes, buffer holds {BUFFER_SIZE}"
        );
        // SAFETY: `buffer` lives inside the payload and `len` was bounded above.
        let src = unsafe { std::ptr::addr_of!((*self.as_ptr()).buffer).cast::<u8>() };
        Ok(unsafe { std::slice::from_raw_parts(src, len) }.to_vec())
    }
}

/// One command round trip. Split out so the encoding and the command semantics
/// can be tested without a kernel.
pub trait Transport {
    /// Send `payload` and wait for the kernel's answer.
    ///
    /// # Errors
    /// Returns an error when the payload cannot be delivered or the kernel
    /// reports a non-zero status.
    fn send(&self, payload: &mut Payload) -> Result<()>;
}

/// The real transport: one `add_key()` against the `nomount` key type.
pub struct KernelTransport;

impl Transport for KernelTransport {
    fn send(&self, payload: &mut Payload) -> Result<()> {
        payload.status = -1;
        let address = std::ptr::from_mut::<Payload>(payload) as u64;
        // `add_key` is the whole protocol: the key type's `preparse` reads the
        // address, runs the command and returns `-ECANCELED`, so the syscall's
        // own return value carries no information.
        unsafe {
            libc::syscall(
                libc::SYS_add_key,
                c"nomount".as_ptr(),
                c"trigger".as_ptr(),
                std::ptr::addr_of!(address),
                8_usize,
                -1_isize,
            );
        }
        // Copy the fields out: `Payload` is packed, so only value reads are
        // sound, never references to its fields.
        let status = unsafe { std::ptr::read_volatile(std::ptr::addr_of!(payload.status)) };
        let cmd = payload.cmd;
        ensure!(status == 0, "nomount: command {cmd} failed with {status}");
        Ok(())
    }
}

/// A driver handle bound to a transport.
pub struct Driver<T: Transport> {
    transport: T,
}

impl Driver<KernelTransport> {
    #[must_use]
    pub const fn kernel() -> Self {
        Self {
            transport: KernelTransport,
        }
    }
}

impl<T: Transport> Driver<T> {
    #[cfg(test)]
    #[must_use]
    pub const fn new(transport: T) -> Self {
        Self { transport }
    }

    /// Ask the kernel for its API version.
    ///
    /// # Errors
    /// Returns an error when the key type is not registered or the reply is
    /// not valid UTF-8.
    pub fn version(&self) -> Result<String> {
        let mut page = PageBuffer::zeroed();
        page.as_mut().magic = MAGIC;
        page.as_mut().cmd = Command::GetVersion as u32;
        self.transport.send(page.as_mut())?;
        let len = usize::try_from(page.data_size()).context("nomount: bad version length")?;
        let bytes = page.data(len)?;
        String::from_utf8(bytes).context("nomount: version reply is not UTF-8")
    }

    /// Probe the driver without changing any state.
    ///
    /// A version reply alone is not enough: the listing command also has to
    /// answer, otherwise a half-registered key type would look healthy.
    #[must_use]
    pub fn probe(&self) -> DriverProbe {
        let version = match self.version() {
            Ok(version) => version,
            Err(error) => {
                log::debug!("nomount: driver absent ({error:#})");
                return DriverProbe {
                    state: DriverState::Absent,
                    version: None,
                };
            }
        };
        let state = match parse_api_version(&version) {
            Some(api) if api >= MIN_API_VERSION => DriverState::Ready,
            _ => DriverState::Outdated,
        };
        if state == DriverState::Ready
            && let Err(error) = self.list_rules()
        {
            log::debug!("nomount: listing refused ({error:#}); driver not ready");
            return DriverProbe {
                state: DriverState::Outdated,
                version: Some(version),
            };
        }
        DriverProbe {
            state,
            version: Some(version),
        }
    }

    /// Apply rules, then read the kernel's rule list back to prove they landed.
    ///
    /// The verification is the point: a transport that silently drops rules
    /// must not be reported as a successful injection.
    ///
    /// # Errors
    /// Returns an error when a rule cannot be encoded, the kernel refuses a
    /// batch, or the read-back does not contain every virtual path.
    pub fn apply_rules(&self, rules: &[Rule]) -> Result<usize> {
        if rules.is_empty() {
            return Ok(0);
        }
        for batch in encode_batches(rules)? {
            self.command(Command::AddRule, &batch)?;
        }
        let applied: std::collections::HashSet<String> = self
            .list_rules()?
            .into_iter()
            .map(|rule| rule.virtual_path)
            .collect();
        let missing: Vec<&str> = rules
            .iter()
            .map(|rule| rule.virtual_path.as_str())
            .filter(|path| !applied.contains(*path))
            .collect();
        ensure!(
            missing.is_empty(),
            "nomount: kernel did not accept {} rule(s), first missing: {}",
            missing.len(),
            missing[0]
        );
        Ok(rules.len())
    }

    /// The rules currently active in the kernel.
    ///
    /// # Errors
    /// Returns an error when the reply is malformed or the kernel refuses the
    /// command.
    pub fn list_rules(&self) -> Result<Vec<Rule>> {
        let mut rules = Vec::new();
        loop {
            let mut page = PageBuffer::zeroed();
            page.as_mut().magic = MAGIC;
            page.as_mut().cmd = Command::GetList as u32;
            page.as_mut().arg1 = u32::try_from(rules.len()).context("nomount: rule overflow")?;
            self.transport.send(page.as_mut())?;
            let len = usize::try_from(page.data_size()).context("nomount: bad list length")?;
            if len == 0 {
                return Ok(rules);
            }
            let batch = parse_list_records(&page.data(len)?)?;
            let count = batch.len();
            rules.extend(batch);
            if count == 0 {
                return Ok(rules);
            }
        }
    }

    /// Remove specific virtual paths.
    ///
    /// # Errors
    /// Returns an error when a path is too long or the kernel refuses a batch.
    pub fn delete_rules(&self, virtual_paths: &[String]) -> Result<()> {
        if virtual_paths.is_empty() {
            return Ok(());
        }
        let mut page = PageBuffer::zeroed();
        let mut cursor = 0_usize;
        for path in virtual_paths {
            let len = u16::try_from(path.len()).context("nomount: path too long")?;
            let record = DELETE_HEADER_SIZE + path.len();
            ensure!(
                record <= BUFFER_SIZE,
                "nomount: {path} does not fit in one payload"
            );
            if cursor + record > BUFFER_SIZE {
                self.flush(Command::DelRule, &mut page, cursor)?;
                cursor = 0;
            }
            let buffer = &mut page.as_mut().buffer;
            buffer[cursor..cursor + 16].fill(0);
            buffer[cursor + 16..cursor + 18].copy_from_slice(&len.to_le_bytes());
            buffer[cursor + 18..cursor + 18 + path.len()].copy_from_slice(path.as_bytes());
            cursor += record;
        }
        self.flush(Command::DelRule, &mut page, cursor)
    }

    /// Block or unblock isolated-process UIDs.
    ///
    /// # Errors
    /// Returns an error when the kernel refuses the command.
    pub fn block_isolated(&self, on: bool) -> Result<()> {
        let mut page = PageBuffer::zeroed();
        page.as_mut().magic = MAGIC;
        page.as_mut().cmd = Command::BlockIsolatedUids as u32;
        page.as_mut().arg1 = u32::from(on);
        self.transport.send(page.as_mut())
    }

    /// Isolated-process blocking state.
    ///
    /// # Errors
    /// Returns an error when the kernel refuses the command.
    pub fn isolated_state(&self) -> Result<bool> {
        let mut page = PageBuffer::zeroed();
        page.as_mut().magic = MAGIC;
        page.as_mut().cmd = Command::GetIsolatedState as u32;
        self.transport.send(page.as_mut())?;
        let len = usize::try_from(page.data_size()).context("nomount: bad state length")?;
        Ok(page.data(len)?.first() == Some(&b'1'))
    }

    /// Add UIDs to the exclusion list (they see the stock filesystem).
    ///
    /// # Errors
    /// Returns an error when the kernel refuses a UID.
    pub fn add_uids(&self, uids: &[u32]) -> Result<()> {
        self.each_uid(Command::AddUid, uids)
    }

    /// Remove UIDs from the exclusion list.
    ///
    /// # Errors
    /// Returns an error when the kernel refuses a UID.
    pub fn del_uids(&self, uids: &[u32]) -> Result<()> {
        self.each_uid(Command::DelUid, uids)
    }

    /// The UIDs currently excluded from redirection.
    ///
    /// # Errors
    /// Returns an error when the reply is malformed.
    pub fn list_uids(&self) -> Result<Vec<u32>> {
        let mut uids = Vec::new();
        loop {
            let mut page = PageBuffer::zeroed();
            page.as_mut().magic = MAGIC;
            page.as_mut().cmd = Command::GetUids as u32;
            page.as_mut().arg1 = u32::try_from(uids.len()).context("nomount: uid overflow")?;
            self.transport.send(page.as_mut())?;
            let len = usize::try_from(page.data_size()).context("nomount: bad uid length")?;
            if len < 4 {
                return Ok(uids);
            }
            let bytes = page.data(len)?;
            let count = bytes.len() / 4;
            let mut index = 0;
            while index + 4 <= bytes.len() {
                uids.push(u32::from_le_bytes([
                    bytes[index],
                    bytes[index + 1],
                    bytes[index + 2],
                    bytes[index + 3],
                ]));
                index += 4;
            }
            if count == 0 {
                return Ok(uids);
            }
        }
    }

    /// Drop every redirection rule, keeping UID exclusions.
    ///
    /// # Errors
    /// Returns an error when the kernel refuses the command.
    pub fn clear_rules(&self) -> Result<()> {
        self.command(Command::ClearRules, &[])
    }

    /// Make the kernel's rules match `rules` exactly.
    ///
    /// Stale rules from an earlier module state are removed, so re-running the
    /// injection for the same boot converges instead of accumulating paths.
    ///
    /// # Errors
    /// Returns an error when the kernel refuses a change or the read-back does
    /// not contain every wanted path.
    pub fn reconcile_rules(&self, rules: &[Rule]) -> Result<(usize, usize)> {
        let wanted: std::collections::HashSet<&str> = rules
            .iter()
            .map(|rule| rule.virtual_path.as_str())
            .collect();
        let stale: Vec<String> = self
            .list_rules()?
            .into_iter()
            // Directories the kernel created to host deeper rules are not ours
            // to drop: they disappear on their own when their children go.
            .filter(|rule| !rule.virtual_dir)
            .map(|rule| rule.virtual_path)
            .filter(|path| !wanted.contains(path.as_str()))
            .collect();
        let removed = stale.len();
        if !stale.is_empty() {
            self.delete_rules(&stale)?;
        }
        let applied = self.apply_rules(rules)?;
        Ok((applied, removed))
    }

    fn each_uid(&self, command: Command, uids: &[u32]) -> Result<()> {
        for uid in uids {
            let mut page = PageBuffer::zeroed();
            page.as_mut().magic = MAGIC;
            page.as_mut().cmd = command as u32;
            page.as_mut().target_uid = *uid;
            self.transport.send(page.as_mut())?;
        }
        Ok(())
    }

    fn command(&self, command: Command, data: &[u8]) -> Result<()> {
        ensure!(data.len() <= BUFFER_SIZE, "nomount: command data too large");
        let mut page = PageBuffer::zeroed();
        page.as_mut().magic = MAGIC;
        page.as_mut().cmd = command as u32;
        page.as_mut().buffer[..data.len()].copy_from_slice(data);
        page.as_mut().data_size = u32::try_from(data.len()).context("nomount: data too large")?;
        self.transport.send(page.as_mut())
    }

    fn flush(&self, command: Command, page: &mut PageBuffer, len: usize) -> Result<()> {
        if len == 0 {
            return Ok(());
        }
        // Stamp the magic here so no batching path can forget it: the kernel
        // rejects a payload without it.
        page.as_mut().magic = MAGIC;
        page.as_mut().cmd = command as u32;
        page.as_mut().data_size = u32::try_from(len).context("nomount: batch too large")?;
        page.as_mut().arg1 = 0;
        self.transport.send(page.as_mut())
    }
}

/// A rule readback alone does not prove that lookup changed.
#[cfg(any(target_os = "android", test))]
pub fn verify_data_path(rules: &[Rule]) -> Result<Option<String>> {
    let mut failures = Vec::new();
    for rule in rules.iter().filter(|rule| !rule.virtual_dir) {
        let result = if rule.whiteout {
            verify_whiteout(rule)
        } else if let Some(real) = rule.real_path.as_deref() {
            verify_redirect(rule, real)
        } else {
            bail!("nomount: redirect rule {} has no target", rule.virtual_path);
        };
        match result {
            Ok(description) => return Ok(Some(description)),
            Err(error) => failures.push(format!("{}: {error:#}", rule.virtual_path)),
        }
    }
    if failures.is_empty() {
        return Ok(None);
    }
    bail!(
        "nomount: rules were accepted but no data-plane path could be verified: {}",
        failures[0]
    )
}

fn verify_whiteout(rule: &Rule) -> Result<String> {
    match std::fs::symlink_metadata(&rule.virtual_path) {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            Ok(format!("{} hidden (path absent)", rule.virtual_path))
        }
        Err(error) => Err(error).with_context(|| format!("read whiteout {}", rule.virtual_path)),
        Ok(_) => bail!("whiteout is visible"),
    }
}

fn verify_redirect(rule: &Rule, real: &str) -> Result<String> {
    let virtual_metadata = std::fs::symlink_metadata(&rule.virtual_path)
        .with_context(|| format!("stat {}", rule.virtual_path))?;
    let real_metadata = std::fs::symlink_metadata(real).with_context(|| format!("stat {real}"))?;
    let virtual_type = virtual_metadata.file_type();
    let real_type = real_metadata.file_type();
    ensure!(
        virtual_type.is_file() == real_type.is_file()
            && virtual_type.is_dir() == real_type.is_dir()
            && virtual_type.is_symlink() == real_type.is_symlink(),
        "virtual path type does not match {real}"
    );
    if real_type.is_file() {
        ensure!(
            virtual_metadata.len() == real_metadata.len()
                && virtual_metadata.mode() == real_metadata.mode()
                && virtual_metadata.uid() == real_metadata.uid()
                && virtual_metadata.gid() == real_metadata.gid(),
            "virtual file metadata does not match {real}"
        );
        ensure!(
            files_equal(Path::new(&rule.virtual_path), Path::new(real))?,
            "virtual file contents do not match {real}"
        );
    } else if real_type.is_symlink() {
        ensure!(
            std::fs::read_link(&rule.virtual_path).context("read virtual symlink")?
                == std::fs::read_link(real).context("read target symlink")?,
            "virtual symlink target does not match {real}"
        );
    } else if real_type.is_dir() {
        ensure!(
            directory_names(Path::new(&rule.virtual_path))? == directory_names(Path::new(real))?,
            "virtual directory contents do not match {real}"
        );
    }
    Ok(format!("{} -> {real}", rule.virtual_path))
}

fn files_equal(left: &Path, right: &Path) -> Result<bool> {
    let mut left = std::fs::File::open(left).context("open virtual file")?;
    let mut right = std::fs::File::open(right).context("open target file")?;
    let mut left_buffer = vec![0_u8; 64 * 1024];
    let mut right_buffer = vec![0_u8; 64 * 1024];
    loop {
        let left_len = left.read(&mut left_buffer).context("read virtual file")?;
        let right_len = right.read(&mut right_buffer).context("read target file")?;
        if left_len != right_len {
            return Ok(false);
        }
        if left_buffer[..left_len] != right_buffer[..right_len] {
            return Ok(false);
        }
        if left_len == 0 {
            return Ok(true);
        }
    }
}

fn directory_names(path: &Path) -> Result<Vec<std::ffi::OsString>> {
    let mut names = std::fs::read_dir(path)
        .with_context(|| format!("read directory {}", path.display()))?
        .map(|entry| entry.map(|entry| entry.file_name()))
        .collect::<std::io::Result<Vec<_>>>()
        .with_context(|| format!("read directory entry in {}", path.display()))?;
    names.sort();
    Ok(names)
}

/// Parse the leading integer of a version string (`"22"`, `"22 (605)"`).
#[must_use]
pub fn parse_api_version(version: &str) -> Option<u32> {
    let digits: String = version
        .trim()
        .chars()
        .take_while(char::is_ascii_digit)
        .collect();
    digits.parse().ok()
}

/// Encode rules into wire batches, preserving input order.
///
/// # Errors
/// Returns an error when a rule has no target, or a path does not fit the
/// payload buffer.
pub fn encode_batches(rules: &[Rule]) -> Result<Vec<Vec<u8>>> {
    let mut batches = Vec::new();
    let mut current = Vec::new();
    for rule in rules {
        let real = rule.real_path.as_deref().unwrap_or_default();
        if rule.whiteout {
            ensure!(
                rule.real_path.is_none(),
                "nomount: a whiteout rule must not carry a target"
            );
        } else {
            ensure!(
                !real.is_empty(),
                "nomount: rule for {} has no target",
                rule.virtual_path
            );
        }
        ensure!(
            !(rule.whiteout && rule.opaque),
            "nomount: a rule cannot be both whiteout and opaque"
        );
        let uid_end = if rule.uid_end == 0 {
            rule.uid
        } else {
            rule.uid_end
        };
        ensure!(
            rule.uid <= uid_end,
            "nomount: UID range starts after it ends"
        );
        if rule.deny_start != 0 || rule.deny_end != 0 {
            ensure!(
                rule.deny_start != 0 && rule.deny_end != 0 && rule.deny_start <= rule.deny_end,
                "nomount: invalid excluded UID range"
            );
            ensure!(
                rule.deny_start >= rule.uid && rule.deny_end <= uid_end,
                "nomount: excluded UID range is outside the rule scope"
            );
        }
        let v_len = u16::try_from(rule.virtual_path.len()).context("nomount: path too long")?;
        let r_len = u16::try_from(real.len()).context("nomount: target too long")?;
        let record = RULE_HEADER_SIZE + rule.virtual_path.len() + real.len();
        ensure!(
            record <= BUFFER_SIZE,
            "nomount: rule for {} does not fit in one payload",
            rule.virtual_path
        );
        if current.len() + record > BUFFER_SIZE {
            batches.push(std::mem::take(&mut current));
        }
        let flags = (if rule.whiteout { WHITEOUT_FLAG } else { 0 })
            | (if rule.opaque { OPAQUE_FLAG } else { 0 });
        current.extend_from_slice(&flags.to_le_bytes());
        current.extend_from_slice(&rule.uid.to_le_bytes());
        current.extend_from_slice(&uid_end.to_le_bytes());
        current.extend_from_slice(&rule.deny_start.to_le_bytes());
        current.extend_from_slice(&rule.deny_end.to_le_bytes());
        current.extend_from_slice(&v_len.to_le_bytes());
        current.extend_from_slice(&r_len.to_le_bytes());
        current.extend_from_slice(rule.virtual_path.as_bytes());
        current.extend_from_slice(real.as_bytes());
    }
    if !current.is_empty() {
        batches.push(current);
    }
    Ok(batches)
}

/// Decode the kernel's rule list reply.
///
/// # Errors
/// Returns an error when a record is truncated or not valid UTF-8.
pub fn parse_list_records(data: &[u8]) -> Result<Vec<Rule>> {
    let mut rules = Vec::new();
    let mut cursor = 0_usize;
    while cursor + RULE_HEADER_SIZE <= data.len() {
        let header = &data[cursor..cursor + RULE_HEADER_SIZE];
        let flags = u32::from_le_bytes([header[0], header[1], header[2], header[3]]);
        let uid = u32::from_le_bytes([header[4], header[5], header[6], header[7]]);
        let uid_end = u32::from_le_bytes([header[8], header[9], header[10], header[11]]);
        let deny_start = u32::from_le_bytes([header[12], header[13], header[14], header[15]]);
        let deny_end = u32::from_le_bytes([header[16], header[17], header[18], header[19]]);
        let v_len = usize::from(u16::from_le_bytes([header[20], header[21]]));
        let r_len = usize::from(u16::from_le_bytes([header[22], header[23]]));
        let end = cursor + RULE_HEADER_SIZE + v_len + r_len;
        ensure!(end <= data.len(), "nomount: truncated rule record");
        let path_start = cursor + RULE_HEADER_SIZE;
        let virtual_path = String::from_utf8(data[path_start..path_start + v_len].to_vec())
            .context("nomount: rule path is not UTF-8")?;
        let real_path = if r_len == 0 {
            None
        } else {
            Some(
                String::from_utf8(data[path_start + v_len..end].to_vec())
                    .context("nomount: rule target is not UTF-8")?,
            )
        };
        rules.push(Rule {
            virtual_path,
            real_path,
            whiteout: flags & WHITEOUT_FLAG != 0,
            virtual_dir: flags & VIRTUAL_DIR_FLAG != 0,
            opaque: flags & OPAQUE_FLAG != 0,
            uid,
            uid_end,
            deny_start,
            deny_end,
        });
        cursor = end;
    }
    ensure!(
        cursor == data.len(),
        "nomount: trailing bytes after the last rule record"
    );
    Ok(rules)
}

/// Parse a `/proc/modules` listing for the vendored module.
#[must_use]
pub fn module_loaded(proc_modules: &str) -> bool {
    proc_modules
        .lines()
        .any(|line| line.split_whitespace().next() == Some("nomount"))
}

/// Fail closed when a load attempt produced no usable API.
///
/// A module can load and still be unusable: ksud's loader rewrites vermagic and
/// relocates symbols, so a mismatched build may install itself without
/// implementing what this code talks to.
///
/// # Errors
/// Returns an error describing the unusable state.
pub fn ensure_usable(probe: &DriverProbe) -> Result<()> {
    match probe.state {
        DriverState::Ready => Ok(()),
        DriverState::Absent => bail!("nomount: driver did not answer after loading"),
        DriverState::Outdated => bail!(
            "nomount: driver is {} but this build needs API {MIN_API_VERSION}; refusing to inject",
            probe.version.as_deref().unwrap_or("unknown")
        ),
    }
}

/// Log helper used by the CLI so operators can see what was parsed.
pub fn log_probe(probe: &DriverProbe) {
    log::debug!(
        "nomount: driver={} version={}",
        probe.state.as_str(),
        probe.version.as_deref().unwrap_or("-")
    );
}

// ---------------------------------------------------------------------------
// CLI entry points
// ---------------------------------------------------------------------------

/// Rules the module directory currently asks for.
///
/// # Errors
/// Returns an error when the module tree cannot be walked.
#[cfg(target_os = "android")]
pub fn collect_plan() -> Result<crate::nomount_plan::Plan> {
    collect_plan_filtered(None)
}

/// Rules one module currently asks for; an empty plan when it is off.
///
/// # Errors
/// Returns an error when the module tree cannot be walked.
#[cfg(target_os = "android")]
pub fn collect_plan_for(id: &str) -> Result<crate::nomount_plan::Plan> {
    collect_plan_filtered(Some(id))
}

#[cfg(target_os = "android")]
fn collect_plan_filtered(only: Option<&str>) -> Result<crate::nomount_plan::Plan> {
    let cfg = crate::nomount_plan::PlanConfig::new(
        std::path::Path::new(crate::defs::MODULE_DIR),
        std::path::Path::new("/"),
        std::path::Path::new("/system"),
    );
    let fs = crate::nomount_plan::StdFs::new(
        crate::magic_mount::read_opaque as fn(&std::path::Path) -> Result<bool>,
    );
    only.map_or_else(
        || crate::nomount_plan::collect_rules(&fs, &cfg),
        |id| crate::nomount_plan::collect_rules_for(&fs, &cfg, id),
    )
}

/// Rule counts per module, for the per-module list in the manager.
///
/// # Errors
/// Returns an error when the module tree cannot be walked.
#[cfg(target_os = "android")]
pub fn collect_rule_counts() -> Result<Vec<(String, usize)>> {
    let cfg = crate::nomount_plan::PlanConfig::new(
        std::path::Path::new(crate::defs::MODULE_DIR),
        std::path::Path::new("/"),
        std::path::Path::new("/system"),
    );
    let fs = crate::nomount_plan::StdFs::new(
        crate::magic_mount::read_opaque as fn(&std::path::Path) -> Result<bool>,
    );
    crate::nomount_plan::rule_counts(&fs, &cfg)
}

/// Generate one module's rules and add them to the running kernel.
///
/// The module keeps its place in the boot-time plan; a disabled or skipped
/// module contributes nothing and says so.
///
/// # Errors
/// Returns an error when the driver is not usable or the kernel refuses a rule.
#[cfg(target_os = "android")]
pub fn hot_load(id: &str) -> Result<usize> {
    if let Some(owner) = installed_driver_module() {
        bail!("nomount: module {owner} owns NoMount injection; not writing rules as well");
    }
    let driver = Driver::kernel();
    ensure_usable(&driver.probe())?;
    let plan = collect_plan_for(id)?;
    if plan.rules.is_empty()
        && let Some((_, reason)) = plan.skipped.first()
    {
        bail!("nomount: module {id} is {reason}");
    }
    let applied = driver.apply_rules(&plan.rules);
    log_action(&format!("hot-load {id}"));
    applied
}

/// Remove one module's rules from the running kernel.
///
/// The module's own marks are ignored on purpose: a module that was just
/// disabled still has the paths it injected, and those are the ones to drop.
///
/// # Errors
/// Returns an error when the module tree cannot be walked or the kernel
/// refuses a deletion.
#[cfg(target_os = "android")]
pub fn hot_unload(id: &str) -> Result<usize> {
    let driver = Driver::kernel();
    ensure_usable(&driver.probe())?;
    let cfg = crate::nomount_plan::PlanConfig::new(
        std::path::Path::new(crate::defs::MODULE_DIR),
        std::path::Path::new("/"),
        std::path::Path::new("/system"),
    );
    let fs = crate::nomount_plan::StdFs::new(
        crate::magic_mount::read_opaque as fn(&std::path::Path) -> Result<bool>,
    );
    let plan = crate::nomount_plan::collect_rules_ignoring_marks(&fs, &cfg, id)?;
    let paths: Vec<String> = plan
        .rules
        .iter()
        .map(|rule| rule.virtual_path.clone())
        .collect();
    let count = paths.len();
    driver.delete_rules(&paths)?;
    log_action(&format!("hot-unload {id}"));
    Ok(count)
}

/// What the manager wants the visibility policy to be.
///
/// The kernel holds the active state; this is the desired state, persisted so
/// it survives a reboot and so ksud does not have to enumerate app profiles.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct UidConfig {
    /// Apps that must see the stock filesystem.
    pub uids: Vec<u32>,
    /// Whether isolated processes see the stock filesystem too.
    pub block_isolated: bool,
}

/// Read the stored visibility policy: bare UIDs plus an optional
/// `isolated=<on|off>` directive; blank lines and `#` comments are ignored.
///
/// # Errors
/// Returns an error when the file exists but cannot be read, contains a
/// non-numeric UID, or carries an unrecognised directive.
pub fn read_uid_config(path: &std::path::Path) -> Result<UidConfig> {
    let contents = match std::fs::read_to_string(path) {
        Ok(contents) => contents,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(UidConfig::default());
        }
        Err(error) => {
            return Err(error).with_context(|| format!("read {}", path.display()));
        }
    };
    let mut uids = Vec::new();
    let mut block_isolated = false;
    for line in contents.lines() {
        let entry = line.trim();
        if entry.is_empty() || entry.starts_with('#') {
            continue;
        }
        if let Some(value) = entry.strip_prefix("isolated=") {
            block_isolated = match value {
                "on" | "1" | "true" => true,
                "off" | "0" | "false" => false,
                other => bail!("invalid isolated directive {other:?} in {}", path.display()),
            };
            continue;
        }
        let uid: u32 = entry
            .parse()
            .with_context(|| format!("invalid uid {entry:?} in {}", path.display()))?;
        uids.push(uid);
    }
    uids.sort_unstable();
    uids.dedup();
    Ok(UidConfig {
        uids,
        block_isolated,
    })
}

/// Replace the stored visibility policy.
///
/// # Errors
/// Returns an error when the file cannot be written.
pub fn write_uid_config(path: &std::path::Path, config: &UidConfig) -> Result<()> {
    let mut body = String::new();
    if config.block_isolated {
        body.push_str("isolated=on\n");
    }
    for uid in &config.uids {
        let _ = writeln!(body, "{uid}");
    }
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
    }
    std::fs::write(path, body).with_context(|| format!("write {}", path.display()))
}

/// Make the kernel's visibility policy match `wanted`, and report the change.
///
/// The kernel list is the source of truth for what is active, so the diff is
/// computed against it rather than against a cached guess.
///
/// # Errors
/// Returns an error when the kernel refuses a change or the list cannot be read.
pub fn sync_uids<T: Transport>(driver: &Driver<T>, wanted: &UidConfig) -> Result<(usize, usize)> {
    let current = driver.list_uids()?;
    let added: Vec<u32> = wanted
        .uids
        .iter()
        .copied()
        .filter(|uid| !current.contains(uid))
        .collect();
    let removed: Vec<u32> = current
        .iter()
        .copied()
        .filter(|uid| !wanted.uids.contains(uid))
        .collect();
    if !added.is_empty() {
        driver.add_uids(&added)?;
    }
    if !removed.is_empty() {
        driver.del_uids(&removed)?;
    }
    if driver.isolated_state()? != wanted.block_isolated {
        driver.block_isolated(wanted.block_isolated)?;
    }
    Ok((added.len(), removed.len()))
}

/// Remember the last action that changed kernel state.
///
/// Rules live in RAM and can be dropped by an explicit clear or a per-module
/// unload, so "why is the kernel injecting nothing" needs an answer that
/// survives the kernel log rotating out.
///
/// # Errors
/// Returns an error when the record cannot be written.
pub fn record_action(path: &std::path::Path, action: &str) -> Result<()> {
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |since| since.as_secs());
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
    }
    std::fs::write(
        path,
        format!("time={stamp}\naction={}\n", action.replace('\n', " ")),
    )
    .with_context(|| format!("write {}", path.display()))
}

/// The last recorded state change, for `nomount status`.
#[must_use]
pub fn read_last_action(path: &std::path::Path) -> Option<String> {
    let content = std::fs::read_to_string(path).ok()?;
    content.lines().find_map(|line| {
        let (key, value) = line.split_once('=')?;
        (key == "action").then(|| value.to_string())
    })
}

/// Per-boot NoMount record, read back by `ksud nomount status` and the manager.
#[derive(Debug, Clone, Default)]
pub struct RuntimeRecord {
    pub boot_id: String,
    pub driver_state: String,
    pub driver_version: String,
    pub rules_applied: usize,
    pub rules_removed: usize,
    pub verified_path: Option<String>,
    pub uid_count: usize,
    pub isolated_blocked: bool,
    pub error: Option<String>,
}

/// Persist the per-boot NoMount record.
///
/// # Errors
/// Returns an error when the record cannot be written.
pub fn write_runtime_record(path: &std::path::Path, record: &RuntimeRecord) -> Result<()> {
    let mut body = String::new();
    let _ = writeln!(body, "boot_id={}", record.boot_id);
    let _ = writeln!(body, "driver_state={}", record.driver_state);
    let _ = writeln!(body, "driver_version={}", record.driver_version);
    let _ = writeln!(body, "rules_applied={}", record.rules_applied);
    let _ = writeln!(body, "rules_removed={}", record.rules_removed);
    let _ = writeln!(
        body,
        "verified_path={}",
        record.verified_path.as_deref().unwrap_or("")
    );
    let _ = writeln!(body, "uid_count={}", record.uid_count);
    let _ = writeln!(body, "isolated_blocked={}", record.isolated_blocked);
    let _ = writeln!(
        body,
        "error={}",
        record.error.as_deref().unwrap_or("").replace('\n', " ")
    );
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
    }
    std::fs::write(path, body).with_context(|| format!("write {}", path.display()))
}

/// Current boot id, used to tell this boot's record from a stale one.
#[must_use]
pub fn current_boot_id() -> String {
    std::fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .map_or_else(|_| String::new(), |value| value.trim().to_string())
}

/// Outcome of one injection round, mirroring the per-boot record.
#[derive(Debug, Clone)]
pub struct InjectionResult {
    pub driver_state: String,
    pub driver_version: String,
    pub applied: usize,
    pub removed: usize,
    pub verified_path: Option<String>,
    pub skipped: usize,
    pub uid_count: usize,
    pub isolated_blocked: bool,
    pub warnings: Vec<String>,
}

/// Probe (loading the embedded module when needed), apply the current module
/// rules and sync the stored visibility policy.
///
/// Every step that can silently degrade is either recorded or returned as a
/// warning, so callers never have to guess whether the injection happened.
///
/// # Errors
/// Returns an error when the driver cannot be made usable, the module tree
/// cannot be walked, or the kernel refuses the rules.
#[cfg(target_os = "android")]
pub fn apply_current_plan() -> Result<InjectionResult> {
    if let Some(owner) = installed_driver_module() {
        bail!("nomount: module {owner} owns NoMount injection; not writing rules as well");
    }
    let driver = Driver::kernel();
    let probe = ensure_driver()?;
    let plan = collect_plan()?;
    let (applied, removed, verified_path) = reconcile_and_verify(&driver, &plan.rules)?;

    let mut warnings = Vec::new();
    let mut uid_count = 0_usize;
    let mut isolated_blocked = false;
    match apply_stored_uids() {
        Ok((added, dropped)) => {
            log::info!("nomount: uid exclusions +{added}/-{dropped}");
            uid_count = driver.list_uids().map_or(0, |uids| uids.len());
            isolated_blocked = driver.isolated_state().unwrap_or(false);
        }
        Err(error) => {
            // Without the exclusions, apps that should see the stock filesystem
            // can observe injected files, so this is surfaced, not swallowed.
            warnings.push(format!("UID exclusions not applied: {error:#}"));
        }
    }

    let record = RuntimeRecord {
        boot_id: current_boot_id(),
        driver_state: probe.state.as_str().to_string(),
        driver_version: probe.version.clone().unwrap_or_default(),
        rules_applied: applied,
        rules_removed: removed,
        verified_path: verified_path.clone(),
        uid_count,
        isolated_blocked,
        error: warnings.first().cloned(),
    };
    if let Err(error) =
        write_runtime_record(std::path::Path::new(crate::defs::NOMOUNT_RUNTIME), &record)
    {
        warnings.push(format!("runtime record not written: {error:#}"));
    }

    log_injection(&plan, &record, &warnings);

    Ok(InjectionResult {
        driver_state: probe.state.as_str().to_string(),
        driver_version: probe.version.unwrap_or_default(),
        applied,
        removed,
        verified_path,
        skipped: plan.skipped.len(),
        uid_count,
        isolated_blocked,
        warnings,
    })
}

fn reconcile_and_verify<T: Transport>(
    driver: &Driver<T>,
    rules: &[Rule],
) -> Result<(usize, usize, Option<String>)> {
    let (applied, removed) = driver.reconcile_rules(rules)?;
    let verified_path = match verify_data_path(rules) {
        Ok(path) => path,
        Err(error) => {
            if let Err(clear_error) = driver.clear_rules() {
                return Err(error.context(format!(
                    "NoMount data-plane verification failed and its rules could not be cleared: \
                     {clear_error:#}"
                )));
            }
            return Err(error.context(
                "NoMount data-plane verification failed; the accepted rules were cleared",
            ));
        }
    };
    Ok((applied, removed, verified_path))
}

/// Probe the runtime driver and load the embedded KMI module when needed.
///
/// This is separate from rule injection so a later mount-provider safety
/// fallback cannot suppress driver loading and diagnostics.
///
/// # Errors
/// Returns an error when a load is needed and fails, or the resulting API is
/// unusable.
#[cfg(target_os = "android")]
pub fn ensure_driver() -> Result<DriverProbe> {
    let (probe, loaded) = probe_or_load()?;
    if let Some(loaded) = loaded {
        log::info!("nomount: driver loaded from {loaded}");
    }
    Ok(probe)
}

/// Append what this boot injected to the shared mount log.
///
/// The log is what a bug report carries: the runtime record holds the numbers,
/// but a report has to show *which* paths went into the kernel, because that is
/// what decides whether an injection can break a vendor kernel's boot.
#[cfg(target_os = "android")]
fn log_injection(plan: &crate::nomount_plan::Plan, record: &RuntimeRecord, warnings: &[String]) {
    let mut body = String::new();
    let _ = writeln!(
        body,
        "--- nomount injection boot={} stage=inject provider=nomount",
        record.boot_id
    );
    let _ = writeln!(
        body,
        "driver={} version={} rules_applied={} rules_removed={} rules_planned={} uids={} isolated={}",
        record.driver_state,
        record.driver_version,
        record.rules_applied,
        record.rules_removed,
        plan.rules.len(),
        record.uid_count,
        record.isolated_blocked
    );
    if let Some(path) = &record.verified_path {
        let _ = writeln!(body, "data_path={path}");
    }
    for (path, count) in summarize_targets(&plan.rules) {
        let _ = writeln!(body, "target={path} rules={count}");
    }
    let _ = writeln!(
        body,
        "dir_replace={}",
        plan.rules
            .iter()
            .filter(
                |rule| !rule.whiteout && plan.directory_replacements.contains(&rule.virtual_path)
            )
            .count()
    );
    for (index, rule) in plan.rules.iter().enumerate() {
        if body.len() >= crate::defs::MOUNT_LOG_MAX_BYTES as usize / 2 {
            let _ = writeln!(body, "rule_details_omitted={}", plan.rules.len() - index);
            break;
        }
        let kind = if rule.whiteout {
            "whiteout"
        } else if plan.directory_replacements.contains(&rule.virtual_path) {
            "dir-replace"
        } else {
            "file"
        };
        let _ = writeln!(
            body,
            "{kind}={} -> {}",
            rule.virtual_path,
            rule.real_path.as_deref().unwrap_or("-")
        );
    }
    for (id, reason) in &plan.skipped {
        let _ = writeln!(body, "skipped_module={id} reason={reason}");
    }
    for warning in warnings {
        let _ = writeln!(body, "warning={warning}");
    }
    if let Err(error) = mount_guard::append_log(
        std::path::Path::new(crate::defs::MOUNT_LOG),
        &body,
        crate::defs::MOUNT_LOG_MAX_BYTES,
    ) {
        log::warn!("nomount: could not write the mount log: {error:#}");
    }
}

/// The directories an injection touches, largest first.
///
/// A rule's virtual path names the file the kernel will redirect, so the parent
/// directory is the directory whose lookups get hooked: that is the unit a
/// device's boot either survives or does not.
#[cfg(target_os = "android")]
fn summarize_targets(rules: &[Rule]) -> Vec<(String, usize)> {
    let mut counts: std::collections::BTreeMap<String, usize> = std::collections::BTreeMap::new();
    for rule in rules {
        let dir = rule
            .virtual_path
            .rsplit_once('/')
            .map_or("/", |(parent, _)| parent);
        let dir = if dir.is_empty() { "/" } else { dir };
        *counts.entry(dir.to_string()).or_default() += 1;
    }
    let mut dirs: Vec<(String, usize)> = counts.into_iter().collect();
    dirs.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
    dirs.truncate(24);
    dirs
}

/// Status reported by `ksud nomount status`.
#[derive(Debug, Clone)]
pub struct Status {
    pub probe: DriverProbe,
    pub expected_rules: usize,
    pub applied_rules: usize,
    /// Everything the kernel holds, including directories it created itself.
    pub kernel_rules: usize,
    /// Rule count per module that contributes rules.
    pub modules: Vec<(String, usize)>,
    /// UIDs the stored policy wants excluded from redirection.
    pub uids: Vec<u32>,
    /// UIDs the kernel is excluding right now.
    pub uids_active: Vec<u32>,
    /// Non-empty when an installed module owns injection instead of us.
    pub deferred_to: Option<String>,
    /// True when this backend is the one injecting right now.
    pub owns_injection: bool,
    pub data_verified: Option<String>,
    pub data_error: Option<String>,
    /// Built-in support or a loadable module; `None` while the API is silent.
    pub driver_kind: Option<DriverKind>,
    /// Last action that changed kernel state, e.g. `clear` or `hot-unload <id>`.
    pub last_action: Option<String>,
    /// Machine-readable reason the driver is unusable, for the manager to
    /// translate: `no_asset_for_kmi`, `outdated`, `not_loaded` or `unknown_kmi`.
    pub unavailable_code: Option<&'static str>,
    /// The KMI or version the code refers to.
    pub unavailable_detail: Option<String>,
    pub uid_count: usize,
    pub isolated_blocked: bool,
    pub error: Option<String>,
}

impl Status {
    /// # Errors
    /// Returns an error when the module tree cannot be walked.
    #[cfg(target_os = "android")]
    pub fn collect() -> Result<Self> {
        let driver = Driver::kernel();
        let probe = driver.probe();
        let plan = collect_plan()?;
        let mut status = Self {
            probe,
            expected_rules: plan.rules.len(),
            applied_rules: 0,
            kernel_rules: 0,
            modules: collect_rule_counts().unwrap_or_default(),
            uids: read_uid_config(std::path::Path::new(crate::defs::NOMOUNT_UIDS))
                .map_or_else(|_| Vec::new(), |config| config.uids),
            uids_active: Vec::new(),
            deferred_to: installed_driver_module(),
            owns_injection: false,
            data_verified: None,
            data_error: None,
            driver_kind: None,
            last_action: read_last_action(std::path::Path::new(crate::defs::NOMOUNT_LAST_ACTION)),
            unavailable_code: None,
            unavailable_detail: None,
            uid_count: 0,
            isolated_blocked: false,
            error: None,
        };
        status.owns_injection = matches!(
            crate::magic_mount::read_mount_mode().unwrap_or(crate::magic_mount::MountMode::Auto),
            crate::magic_mount::MountMode::Nomount
        ) && status.deferred_to.is_none()
            && status.probe.ready();
        if !status.probe.ready() {
            status.error = Some("driver is not ready".to_string());
            if status.deferred_to.is_none() {
                let (code, detail) = unavailable_reason(&status.probe);
                status.unavailable_code = Some(code);
                status.unavailable_detail = detail;
            }
            return Ok(status);
        }
        status.driver_kind = status.probe.ready().then(|| {
            if module_loaded(&std::fs::read_to_string("/proc/modules").unwrap_or_default()) {
                DriverKind::Module
            } else {
                DriverKind::Builtin
            }
        });
        if let Ok(rules) = driver.list_rules() {
            // Report the rules we published separately from the directories the
            // kernel made up for them, so the two never contradict each other.
            status.applied_rules = rules.iter().filter(|rule| !rule.virtual_dir).count();
            status.kernel_rules = rules.len();
            match verify_data_path(&rules) {
                Ok(path) => status.data_verified = path,
                Err(error) => status.data_error = Some(format!("{error:#}")),
            }
        }
        if let Ok(uids) = driver.list_uids() {
            status.uid_count = uids.len();
            status.uids_active = uids;
        }
        status.isolated_blocked = driver.isolated_state().unwrap_or(false);
        Ok(status)
    }

    /// # Errors
    /// Returns an error when writing to stdout fails.
    pub fn print(&self, json: bool) -> Result<()> {
        let stdout = std::io::stdout();
        let mut out = stdout.lock();
        if json {
            let payload = serde_json::json!({
                "schema_version": 2,
                "driver_state": self.probe.state.as_str(),
                "driver_version": self.probe.version,
                "api_ok": self.probe.ready(),
                "rules_expected": self.expected_rules,
                "rules_applied": self.applied_rules,
                "kernel_rules": self.kernel_rules,
                "modules": self
                    .modules
                    .iter()
                    .map(|(id, rules)| serde_json::json!({ "id": id, "rules": rules }))
                    .collect::<Vec<_>>(),
                "uids": self.uids,
                "uids_active": self.uids_active,
                "deferred_to": self.deferred_to,
                "owns_injection": self.owns_injection,
                "data_verified": self.data_verified,
                "data_error": self.data_error,
                "driver_kind": self.driver_kind.map(DriverKind::as_str),
                "last_action": self.last_action,
                "unavailable_code": self.unavailable_code,
                "unavailable_detail": self.unavailable_detail,
                "uid_count": self.uid_count,
                "isolated_blocked": self.isolated_blocked,
                "error": self.error,
            });
            writeln!(out, "{payload}").context("failed to write nomount status")?;
        } else {
            writeln!(out, "schema_version=2").context("failed to write nomount status")?;
            writeln!(out, "driver_state={}", self.probe.state.as_str())
                .context("failed to write nomount status")?;
            writeln!(
                out,
                "driver_version={}",
                self.probe.version.as_deref().unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(out, "api_ok={}", self.probe.ready())
                .context("failed to write nomount status")?;
            writeln!(out, "rules_expected={}", self.expected_rules)
                .context("failed to write nomount status")?;
            writeln!(out, "rules_applied={}", self.applied_rules)
                .context("failed to write nomount status")?;
            writeln!(out, "kernel_rules={}", self.kernel_rules)
                .context("failed to write nomount status")?;
            for (id, rules) in &self.modules {
                writeln!(out, "module={id} rules={rules}")
                    .context("failed to write nomount status")?;
            }
            writeln!(out, "uids={}", join_uids(&self.uids))
                .context("failed to write nomount status")?;
            writeln!(out, "uids_active={}", join_uids(&self.uids_active))
                .context("failed to write nomount status")?;
            writeln!(
                out,
                "deferred_to={}",
                self.deferred_to.as_deref().unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(out, "owns_injection={}", self.owns_injection)
                .context("failed to write nomount status")?;
            writeln!(
                out,
                "data_verified={}",
                self.data_verified.as_deref().unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(
                out,
                "data_error={}",
                self.data_error.as_deref().unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(
                out,
                "driver_kind={}",
                self.driver_kind.map_or("", DriverKind::as_str)
            )
            .context("failed to write nomount status")?;
            writeln!(
                out,
                "last_action={}",
                self.last_action.as_deref().unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(
                out,
                "unavailable_code={}",
                self.unavailable_code.unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(
                out,
                "unavailable_detail={}",
                self.unavailable_detail.as_deref().unwrap_or("")
            )
            .context("failed to write nomount status")?;
            writeln!(out, "uid_count={}", self.uid_count)
                .context("failed to write nomount status")?;
            writeln!(out, "isolated_blocked={}", self.isolated_blocked)
                .context("failed to write nomount status")?;
            writeln!(out, "error={}", self.error.as_deref().unwrap_or(""))
                .context("failed to write nomount status")?;
        }
        out.flush().context("failed to flush nomount status")?;
        Ok(())
    }
}

/// Apply the current module rules, optionally without touching the kernel.
///
/// A dry run reports what the modules ask for; the real run reconciles the
/// kernel state and only reports success when the rules landed.
///
/// # Errors
/// Returns an error when the plan cannot be built or the driver refuses the
/// rules; a refused injection is never reported as success.
#[cfg(target_os = "android")]
pub fn inject(dry_run: bool, json: bool, verbose: bool) -> Result<()> {
    let stdout = std::io::stdout();
    let mut out = stdout.lock();
    if dry_run {
        let plan = collect_plan()?;
        if json {
            let payload = serde_json::json!({
                "schema_version": 1,
                "dry_run": true,
                "rules": plan.rules.len(),
                "skipped": plan.skipped.len(),
            });
            writeln!(out, "{payload}").context("failed to write dry run")?;
        } else {
            writeln!(out, "rules={}", plan.rules.len()).context("failed to write dry run")?;
            for (id, reason) in &plan.skipped {
                writeln!(out, "skipped={id} ({reason})").context("failed to write dry run")?;
            }
            if verbose {
                for rule in &plan.rules {
                    if rule.whiteout {
                        writeln!(out, "whiteout={}", rule.virtual_path)
                    } else {
                        writeln!(
                            out,
                            "{}={} -> {}",
                            if plan.directory_replacements.contains(&rule.virtual_path) {
                                "dir-replace"
                            } else {
                                "file"
                            },
                            rule.virtual_path,
                            rule.real_path.as_deref().unwrap_or("-")
                        )
                    }
                    .context("failed to write dry run")?;
                }
            }
        }
        return out.flush().context("failed to flush dry run");
    }

    let result = apply_current_plan()?;
    log_action("inject");
    if json {
        let payload = serde_json::json!({
            "schema_version": 1,
            "driver_state": result.driver_state,
            "driver_version": result.driver_version,
            "rules_applied": result.applied,
            "rules_removed": result.removed,
            "verified_path": result.verified_path,
            "skipped": result.skipped,
            "uid_count": result.uid_count,
            "isolated_blocked": result.isolated_blocked,
            "warnings": result.warnings,
        });
        writeln!(out, "{payload}").context("failed to write injection result")?;
    } else {
        writeln!(
            out,
            "rules_applied={} rules_removed={} uid_count={}",
            result.applied, result.removed, result.uid_count
        )
        .context("failed to write injection result")?;
        for warning in &result.warnings {
            writeln!(out, "warning={warning}").context("failed to write injection result")?;
        }
    }
    out.flush().context("failed to flush injection result")
}

/// Drop every rule but keep UID exclusions.
///
/// # Errors
/// Returns an error when the kernel refuses the command.
pub fn clear_rules() -> Result<()> {
    let result = Driver::kernel().clear_rules();
    log_action("clear");
    result
}

/// Record a state change next to the runtime record; failures only warn.
fn log_action(action: &str) {
    // Only the Android build has a persistent workspace to write to; host tests
    // exercise the codec and the planner instead.
    #[cfg(target_os = "android")]
    {
        let path = std::path::Path::new(crate::defs::NOMOUNT_LAST_ACTION);
        if let Err(error) = record_action(path, action) {
            log::warn!("nomount: could not record action {action}: {error:#}");
        }
    }
    #[cfg(not(target_os = "android"))]
    let _ = action;
}

/// Print the rules the kernel currently holds.
///
/// # Errors
/// Returns an error when the kernel refuses the command or writing fails.
pub fn print_rules(json: bool) -> Result<()> {
    let driver = Driver::kernel();
    ensure_usable(&driver.probe())?;
    let rules = driver.list_rules()?;
    let stdout = std::io::stdout();
    let mut out = stdout.lock();
    if json {
        let payload = serde_json::json!({
            "schema_version": 1,
            "rules": rules
                .iter()
                .map(|rule| serde_json::json!({
                    "virtual": rule.virtual_path,
                    "real": rule.real_path,
                    "whiteout": rule.whiteout,
                    "virtual_dir": rule.virtual_dir,
                    "opaque": rule.opaque,
                    "uid": rule.uid,
                    "uid_end": rule.uid_end,
                    "deny_start": rule.deny_start,
                    "deny_end": rule.deny_end,
                }))
                .collect::<Vec<_>>(),
        });
        writeln!(out, "{payload}").context("failed to write rules")?;
    } else {
        for rule in &rules {
            if rule.whiteout {
                writeln!(out, "{} (whiteout)", rule.virtual_path)
                    .context("failed to write rules")?;
            } else if rule.virtual_dir {
                writeln!(out, "{} (virtual dir)", rule.virtual_path)
                    .context("failed to write rules")?;
            } else {
                write!(
                    out,
                    "{} -> {}{}",
                    rule.virtual_path,
                    rule.real_path.as_deref().unwrap_or("-"),
                    if rule.opaque { " (opaque)" } else { "" }
                )
                .context("failed to write rules")?;
                if rule.uid != 0 || rule.uid_end != 0 {
                    if rule.uid == rule.uid_end {
                        write!(out, " [UID: {}]", rule.uid)
                    } else {
                        write!(out, " [UID: {}-{}", rule.uid, rule.uid_end)
                    }
                    .context("failed to write rules")?;
                    if rule.deny_start != 0 {
                        write!(out, " excl {}-{}", rule.deny_start, rule.deny_end)
                            .context("failed to write rules")?;
                    }
                    write!(out, "]").context("failed to write rules")?;
                }
                writeln!(out).context("failed to write rules")?;
            }
        }
    }
    out.flush().context("failed to flush rules")
}

/// Make the kernel's UID exclusions match the stored list.
///
/// # Errors
/// Returns an error when the list cannot be read or the kernel refuses a change.
#[cfg(target_os = "android")]
pub fn apply_stored_uids() -> Result<(usize, usize)> {
    let wanted = read_uid_config(std::path::Path::new(crate::defs::NOMOUNT_UIDS))?;
    sync_uids(&Driver::kernel(), &wanted)
}

/// Why the driver cannot be used, as a code the manager can translate.
///
/// The reason matters most on kernels this build does not ship a module for
/// (legacy or custom kernels): falling back to the built-in engine is correct,
/// but the user has to be told which of the cases they are in.
#[cfg(target_os = "android")]
fn unavailable_reason(probe: &DriverProbe) -> (&'static str, Option<String>) {
    match probe.state {
        DriverState::Ready => ("ready", None),
        DriverState::Outdated => ("outdated", probe.version.clone()),
        DriverState::Absent => crate::boot_patch::get_current_kmi().map_or_else(
            |_| ("unknown_kmi", None),
            |kmi| {
                let name = format!("{kmi}{ASSET_SUFFIX}");
                if crate::assets::get_asset_data(&name).is_ok() {
                    ("not_loaded", Some(kmi))
                } else {
                    ("no_asset_for_kmi", Some(kmi))
                }
            },
        ),
    }
}

/// An installed module that drives NoMount itself, if there is one.
///
/// The upstream NoMount distribution is a metamodule that ships its own `nm`
/// tool and LKM. When one of those is installed it owns injection: our backend
/// defers to it instead of becoming a second writer of the same rules, which
/// the kernel would resolve as last-writer-wins.
///
/// The test is deliberately narrow: a metamodule whose id is `nomount`, that
/// ships `bin/nm`, or that carries a NoMount LKM. A metamodule that only mounts
/// files (overlayfs, magic mount) is not one.
#[cfg(target_os = "android")]
#[must_use]
pub fn installed_driver_module() -> Option<String> {
    let path = crate::metamodule::get_metamodule_path()?;
    installed_driver_module_at(&path)
}

#[cfg(any(target_os = "android", test))]
fn installed_driver_module_at(path: &Path) -> Option<String> {
    if path.join(crate::mount_plan::DISABLE_FILE_NAME).exists() {
        return None;
    }
    let id = path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or_default()
        .to_string();
    let ships_tool = path.join("bin/nm").exists();
    let ships_lkm = ["lkm/nomount.ko", "nomount.ko"]
        .iter()
        .any(|candidate| path.join(candidate).exists());
    if id == "nomount" || ships_tool || ships_lkm {
        Some(id)
    } else {
        None
    }
}

/// True when the built-in backend is the one injecting right now.
///
/// Used to block installing a metamodule that would inject on its own while
/// this backend is active: two injectors would fight over the same paths.
///
/// # Errors
/// Returns an error when the mount mode cannot be read.
#[cfg(target_os = "android")]
pub fn owns_injection() -> Result<bool> {
    if !matches!(
        crate::magic_mount::read_mount_mode()?,
        crate::magic_mount::MountMode::Nomount
    ) {
        return Ok(false);
    }
    // An installed module that drives the same driver wins: we are not the
    // injector even though the mode says NoMount.
    if installed_driver_module().is_some() {
        return Ok(false);
    }
    Ok(Driver::kernel().probe().ready())
}

/// Load the embedded NoMount module for the running KMI.
///
/// The loader rewrites vermagic and relocates symbols, so a successful load is
/// not proof of a working driver: callers must probe afterwards.
///
/// A load that never returns cannot be observed from userspace at all — the
/// kernel is already gone when the call would come back — so the attempt is
/// recorded first and later calls refuse to repeat it (see
/// [`reject_unfinished_load`]) instead of hanging the same way again.
///
/// # Errors
/// Returns an error when the KMI cannot be determined, this build carries no
/// module for it, an earlier load is known to have hung, the attempt cannot be
/// recorded, or the loader refuses the module.
#[cfg(target_os = "android")]
pub fn load_embedded_driver() -> Result<String> {
    let attempt = load_attempt_path();
    let _lock = load_lock()?;
    if module_loaded(&std::fs::read_to_string("/proc/modules").unwrap_or_default()) {
        // Something already loaded it, so no load is in flight any more.
        mount_guard::try_clear(attempt)
            .context("nomount: cannot clear the completed load record")?;
        clear_legacy_load_record()?;
        return Ok("already loaded".to_string());
    }
    preserve_legacy_load_record(attempt, Path::new(crate::defs::MOUNT_INFLIGHT))?;
    reject_unfinished_load(attempt)?;
    let kmi = crate::boot_patch::get_current_kmi()
        .context("nomount: cannot determine the running KMI")?;
    let name = format!("{kmi}{ASSET_SUFFIX}");
    let data = crate::assets::get_asset_data(&name)
        .with_context(|| format!("nomount: this build has no module for {kmi}"))?;
    let params = std::ffi::CString::new("").context("nomount: empty module parameters")?;
    let epoch = crate::startup_epoch::current_or_create()?;
    mount_guard::begin(
        attempt,
        mount_guard::Stage::Load,
        "nomount",
        &kmi,
        &epoch.boot_id,
        epoch.generation,
    )
    .context("nomount: cannot record the load attempt")?;
    let loaded = ksuinit::load_module(&data, &params);
    // The call returned, so the kernel survived it and the record has done its
    // job: only a load that hangs leaves it behind for the next boot.
    let cleared = mount_guard::try_clear(attempt)
        .context("nomount: module load returned but its guard could not be cleared");
    loaded.with_context(|| format!("nomount: loading {name} failed"))?;
    cleared?;
    clear_legacy_load_record()?;
    log::info!("nomount: loaded {name}");
    Ok(name)
}

/// Where the in-flight module-load step record lives on a device.
#[cfg(target_os = "android")]
fn load_attempt_path() -> &'static std::path::Path {
    std::path::Path::new(crate::defs::NOMOUNT_LOAD_INFLIGHT)
}

/// Pre-split builds used one file for both loading and rule injection. Only a
/// legacy record for the load stage may suppress another module load.
#[cfg(any(target_os = "android", test))]
fn preserve_legacy_load_record(attempt: &Path, legacy: &Path) -> Result<()> {
    let Some(record) = mount_guard::read(legacy) else {
        return Ok(());
    };
    if record.stage != mount_guard::Stage::Load || mount_guard::read(attempt).is_some() {
        return Ok(());
    }
    mount_guard::begin(
        attempt,
        mount_guard::Stage::Load,
        &record.provider,
        &record.detail,
        &record.boot_id,
        record.generation,
    )
    .context("nomount: cannot preserve the legacy unfinished load record")
}

#[cfg(any(target_os = "android", test))]
fn reject_unfinished_load(attempt: &Path) -> Result<()> {
    let Some(record) = mount_guard::read(attempt) else {
        return Ok(());
    };
    bail!(
        "the {} is unfinished, so this boot keeps the built-in engine instead of loading the module again; \
         select NoMount again, or delete {}, to retry",
        record.describe(),
        attempt.display()
    )
}

#[cfg(target_os = "android")]
fn clear_legacy_load_record() -> Result<()> {
    let path = Path::new(crate::defs::MOUNT_INFLIGHT);
    if mount_guard::read(path).is_some_and(|record| {
        record.stage == mount_guard::Stage::Load && record.provider == "nomount"
    }) {
        mount_guard::try_clear(path)
            .context("nomount: cannot clear the completed legacy load record")?;
    }
    Ok(())
}

#[cfg(target_os = "android")]
pub fn preserve_legacy_load_guard() -> Result<()> {
    preserve_legacy_load_guard_at(
        Path::new(crate::defs::MOUNT_INFLIGHT),
        load_attempt_path(),
        Path::new(crate::defs::NOMOUNT_LOAD_LOCK),
    )
}

#[cfg(any(target_os = "android", test))]
fn preserve_legacy_load_guard_at(legacy: &Path, attempt: &Path, lock_path: &Path) -> Result<()> {
    if !mount_guard::read(legacy).is_some_and(|record| record.stage == mount_guard::Stage::Load)
        || mount_guard::read(attempt).is_some()
    {
        return Ok(());
    }
    let _lock = load_lock_at(lock_path)?;
    preserve_legacy_load_record(attempt, legacy)
}

#[cfg(any(target_os = "android", test))]
fn load_lock_at(path: &Path) -> Result<std::fs::File> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .open(path)?;
    // SAFETY: the returned file owns a valid descriptor for the lock lifetime.
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        bail!("nomount: module loading is already in progress");
    }
    Ok(file)
}

#[cfg(target_os = "android")]
fn load_lock() -> Result<std::fs::File> {
    load_lock_at(Path::new(crate::defs::NOMOUNT_LOAD_LOCK))
}

/// Forget the recorded step.
///
/// Selecting NoMount again is the retry both guards are waiting for, so the
/// record has to go before the next load or injection can run.
#[cfg(target_os = "android")]
pub fn forget_inflight() -> Result<()> {
    forget_inflight_at(
        load_attempt_path(),
        Path::new(crate::defs::MOUNT_INFLIGHT),
        Path::new(crate::defs::NOMOUNT_LOAD_LOCK),
    )
}

#[cfg(any(target_os = "android", test))]
fn forget_inflight_at(attempt: &Path, legacy: &Path, lock_path: &Path) -> Result<()> {
    let _lock = load_lock_at(lock_path)?;
    mount_guard::try_clear(attempt).context("nomount: cannot clear the load guard")?;
    if mount_guard::read(legacy).is_some_and(|record| record.provider == "nomount") {
        mount_guard::try_clear(legacy).context("nomount: cannot clear the injection guard")?;
    }
    Ok(())
}

/// Probe the driver, loading the embedded module first when there is none.
///
/// # Errors
/// Returns an error when a load is needed and fails, or when the driver answers
/// with an unusable API.
#[cfg(target_os = "android")]
pub fn probe_or_load() -> Result<(DriverProbe, Option<String>)> {
    let driver = Driver::kernel();
    let first = driver.probe();
    if first.ready() {
        return Ok((first, None));
    }
    if !should_load(&first) {
        // A registered key type means something already provides the API, and a
        // second registration always fails: report the real reason instead of a
        // confusing load error.
        bail!(
            "nomount: the kernel already provides NoMount (version {}) but this build needs API >= {MIN_API_VERSION}; \
             a built-in driver cannot be replaced at runtime",
            first.version.as_deref().unwrap_or("unknown")
        );
    }
    let loaded = load_embedded_driver()?;
    let second = driver.probe();
    ensure_usable(&second)?;
    Ok((second, Some(loaded)))
}

/// How a `ksud nomount uid` subcommand edits the stored list.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UidEdit {
    Set,
    Add,
    Del,
    Isolated(bool),
}

/// Edit the stored UID list and apply it to the kernel.
///
/// # Errors
/// Returns an error when the stored list cannot be read or written, or when the
/// kernel refuses a change.
#[cfg(target_os = "android")]
pub fn uid_edit(mode: Option<UidEdit>, uids: &[u32], json: bool) -> Result<()> {
    let driver = Driver::kernel();
    let stdout = std::io::stdout();
    let mut out = stdout.lock();
    let Some(edit) = mode else {
        return print_uid_state(&driver, json, &mut out);
    };

    let path = std::path::Path::new(crate::defs::NOMOUNT_UIDS);
    let mut config = read_uid_config(path)?;
    match edit {
        UidEdit::Set => config.uids = uids.to_vec(),
        UidEdit::Add => config.uids.extend_from_slice(uids),
        UidEdit::Del => config.uids.retain(|uid| !uids.contains(uid)),
        UidEdit::Isolated(state) => config.block_isolated = state,
    }
    config.uids.sort_unstable();
    config.uids.dedup();
    write_uid_config(path, &config)?;
    let (added, removed) = sync_uids(&driver, &config)?;
    if json {
        let payload = serde_json::json!({
            "schema_version": 1,
            "stored": config.uids,
            "isolated_blocked": config.block_isolated,
            "added": added,
            "removed": removed,
        });
        writeln!(out, "{payload}").context("failed to write uid result")?;
    } else {
        writeln!(
            out,
            "stored={} isolated_blocked={} added={added} removed={removed}",
            config.uids.len(),
            config.block_isolated
        )
        .context("failed to write uid result")?;
    }
    out.flush().context("failed to flush uid result")
}

#[cfg(target_os = "android")]
fn print_uid_state(
    driver: &Driver<KernelTransport>,
    json: bool,
    out: &mut impl Write,
) -> Result<()> {
    let active = driver.list_uids()?;
    let stored = read_uid_config(std::path::Path::new(crate::defs::NOMOUNT_UIDS))?;
    if json {
        let payload = serde_json::json!({
            "schema_version": 1,
            "active": active,
            "stored": stored.uids,
            "isolated_blocked": driver.isolated_state().unwrap_or(false),
        });
        writeln!(out, "{payload}").context("failed to write uid list")?;
    } else {
        writeln!(out, "active={}", join_uids(&active)).context("failed to write uid list")?;
        writeln!(out, "stored={}", join_uids(&stored.uids)).context("failed to write uid list")?;
    }
    out.flush().context("failed to flush uid list")
}

fn join_uids(uids: &[u32]) -> String {
    uids.iter()
        .map(u32::to_string)
        .collect::<Vec<_>>()
        .join(",")
}

#[cfg(test)]
#[path = "nomount_tests.rs"]
mod tests;
