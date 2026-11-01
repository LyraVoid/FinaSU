//! Immutable, content-addressed engine installation. Never replaces a live engine.

use anyhow::{Context, Result, ensure};
use std::fs::{self, File};
use std::io::Write;
use std::os::unix::fs::{PermissionsExt, symlink};
use std::path::Path;

pub const FILES: [&str; 12] = [
    "bin/zygisk-ptrace64",
    "bin/zygiskd64",
    "lib64/libzygisk.so",
    "bin/zygisk-ptrace32",
    "bin/zygiskd32",
    "lib/libzygisk.so",
    "LICENSE",
    "NOTICE",
    "UPSTREAM.md",
    "AGPL-3.0.txt",
    "licenses/CSOLoader/LICENSE",
    "licenses/PLTI/LICENSE",
];

fn mode(name: &str) -> u32 {
    if name.starts_with("bin/") {
        0o755
    } else {
        0o644
    }
}

pub fn asset_version(files: &[(String, Vec<u8>)]) -> Result<String> {
    ensure!(files.len() == FILES.len(), "Incomplete FolkZygisk assets");
    let mut identity = Vec::new();
    for (expected, (name, bytes)) in FILES.iter().zip(files) {
        ensure!(
            name == expected && !bytes.is_empty(),
            "Invalid FolkZygisk asset: {name}"
        );
        identity.extend_from_slice(name.as_bytes());
        identity.push(0);
        identity.extend_from_slice(&(bytes.len() as u64).to_le_bytes());
        identity.extend_from_slice(bytes);
    }
    Ok(sha256::digest(&identity))
}

pub fn installed_version(parent: &Path) -> Result<Option<String>> {
    let current = parent.join("folkzygisk");
    let target = match fs::read_link(&current) {
        Ok(target) => target,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error.into()),
    };
    ensure!(
        target.parent() == Some(Path::new("folkzygisk-versions")),
        "Unsafe FolkZygisk engine link"
    );
    let version = target
        .file_name()
        .and_then(|name| name.to_str())
        .context("Invalid FolkZygisk engine version")?;
    Ok(Some(version.to_owned()))
}

pub fn install(
    parent: &Path,
    files: &[(String, Vec<u8>)],
    prepare: impl Fn(&Path) -> Result<()>,
) -> Result<String> {
    let version = asset_version(files)?;
    fs::create_dir_all(parent)?;
    let current = parent.join("folkzygisk");
    match fs::symlink_metadata(&current) {
        Ok(metadata) => ensure!(
            metadata.file_type().is_symlink(),
            "Refusing to replace legacy FolkZygisk directory"
        ),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }
    let versions = parent.join("folkzygisk-versions");
    fs::create_dir_all(&versions)?;
    ensure!(
        fs::symlink_metadata(&versions)?.is_dir(),
        "Unsafe engine version directory"
    );
    let destination = versions.join(&version);
    if destination.try_exists()? {
        ensure!(
            fs::symlink_metadata(&destination)?.is_dir(),
            "Unsafe existing engine version"
        );
        for (name, bytes) in files {
            let path = destination.join(name);
            let metadata = fs::symlink_metadata(&path)?;
            ensure!(
                metadata.is_file()
                    && metadata.permissions().mode() & 0o777 == mode(name)
                    && fs::read(&path)? == *bytes,
                "Changed engine asset: {name}"
            );
        }
    } else {
        let staging = tempfile::tempdir_in(&versions)?;
        for (name, bytes) in files {
            let path = staging.path().join(name);
            fs::create_dir_all(path.parent().context("Asset parent")?)?;
            let mut file = File::create(&path)?;
            file.set_permissions(fs::Permissions::from_mode(mode(name)))?;
            file.write_all(bytes)?;
            file.sync_all()?;
        }
        fs::set_permissions(staging.path(), fs::Permissions::from_mode(0o755))?;
        prepare(staging.path())?;
        for directory in [
            "bin",
            "lib",
            "lib64",
            "licenses/CSOLoader",
            "licenses/PLTI",
            "licenses",
        ] {
            File::open(staging.path().join(directory))?.sync_all()?;
        }
        File::open(staging.path())?.sync_all()?;
        fs::rename(staging.path(), &destination)?;
        File::open(&versions)?.sync_all()?;
    }
    prepare(&destination)?;
    // Publish only after every file is complete. Old versions remain untouched.
    let staging = tempfile::tempdir_in(parent)?;
    let link = staging.path().join("current");
    symlink(Path::new("folkzygisk-versions").join(&version), &link)?;
    fs::rename(link, current)?;
    File::open(parent)?.sync_all()?;
    Ok(version)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn assets(value: u8) -> Vec<(String, Vec<u8>)> {
        FILES
            .iter()
            .map(|name| ((*name).into(), vec![value]))
            .collect()
    }

    #[test]
    fn versions_are_immutable_and_switch_is_explicit() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let first = install(dir.path(), &assets(1), |_| Ok(()))?;
        assert_eq!(first, asset_version(&assets(1))?);
        assert_eq!(first, install(dir.path(), &assets(1), |_| Ok(()))?);
        assert_eq!(installed_version(dir.path())?, Some(first.clone()));
        let second = install(dir.path(), &assets(2), |_| Ok(()))?;
        assert_ne!(first, second);
        assert_eq!(installed_version(dir.path())?, Some(second));
        assert_eq!(
            fs::read(dir.path().join("folkzygisk/bin/zygiskd64"))?,
            vec![2]
        );
        assert_eq!(
            fs::read(
                dir.path()
                    .join("folkzygisk-versions")
                    .join(&first)
                    .join("bin/zygiskd64")
            )?,
            vec![1]
        );
        fs::write(dir.path().join("folkzygisk/bin/zygiskd64"), [3])?;
        assert!(install(dir.path(), &assets(2), |_| Ok(())).is_err());
        Ok(())
    }

    #[test]
    fn incomplete_assets_and_legacy_directory_do_not_publish() -> Result<()> {
        let dir = tempfile::tempdir()?;
        assert!(install(dir.path(), &[], |_| Ok(())).is_err());
        assert!(!dir.path().join("folkzygisk").exists());
        fs::create_dir(dir.path().join("folkzygisk"))?;
        assert!(install(dir.path(), &assets(1), |_| Ok(())).is_err());
        assert!(dir.path().join("folkzygisk").is_dir());
        Ok(())
    }

    #[test]
    fn preparation_failure_leaves_selection_unchanged() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let first = install(dir.path(), &assets(1), |_| Ok(()))?;
        assert!(install(dir.path(), &assets(2), |_| anyhow::bail!("label failure")).is_err());
        assert_eq!(
            fs::read_link(dir.path().join("folkzygisk"))?,
            Path::new("folkzygisk-versions").join(first)
        );
        Ok(())
    }
}
