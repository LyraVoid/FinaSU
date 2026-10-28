use anyhow::{Context, Result, bail};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fmt;
use std::fs::{self, File, OpenOptions, Permissions};
use std::io::Write;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::PathBuf;

const SCHEMA_VERSION: u32 = 1;
const MAX_ENTRIES: usize = 256;
pub const MNT_DETACH: u32 = 2;

const FORBIDDEN_MOUNTPOINTS: &[&str] = &[
    "/",
    "/data",
    "/system",
    "/system_ext",
    "/vendor",
    "/product",
    "/odm",
    "/apex",
    "/proc",
    "/sys",
    "/dev",
    "/storage",
    "/mnt",
    "/sdcard",
    "/data/adb",
    "/metadata",
    "/system_dlkm",
    "/vendor_dlkm",
    "/debug_ramdisk",
    "/linkerconfig",
    "/second_stage_resources",
    "/postinstall",
    "/config",
    "/efs",
];

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Entry {
    pub path: String,
    pub flags: u32,
}

#[derive(Debug, Serialize, Deserialize)]
struct Config {
    version: u32,
    entries: Vec<Entry>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub struct AddOutcome {
    pub configured: bool,
    pub kernel_applied: bool,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct ApplyReport {
    pub applied: usize,
    pub existing: usize,
    pub failed: usize,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PathError {
    NotAbsolute,
    TooShort,
    TooLong,
    TrailingSlash,
    EmptySegment,
    DotSegment,
    ControlCharacter,
    ForbiddenMountpoint,
    InvalidFlags,
}

impl fmt::Display for PathError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::NotAbsolute => "path must be absolute",
            Self::TooShort => "path must name more than the root",
            Self::TooLong => "path exceeds 255 bytes",
            Self::TrailingSlash => "path must not end with '/'",
            Self::EmptySegment => "path must not contain empty segments",
            Self::DotSegment => "path must not contain '.' or '..' segments",
            Self::ControlCharacter => "path must not contain control characters",
            Self::ForbiddenMountpoint => "path is a protected system mountpoint",
            Self::InvalidFlags => "only MNT_DETACH (2) or flags 0 are accepted",
        })
    }
}

impl std::error::Error for PathError {}

pub fn validate_path(path: &str) -> std::result::Result<(), PathError> {
    if !path.starts_with('/') {
        return Err(PathError::NotAbsolute);
    }
    if path.len() > 255 {
        return Err(PathError::TooLong);
    }
    if path == "/" {
        return Err(PathError::TooShort);
    }
    if path.ends_with('/') {
        return Err(PathError::TrailingSlash);
    }
    if path.chars().any(char::is_control) {
        return Err(PathError::ControlCharacter);
    }
    if FORBIDDEN_MOUNTPOINTS.contains(&path) {
        return Err(PathError::ForbiddenMountpoint);
    }
    for segment in path[1..].split('/') {
        if segment.is_empty() {
            return Err(PathError::EmptySegment);
        }
        if matches!(segment, "." | "..") {
            return Err(PathError::DotSegment);
        }
    }
    Ok(())
}

pub fn validate_entry(entry: &Entry) -> std::result::Result<(), PathError> {
    validate_path(&entry.path)?;
    if !matches!(entry.flags, 0 | MNT_DETACH) {
        return Err(PathError::InvalidFlags);
    }
    Ok(())
}

struct ConfigStore {
    path: PathBuf,
    lock_path: PathBuf,
}

impl ConfigStore {
    fn new(path: PathBuf) -> Self {
        let lock_path = path.with_extension("lock");
        Self { path, lock_path }
    }

    #[cfg(target_os = "android")]
    fn default_path() -> Self {
        Self::new(PathBuf::from(crate::defs::UMOUNT_CONFIG_PATH))
    }

    fn lock(&self) -> Result<File> {
        let parent = self.path.parent().context("umount config has no parent")?;
        fs::create_dir_all(parent)
            .with_context(|| format!("create umount config directory {}", parent.display()))?;
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .open(&self.lock_path)
            .with_context(|| format!("open umount config lock {}", self.lock_path.display()))?;
        rustix::fs::flock(&file, rustix::fs::FlockOperation::LockExclusive)
            .context("lock umount config")?;
        Ok(file)
    }

    fn read(&self) -> Result<Vec<Entry>> {
        let content = match fs::read(&self.path) {
            Ok(content) => content,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
            Err(error) => {
                return Err(error)
                    .with_context(|| format!("read umount config {}", self.path.display()));
            }
        };
        let config: Config = serde_json::from_slice(&content)
            .with_context(|| format!("parse umount config {}", self.path.display()))?;
        if config.version != SCHEMA_VERSION {
            bail!(
                "unsupported umount config schema {} (expected {})",
                config.version,
                SCHEMA_VERSION
            );
        }
        if config.entries.len() > MAX_ENTRIES {
            bail!("umount config contains more than {MAX_ENTRIES} entries");
        }
        for entry in &config.entries {
            validate_entry(entry)
                .map_err(|error| anyhow::anyhow!("invalid persisted umount entry: {error}"))?;
        }
        Ok(config.entries)
    }

    fn write(&self, entries: Vec<Entry>) -> Result<()> {
        let parent = self.path.parent().context("umount config has no parent")?;
        fs::create_dir_all(parent)
            .with_context(|| format!("create umount config directory {}", parent.display()))?;

        if entries.len() > MAX_ENTRIES {
            bail!("umount config cannot exceed {MAX_ENTRIES} entries");
        }
        let mut by_path = BTreeMap::new();
        for entry in entries {
            validate_entry(&entry).map_err(|error| anyhow::anyhow!("{error}"))?;
            by_path.insert(entry.path, entry.flags);
        }
        let config = Config {
            version: SCHEMA_VERSION,
            entries: by_path
                .into_iter()
                .map(|(path, flags)| Entry { path, flags })
                .collect(),
        };

        let mut temp = tempfile::Builder::new()
            .prefix(".umount.config.")
            .tempfile_in(parent)
            .context("create temporary umount config")?;
        temp.as_file()
            .set_permissions(Permissions::from_mode(0o600))
            .context("set umount config permissions")?;
        serde_json::to_writer_pretty(temp.as_file_mut(), &config)
            .context("serialize umount config")?;
        temp.write_all(b"\n").context("finish umount config")?;
        temp.as_file_mut()
            .sync_all()
            .context("sync temporary umount config")?;
        temp.persist(&self.path)
            .map_err(|error| error.error)
            .with_context(|| format!("replace umount config {}", self.path.display()))?;
        File::open(parent)
            .and_then(|directory| directory.sync_all())
            .context("sync umount config directory")?;
        Ok(())
    }

    fn list(&self) -> Result<Vec<Entry>> {
        self.read()
    }

    fn add(&self, path: &str, flags: u32) -> Result<bool> {
        validate_entry(&Entry {
            path: path.to_string(),
            flags,
        })
        .map_err(|error| anyhow::anyhow!("{error}"))?;

        let _lock = self.lock()?;
        let mut entries = self.read()?;
        if entries
            .iter()
            .any(|entry| entry.path == path && entry.flags == flags)
        {
            return Ok(false);
        }
        if entries.len() >= MAX_ENTRIES && !entries.iter().any(|entry| entry.path == path) {
            bail!("umount config cannot exceed {MAX_ENTRIES} entries");
        }
        entries.retain(|entry| entry.path != path);
        entries.push(Entry {
            path: path.to_string(),
            flags,
        });
        self.write(entries)?;
        Ok(true)
    }

    fn remove(&self, path: &str) -> Result<bool> {
        validate_path(path).map_err(|error| anyhow::anyhow!("{error}"))?;
        let _lock = self.lock()?;
        let mut entries = self.read()?;
        let len = entries.len();
        entries.retain(|entry| entry.path != path);
        if entries.len() == len {
            return Ok(false);
        }
        self.write(entries)?;
        Ok(true)
    }

    fn clear(&self) -> Result<()> {
        let _lock = self.lock()?;
        let entries = self.read()?;
        if entries.is_empty() && !self.path.exists() {
            return Ok(());
        }
        self.write(Vec::new())
    }
}

trait UmountKernel {
    fn add(&self, path: &str, flags: u32) -> Result<bool>;
}

#[cfg(target_os = "android")]
struct AndroidUmountKernel;

#[cfg(target_os = "android")]
impl UmountKernel for AndroidUmountKernel {
    fn add(&self, path: &str, flags: u32) -> Result<bool> {
        match crate::ksucalls::umount_list_add(path, flags) {
            Ok(()) => Ok(true),
            Err(error) if crate::ksucalls::is_errno(&error, libc::EEXIST) => Ok(false),
            Err(error) => Err(error),
        }
    }
}

fn apply_with<K: UmountKernel>(store: &ConfigStore, kernel: &K) -> Result<ApplyReport> {
    let entries = store.list()?;
    let mut report = ApplyReport::default();

    for entry in entries {
        if let Err(error) = validate_entry(&entry) {
            report.failed += 1;
            log::warn!("skip invalid persisted umount entry: {error}");
            continue;
        }
        match kernel.add(&entry.path, entry.flags) {
            Ok(true) => report.applied += 1,
            Ok(false) => report.existing += 1,
            Err(error) => {
                report.failed += 1;
                log::warn!("apply umount entry failed: {error:#}");
            }
        }
    }
    Ok(report)
}

#[cfg(target_os = "android")]
pub fn list() -> Result<Vec<Entry>> {
    ConfigStore::default_path().list()
}

#[cfg(target_os = "android")]
pub fn add(path: &str, flags: u32) -> Result<AddOutcome> {
    let store = ConfigStore::default_path();
    let configured = store.add(path, flags)?;
    match AndroidUmountKernel.add(path, flags) {
        Ok(kernel_applied) => Ok(AddOutcome {
            configured,
            kernel_applied,
        }),
        Err(error) => Err(error).with_context(
            || "configuration was saved, but applying it to the running kernel failed",
        ),
    }
}

#[cfg(target_os = "android")]
pub fn remove(path: &str) -> Result<bool> {
    ConfigStore::default_path().remove(path)
}

#[cfg(target_os = "android")]
pub fn clear() -> Result<()> {
    ConfigStore::default_path().clear()
}

#[cfg(target_os = "android")]
pub fn apply() -> Result<ApplyReport> {
    apply_with(&ConfigStore::default_path(), &AndroidUmountKernel)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    fn store() -> (tempfile::TempDir, ConfigStore) {
        let directory = tempfile::tempdir().unwrap();
        let store = ConfigStore::new(directory.path().join("umount.config.json"));
        (directory, store)
    }

    #[test]
    fn config_round_trip_is_sorted_and_private() {
        let (_directory, store) = store();
        store
            .add("/system/etc/security/cacerts/example.0", MNT_DETACH)
            .unwrap();
        store
            .add("/apex/com.android.conscrypt/cacerts/example.0", 0)
            .unwrap();

        let entries = store.list().unwrap();
        assert_eq!(entries.len(), 2);
        assert_eq!(
            entries[0].path,
            "/apex/com.android.conscrypt/cacerts/example.0"
        );
        assert_eq!(entries[1].flags, MNT_DETACH);
        assert_eq!(
            fs::metadata(&store.path).unwrap().permissions().mode() & 0o777,
            0o600
        );
        assert!(
            fs::read_dir(store.path.parent().unwrap())
                .unwrap()
                .all(|entry| !entry
                    .unwrap()
                    .file_name()
                    .to_string_lossy()
                    .contains(".umount.config."))
        );
    }

    #[test]
    fn corrupt_config_is_preserved_and_never_rebuilt() {
        let (_directory, store) = store();
        let original = b"{ this is not json";
        fs::write(&store.path, original).unwrap();

        assert!(store.list().is_err());
        assert!(store.add("/data/local/tmp/ca", MNT_DETACH).is_err());
        assert_eq!(fs::read(&store.path).unwrap(), original);
    }

    #[test]
    fn add_and_remove_are_idempotent() {
        let (_directory, store) = store();
        assert!(store.add("/data/local/tmp/ca", MNT_DETACH).unwrap());
        assert!(!store.add("/data/local/tmp/ca", MNT_DETACH).unwrap());
        assert!(store.remove("/data/local/tmp/ca").unwrap());
        assert!(!store.remove("/data/local/tmp/ca").unwrap());
    }

    #[test]
    fn add_replaces_flags_without_duplicate_path() {
        let (_directory, store) = store();
        store.add("/data/local/tmp/ca", MNT_DETACH).unwrap();
        store.add("/data/local/tmp/ca", 0).unwrap();
        assert_eq!(
            store.list().unwrap(),
            vec![Entry {
                path: "/data/local/tmp/ca".into(),
                flags: 0
            }]
        );
    }

    #[test]
    fn clear_keeps_a_valid_empty_document() {
        let (_directory, store) = store();
        store.add("/data/local/tmp/ca", MNT_DETACH).unwrap();
        store.clear().unwrap();
        assert!(store.list().unwrap().is_empty());
        let content = fs::read_to_string(&store.path).unwrap();
        assert!(content.contains("\"version\": 1"));
        assert!(content.contains("\"entries\": []"));
    }

    #[test]
    fn rejects_an_oversized_config_without_writing_it() {
        let (_directory, store) = store();
        let entries = (0..=MAX_ENTRIES)
            .map(|index| Entry {
                path: format!("/data/local/tmp/{index}"),
                flags: MNT_DETACH,
            })
            .collect();
        assert!(store.write(entries).is_err());
        assert!(!store.path.exists());
    }

    struct TestKernel {
        calls: AtomicUsize,
        existing: bool,
        fail_path: &'static str,
    }

    impl UmountKernel for TestKernel {
        fn add(&self, path: &str, _flags: u32) -> Result<bool> {
            self.calls.fetch_add(1, Ordering::Relaxed);
            if path == self.fail_path {
                bail!("injected failure");
            }
            Ok(!self.existing)
        }
    }

    #[test]
    fn apply_continues_after_one_entry_fails() {
        let (_directory, store) = store();
        store.add("/a/mount", MNT_DETACH).unwrap();
        store.add("/b/mount", MNT_DETACH).unwrap();
        store.add("/c/mount", MNT_DETACH).unwrap();
        let kernel = TestKernel {
            calls: AtomicUsize::new(0),
            existing: false,
            fail_path: "/b/mount",
        };

        let report = apply_with(&store, &kernel).unwrap();
        assert_eq!(
            report,
            ApplyReport {
                applied: 2,
                existing: 0,
                failed: 1
            }
        );
        assert_eq!(kernel.calls.load(Ordering::Relaxed), 3);
    }

    #[test]
    fn existing_kernel_entry_counts_as_success() {
        let (_directory, store) = store();
        store.add("/a/mount", MNT_DETACH).unwrap();
        let kernel = TestKernel {
            calls: AtomicUsize::new(0),
            existing: true,
            fail_path: "/never",
        };

        assert_eq!(
            apply_with(&store, &kernel).unwrap(),
            ApplyReport {
                applied: 0,
                existing: 1,
                failed: 0
            }
        );
    }

    #[test]
    fn path_validation_covers_all_boundaries() {
        for valid in [
            "/a",
            "/system/etc/security/cacerts/example.0",
            "/apex/com.android.conscrypt/cacerts/example.0",
            "/data/local/tmp/a b",
        ] {
            assert_eq!(validate_path(valid), Ok(()), "{valid}");
        }
        for (path, expected) in [
            ("relative", PathError::NotAbsolute),
            ("/", PathError::TooShort),
            ("/a/", PathError::TrailingSlash),
            ("/a//b", PathError::EmptySegment),
            ("/a/./b", PathError::DotSegment),
            ("/a/../b", PathError::DotSegment),
            ("/a/\n/b", PathError::ControlCharacter),
            ("/system", PathError::ForbiddenMountpoint),
            ("/apex", PathError::ForbiddenMountpoint),
        ] {
            assert_eq!(validate_path(path), Err(expected), "{path:?}");
        }
        assert_eq!(
            validate_path(&format!("/{}", "a".repeat(255))),
            Err(PathError::TooLong)
        );
        assert_eq!(
            validate_entry(&Entry {
                path: "/a".into(),
                flags: libc::MNT_FORCE as u32
            }),
            Err(PathError::InvalidFlags)
        );
    }
}
