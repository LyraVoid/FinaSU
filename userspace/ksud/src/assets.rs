use anyhow::Result;
use rust_embed::RustEmbed;

#[cfg(target_os = "android")]
mod android {
    use crate::assets::Asset;
    use crate::defs::BINARY_DIR;
    use crate::utils::ensure_binary;
    use const_format::concatcp;
    use std::path::Path;

    pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
    pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
    pub const BOOTCTL_PATH: &str = concatcp!(BINARY_DIR, "bootctl");

    pub fn ensure_binaries(ignore_if_exist: bool) -> anyhow::Result<()> {
        for file in Asset::iter() {
            if file == "ksuinit"
                || file == "waitsys"
                || file.ends_with(".ko")
                || file.starts_with("folkzygisk/")
                || file.starts_with("sui/")
            {
                // don't extract internal executables and kernel modules
                continue;
            }
            let path = format!("{BINARY_DIR}{file}");
            if ignore_if_exist && Path::new(&path).exists() {
                continue;
            }
            let asset =
                Asset::get(&file).ok_or_else(|| anyhow::anyhow!("asset not found: {file}"))?;
            ensure_binary(path, &asset.data, ignore_if_exist)?;
        }

        // Create resetprop -> ksud symlink (resetprop is now built into ksud)
        let resetprop_link = RESETPROP_PATH;
        let _ = std::fs::remove_file(resetprop_link);
        std::os::unix::fs::symlink("/data/adb/ksud", resetprop_link)?;

        Ok(())
    }
}

#[cfg(target_os = "android")]
pub use android::*;

#[cfg(all(target_arch = "x86_64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/x86_64"]
struct Asset;

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/aarch64"]
struct Asset;

#[cfg(all(target_arch = "riscv64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/riscv64"]
struct Asset;

// If not Android, ie. macos, linux, windows, include all architectures.
#[cfg(not(target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin"]
struct Asset;

#[allow(unused)]
pub fn get_asset_data(name: &str) -> Result<std::borrow::Cow<'static, [u8]>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(asset.data)
}

pub fn get_asset(name: &str) -> Result<Box<dyn AsRef<[u8]>>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(Box::new(asset.data))
}

pub fn sui_payload() -> anyhow::Result<Vec<(String, Vec<u8>)>> {
    let mut files = Vec::new();
    for name in Asset::iter() {
        let Some(relative) = name.strip_prefix("sui/") else {
            continue;
        };
        let asset = Asset::get(&name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
        files.push((relative.to_owned(), asset.data.into_owned()));
    }
    anyhow::ensure!(
        !files.is_empty(),
        "no embedded Sui payload for this architecture"
    );
    files.sort();
    Ok(files)
}

pub fn list_supported_kmi() -> std::vec::Vec<std::string::String> {
    let mut list = Vec::new();
    for file in Asset::iter() {
        // kmi_name = "xxx_kernelsu.ko"
        if let Some(kmi) = file.strip_suffix("_kernelsu.ko") {
            list.push(kmi.to_string());
        }
    }
    list
}

/// KMI names that carry a vendored NoMount module (`<kmi>_nomount.ko`).
///
/// This is a subset of [`list_supported_kmi`]: a KMI can ship `kernelsu.ko`
/// without a matching NoMount module, in which case the built-in NoMount
/// backend must report the driver as unavailable instead of guessing.
// Kept alongside the KMI asset discovery so the built-in NoMount backend lands
// on one source of truth; wired up by the `ksud nomount` commands.
#[allow(dead_code)]
pub fn list_supported_nomount_kmi() -> std::vec::Vec<std::string::String> {
    let mut list: Vec<String> = Asset::iter()
        .filter_map(|file| file.strip_suffix("_nomount.ko").map(ToString::to_string))
        .collect();
    list.sort();
    list
}
