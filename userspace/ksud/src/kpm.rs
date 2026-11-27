//! KernelPatch Module (KPM) support, built into `ksud`.
//!
//! KernelPatch is the kernel-side loader KPM files run on: it is not a root
//! solution and it is not a module we install. The kernel has to carry it, so
//! `boot-patch --kpm` patches the kernel image with the bundled `kptools` and
//! `kpimg` (KPatch-Next 0.13.5-2), and from then on `kpatch` can load `.kpm`
//! files at runtime.
//!
//! This replaces the KPatch-Next KernelSU module: the same three binaries and
//! the same `/data/adb/kp-next` layout, but no module to install and no WebUI.
//! Files in [`KPM_DIR`] are loaded at the service stage, like the module's
//! `service.sh` did.

use anyhow::{Context, Result, bail};
use serde::Serialize;
use std::path::{Path, PathBuf};
use std::process::Command as ProcCommand;

/// Where KPM files live. Same path the KPatch-Next module used, so an existing
/// `/data/adb/kp-next` survives switching to the built-in manager.
pub const KPM_DIR: &str = "/data/adb/kp-next/kpm";

/// The string `kpatch hello` prints when the running kernel carries
/// KernelPatch. KPatch-Next 0.13.x ships it.
const HELLO: &str = "hello2026";

#[derive(clap::Subcommand, Debug)]
pub enum Command {
    /// Whether this kernel can load KPMs, and how many are loaded
    Status,
    /// Names of the KPMs loaded right now
    List,
    /// Copy a .kpm into /data/adb/kp-next/kpm and load it
    Install { path: PathBuf },
    /// Load a .kpm without copying it; it is lost after a reboot
    Load { path: PathBuf },
    /// Unload a loaded KPM
    Unload { name: String },
    /// Unload and delete a stored KPM
    Remove { name: String },
}

#[derive(Serialize)]
pub struct Status {
    /// The running kernel carries KernelPatch (otherwise nothing here can work)
    pub patched: bool,
    /// Number of KPMs the kernel reports as loaded
    pub loaded: u32,
    /// Files stored for the next boot
    pub stored: Vec<String>,
}

fn binary(name: &str) -> Result<PathBuf> {
    #[cfg(target_os = "android")]
    {
        // ensure_binaries() writes kpatch/kptools/kpimg into the binary dir and
        // chmods them; it skips .ko, ksuinit and the Sui/FolkZygisk trees.
        crate::assets::ensure_binaries(false).context("Extract the KernelPatch tools")?;
        Ok(PathBuf::from(format!("{}{name}", crate::defs::BINARY_DIR)))
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = name;
        bail!("KPM management is only available on Android")
    }
}

fn exec(bin: &str, args: &[&str]) -> Result<String> {
    let out = ProcCommand::new(binary(bin)?)
        .args(args)
        .output()
        .with_context(|| format!("Run {bin} {}", args.join(" ")))?;
    let stdout = String::from_utf8_lossy(&out.stdout).trim().to_owned();
    if !out.status.success() {
        let stderr = String::from_utf8_lossy(&out.stderr).trim().to_owned();
        bail!("{bin} {} failed: {stderr}", args.join(" "));
    }
    Ok(stdout)
}

fn kpatch(args: &[&str]) -> Result<String> {
    exec("kpatch", args)
}

/// True when the running kernel has KernelPatch; `kpatch hello` echoes
/// [`HELLO`] only then. Every other command would fail with a plain syscall
/// error, so check once and say so.
pub fn patched() -> Result<bool> {
    Ok(kpatch(&["hello"])?.contains(HELLO))
}

fn stored() -> Result<Vec<String>> {
    let dir = Path::new(KPM_DIR);
    if !dir.exists() {
        return Ok(Vec::new());
    }
    let mut names = Vec::new();
    for entry in std::fs::read_dir(dir).with_context(|| format!("Read {}", dir.display()))? {
        let path = entry?.path();
        if path.extension().is_some_and(|ext| ext == "kpm") {
            if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                names.push(name.to_owned());
            }
        }
    }
    names.sort();
    Ok(names)
}

pub fn status() -> Result<Status> {
    if !patched()? {
        return Ok(Status {
            patched: false,
            loaded: 0,
            stored: stored()?,
        });
    }
    let loaded = kpatch(&["kpm", "num"])?
        .trim()
        .parse::<u32>()
        .context("kpatch kpm num did not report a number")?;
    Ok(Status {
        patched: true,
        loaded,
        stored: stored()?,
    })
}

pub fn list() -> Result<String> {
    if !patched()? {
        bail!("This kernel has no KernelPatch; patch the boot image with `ksud boot-patch --kpm` first");
    }
    kpatch(&["kpm", "list"])
}

fn validate_name(name: &str) -> Result<()> {
    if name.is_empty()
        || name.len() > 64
        || !name
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, b'_' | b'-' | b'.'))
    {
        bail!("KPM name must be 1-64 characters of letters, digits, '_', '-' or '.'");
    }
    Ok(())
}

pub fn load(path: &Path) -> Result<String> {
    if !path.is_file() {
        bail!("{} does not exist", path.display());
    }
    if path.extension().is_none_or(|ext| ext != "kpm") {
        bail!("{} is not a .kpm file", path.display());
    }
    if !patched()? {
        bail!("This kernel has no KernelPatch; patch the boot image with `ksud boot-patch --kpm` first");
    }
    kpatch(&["kpm", "load", &path.to_string_lossy()])
}

pub fn install(path: &Path) -> Result<String> {
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .context("KPM path has no file name")?;
    validate_name(name.trim_end_matches(".kpm"))?;
    let target_dir = Path::new(KPM_DIR);
    std::fs::create_dir_all(target_dir).context("Create the KPM directory")?;
    let target = target_dir.join(name);
    std::fs::copy(path, &target)
        .with_context(|| format!("Copy {} to {}", path.display(), target.display()))?;
    let result = load(&target);
    if result.is_err() {
        // Do not keep something that cannot load; it would only fail every boot.
        let _ = std::fs::remove_file(&target);
    }
    result
}

pub fn unload(name: &str) -> Result<String> {
    validate_name(name)?;
    if !patched()? {
        bail!("This kernel has no KernelPatch; patch the boot image with `ksud boot-patch --kpm` first");
    }
    kpatch(&["kpm", "unload", name])
}

pub fn remove(name: &str) -> Result<String> {
    validate_name(name.trim_end_matches(".kpm"))?;
    let file = Path::new(KPM_DIR).join(format!("{}.kpm", name.trim_end_matches(".kpm")));
    let loaded = if patched()? {
        kpatch(&["kpm", "unload", name]).unwrap_or_default()
    } else {
        String::new()
    };
    match std::fs::remove_file(&file) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).with_context(|| format!("Remove {}", file.display())),
    }
    Ok(loaded)
}

/// Load everything in [`KPM_DIR`] at boot, like the KPatch-Next service script.
/// A kernel without KernelPatch is not an error: the user simply does not use
/// KPM, and boot must not be noisy about it.
pub fn boot_load() {
    #[cfg(target_os = "android")]
    {
        let dir = Path::new(KPM_DIR);
        if !dir.exists() {
            return;
        }
        let Ok(is_patched) = patched() else {
            log::warn!("KPM: kpatch is unavailable");
            return;
        };
        if !is_patched {
            log::warn!("KPM files exist but this kernel has no KernelPatch");
            return;
        }
        let Ok(entries) = stored() else {
            log::warn!("KPM: cannot read {KPM_DIR}");
            return;
        };
        for name in entries {
            let path = dir.join(&name);
            match kpatch(&["kpm", "load", &path.to_string_lossy()]) {
                Ok(_) => log::info!("KPM loaded: {name}"),
                Err(error) => log::warn!("KPM {name} failed to load: {error:#}"),
            }
        }
    }
}

/// Patch `kernel` with the bundled `kpimg` and return the patched image.
///
/// This is the step that makes KPM possible at all: `kptools` injects
/// KernelPatch into the kernel binary, exactly like APatch's on-device patcher.
/// It refuses a kernel without `CONFIG_KALLSYMS_ALL`, because KernelPatch
/// resolves symbols by name at runtime.
pub fn patch_kernel(kernel: &[u8]) -> Result<Vec<u8>> {
    let kptools = binary("kptools")?;
    let kpimg = binary("kpimg")?;
    if !kpimg.is_file() {
        bail!("The bundled kpimg is missing");
    }

    let dir = std::env::temp_dir().join(format!("ksud-kpm-{}", std::process::id()));
    std::fs::create_dir_all(&dir).context("Create the KPM patch directory")?;
    let input = dir.join("kernel.ori");
    let output = dir.join("kernel");
    let result = (|| -> Result<Vec<u8>> {
        std::fs::write(&input, kernel).context("Write the kernel copy")?;

        let info = exec(
            "kptools",
            &["-i", &input.to_string_lossy(), "-f"],
        )
        .unwrap_or_default();
        if !info.contains("CONFIG_KALLSYMS_ALL=y") {
            bail!("This kernel lacks CONFIG_KALLSYMS_ALL, which KernelPatch needs");
        }
        if info.contains("CONFIG_KPM=y") {
            bail!("This kernel already has KPM built in");
        }

        exec(
            "kptools",
            &[
                "-p",
                "-i",
                &input.to_string_lossy(),
                "-k",
                &kpimg.to_string_lossy(),
                "-o",
                &output.to_string_lossy(),
            ],
        )
        .context("Inject KernelPatch into the kernel")?;
        std::fs::read(&output).context("Read the patched kernel")
    })();
    let _ = std::fs::remove_dir_all(&dir);
    let _ = kptools;
    result
}

pub fn run(command: &Command) -> Result<()> {
    match command {
        Command::Status => println!("{}", serde_json::to_string(&status()?)?),
        Command::List => {
            let names = list()?;
            if !names.is_empty() {
                println!("{names}");
            }
        }
        Command::Install { path } => println!("{}", install(path)?),
        Command::Load { path } => println!("{}", load(path)?),
        Command::Unload { name } => println!("{}", unload(name)?),
        Command::Remove { name } => println!("{}", remove(name)?),
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn names_are_bounded_and_path_free() {
        assert!(validate_name("demo-hello").is_ok());
        assert!(validate_name("demo.hello").is_ok());
        assert!(validate_name("").is_err());
        assert!(validate_name("../evil").is_err());
        assert!(validate_name("a/b").is_err());
        assert!(validate_name(&"x".repeat(65)).is_err());
    }
}
