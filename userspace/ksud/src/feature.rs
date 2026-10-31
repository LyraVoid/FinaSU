use crate::sulog;
use anyhow::{Context, Result, bail};
use const_format::concatcp;
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::path::{Path, PathBuf};

use crate::defs;
use crate::feature_id::{FeatureId, parse_feature_id};

const FEATURE_CONFIG_PATH: &str = concatcp!(defs::WORKING_DIR, ".feature_config");
const FEATURE_BASELINE_PATH: &str = concatcp!(defs::WORKING_DIR, ".feature_baseline");
const FEATURE_DRIFT_PATH: &str = concatcp!(defs::WORKING_DIR, ".feature_drift");
const BOOT_ID_PATH: &str = "/proc/sys/kernel/random/boot_id";
#[allow(clippy::unreadable_literal)]
const FEATURE_MAGIC: u32 = 0x7f4b5355;
const FEATURE_VERSION: u32 = 1;

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
struct FeatureSnapshot {
    boot_id: String,
    values: BTreeMap<u32, u64>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct FeatureChange {
    pub id: u32,
    pub name: String,
    pub expected: u64,
    pub actual: u64,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
struct FeatureDrift {
    boot_id: String,
    stage: String,
    changes: Vec<FeatureChange>,
}

fn set_kernel_feature(feature_id: FeatureId, value: u64) -> Result<()> {
    if feature_id.has_dedicated_manager() {
        bail!(
            "Feature '{}' has a dedicated configuration; use `ksud uts-view`",
            feature_id.name()
        );
    }
    crate::ksucalls::set_feature(feature_id as u32, value)
        .with_context(|| format!("Failed to set feature {} to {value}", feature_id.name()))?;

    on_feature_applied(feature_id, value);
    Ok(())
}

fn on_feature_applied(feature_id: FeatureId, value: u64) {
    if feature_id == FeatureId::Sulog
        && value != 0
        && let Err(err) = sulog::ensure_sulogd_running()
    {
        log::warn!("failed to ensure sulogd is running after feature init: {err:#}");
    }
}

pub fn load_binary_config() -> Result<HashMap<u32, u64>> {
    let path = Path::new(FEATURE_CONFIG_PATH);
    if !path.exists() {
        log::info!("Feature config not found, using defaults");
        return Ok(HashMap::new());
    }

    let mut file = File::open(path).with_context(|| "Failed to open feature config")?;

    let mut magic_buf = [0u8; 4];
    file.read_exact(&mut magic_buf)
        .with_context(|| "Failed to read magic")?;
    let magic = u32::from_le_bytes(magic_buf);

    if magic != FEATURE_MAGIC {
        bail!("Invalid feature config magic: expected 0x{FEATURE_MAGIC:08x}, got 0x{magic:08x}");
    }

    let mut version_buf = [0u8; 4];
    file.read_exact(&mut version_buf)
        .with_context(|| "Failed to read version")?;
    let version = u32::from_le_bytes(version_buf);

    if version != FEATURE_VERSION {
        log::warn!(
            "Feature config version mismatch: expected {FEATURE_VERSION}, got {version
            }",
        );
    }

    let mut count_buf = [0u8; 4];
    file.read_exact(&mut count_buf)
        .with_context(|| "Failed to read count")?;
    let count = u32::from_le_bytes(count_buf);

    let mut features = HashMap::new();
    for _ in 0..count {
        let mut id_buf = [0u8; 4];
        let mut value_buf = [0u8; 8];

        file.read_exact(&mut id_buf)
            .with_context(|| "Failed to read feature id")?;
        file.read_exact(&mut value_buf)
            .with_context(|| "Failed to read feature value")?;

        let id = u32::from_le_bytes(id_buf);
        let value = u64::from_le_bytes(value_buf);

        features.insert(id, value);
    }

    log::info!("Loaded {} features from config", features.len());
    Ok(features)
}

pub fn save_binary_config(features: &HashMap<u32, u64>) -> Result<()> {
    crate::utils::ensure_dir_exists(Path::new(defs::WORKING_DIR))?;

    let path = Path::new(FEATURE_CONFIG_PATH);
    let mut file = File::create(path).with_context(|| "Failed to create feature config")?;

    file.write_all(&FEATURE_MAGIC.to_le_bytes())
        .with_context(|| "Failed to write magic")?;

    file.write_all(&FEATURE_VERSION.to_le_bytes())
        .with_context(|| "Failed to write version")?;

    let count = features.len() as u32;
    file.write_all(&count.to_le_bytes())
        .with_context(|| "Failed to write count")?;

    for (&id, &value) in features {
        file.write_all(&id.to_le_bytes())
            .with_context(|| format!("Failed to write feature id {id}"))?;
        file.write_all(&value.to_le_bytes())
            .with_context(|| format!("Failed to write feature value for id {id}"))?;
    }

    file.sync_all()
        .with_context(|| "Failed to sync feature config")?;

    log::info!("Saved {} features to config", features.len());
    Ok(())
}

fn current_boot_id() -> Result<String> {
    Ok(fs::read_to_string(BOOT_ID_PATH)
        .with_context(|| format!("Read {BOOT_ID_PATH}"))?
        .trim()
        .to_owned())
}

fn read_json<T: DeserializeOwned>(path: &str) -> Result<T> {
    serde_json::from_slice(&fs::read(path).with_context(|| format!("Read {path}"))?)
        .with_context(|| format!("Parse {path}"))
}

fn write_json<T: Serialize>(path: &str, value: &T) -> Result<()> {
    crate::utils::ensure_dir_exists(Path::new(defs::WORKING_DIR))?;
    let target = Path::new(path);
    let temporary = PathBuf::from(format!("{}.tmp.{}", path, std::process::id()));
    let result = (|| -> Result<()> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&temporary)
            .with_context(|| format!("Create {}", temporary.display()))?;
        file.write_all(&serde_json::to_vec(value)?)?;
        file.sync_all()?;
        fs::rename(&temporary, target).with_context(|| format!("Replace {}", target.display()))?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(&temporary);
    }
    result
}

fn managed_feature_ids() -> Result<HashSet<u32>> {
    Ok(crate::module::get_managed_features()?
        .values()
        .flatten()
        .filter_map(|name| parse_feature_id(name).ok())
        .map(|feature| feature as u32)
        .collect())
}

fn current_feature_values(managed: &HashSet<u32>) -> BTreeMap<u32, u64> {
    let mut values = BTreeMap::new();
    for feature in FeatureId::ALL {
        let id = feature as u32;
        if feature.has_dedicated_manager() || managed.contains(&id) {
            continue;
        }
        if let Ok(state) = crate::ksucalls::get_feature(id)
            && state.supported
            && !state.forced
        {
            values.insert(id, state.value);
        }
    }
    values
}

fn changed_features(
    expected: &BTreeMap<u32, u64>,
    actual: &BTreeMap<u32, u64>,
) -> Vec<FeatureChange> {
    expected
        .iter()
        .filter_map(|(&id, &expected)| {
            let &actual = actual.get(&id)?;
            if actual == expected {
                return None;
            }
            Some(FeatureChange {
                id,
                name: FeatureId::from_u32(id)
                    .map_or("unknown", FeatureId::name)
                    .to_owned(),
                expected,
                actual,
            })
        })
        .collect()
}

pub fn capture_baseline() {
    let result = (|| -> Result<()> {
        let managed = managed_feature_ids()?;
        let snapshot = FeatureSnapshot {
            boot_id: current_boot_id()?,
            values: current_feature_values(&managed),
        };
        write_json(FEATURE_BASELINE_PATH, &snapshot)?;
        write_json(
            FEATURE_DRIFT_PATH,
            &FeatureDrift {
                boot_id: snapshot.boot_id,
                stage: "boot".to_owned(),
                changes: Vec::new(),
            },
        )
    })();
    if let Err(error) = result {
        log::warn!("Failed to capture feature baseline: {error:#}");
    }
}

pub fn detect_external_changes(stage: &str) {
    let result = (|| -> Result<()> {
        let snapshot: FeatureSnapshot = read_json(FEATURE_BASELINE_PATH)?;
        let boot_id = current_boot_id()?;
        if snapshot.boot_id != boot_id {
            return Ok(());
        }
        let managed = managed_feature_ids()?;
        let actual = current_feature_values(&managed);
        let changes = changed_features(&snapshot.values, &actual);
        if !changes.is_empty() {
            log::warn!(
                "Features changed outside FinaSU after {stage}: {}",
                serde_json::to_string(&changes)?
            );
        }
        write_json(
            FEATURE_DRIFT_PATH,
            &FeatureDrift {
                boot_id,
                stage: stage.to_owned(),
                changes,
            },
        )
    })();
    if let Err(error) = result {
        log::warn!("Failed to detect external feature changes: {error:#}");
    }
}

fn rebase_feature(feature: FeatureId) {
    if std::env::var_os("KSU_MODULE").is_some() {
        return;
    }
    let result = (|| -> Result<()> {
        let mut snapshot: FeatureSnapshot = read_json(FEATURE_BASELINE_PATH)?;
        if snapshot.boot_id != current_boot_id()? {
            return Ok(());
        }
        let state = crate::ksucalls::get_feature(feature as u32)?;
        if !state.supported || state.forced {
            return Ok(());
        }
        snapshot.values.insert(feature as u32, state.value);
        write_json(FEATURE_BASELINE_PATH, &snapshot)?;

        let mut drift: FeatureDrift = read_json(FEATURE_DRIFT_PATH)?;
        if drift.boot_id == snapshot.boot_id {
            drift.changes.retain(|change| change.id != feature as u32);
            write_json(FEATURE_DRIFT_PATH, &drift)?;
        }
        Ok(())
    })();
    if let Err(error) = result {
        log::warn!("Failed to rebase feature {}: {error:#}", feature.name());
    }
}

pub fn external_changes() -> Result<()> {
    detect_external_changes("on-demand");
    let boot_id = current_boot_id()?;
    let drift = read_json::<FeatureDrift>(FEATURE_DRIFT_PATH).unwrap_or_else(|_| FeatureDrift {
        boot_id: boot_id.clone(),
        ..FeatureDrift::default()
    });
    let visible = if drift.boot_id == boot_id {
        drift
    } else {
        FeatureDrift {
            boot_id,
            ..FeatureDrift::default()
        }
    };
    println!("{}", serde_json::to_string(&visible)?);
    Ok(())
}

pub fn apply_config(features: &HashMap<u32, u64>) {
    log::info!("Applying feature configuration to kernel...");

    let mut applied = 0;
    for (&id, &value) in features {
        if FeatureId::from_u32(id).is_some_and(FeatureId::has_dedicated_manager) {
            log::info!("Skip feature {id}: dedicated configuration is applied separately");
            continue;
        }
        if crate::ksucalls::get_feature(id).is_ok_and(|state| state.forced) {
            log::info!("Skip feature {id}: forced by boot config");
            continue;
        }
        match FeatureId::from_u32(id) {
            Some(feature_id) => match set_kernel_feature(feature_id, value) {
                Ok(()) => {
                    log::info!("Set feature {} to {value}", feature_id.name());
                    applied += 1;
                }
                Err(e) => {
                    log::warn!("Failed to set feature {}: {e}", feature_id.name());
                }
            },
            None => match crate::ksucalls::set_feature(id, value) {
                Ok(()) => {
                    log::info!("Set feature {id} to {value}");
                    applied += 1;
                }
                Err(e) => {
                    log::warn!("Failed to set feature {id}: {e}");
                }
            },
        }
    }

    log::info!("Applied {applied} features successfully");
}

pub fn get_feature(id: &str) -> Result<()> {
    let feature_id = parse_feature_id(id)?;
    if !feature_supported(feature_id)? {
        println!("Feature '{id}' is not supported by kernel");
        return Ok(());
    }
    let state = crate::ksucalls::get_feature(feature_id as u32)
        .with_context(|| format!("Failed to get feature {id}"))?;

    if !state.supported {
        println!("Feature '{id}' is not supported by kernel");
        return Ok(());
    }

    println!("Feature: {} ({})", feature_id.name(), feature_id as u32);
    println!("Description: {}", feature_id.description());
    println!("Value: {}", state.value);
    println!(
        "Status: {}",
        if state.value != 0 {
            "enabled"
        } else {
            "disabled"
        }
    );
    if state.forced {
        println!("Forced: by boot config");
    }

    Ok(())
}

pub fn feature_supported(feature_id: FeatureId) -> Result<bool> {
    if feature_id as u32 >= crate::ksucalls::get_info().features {
        return Ok(false);
    }
    Ok(crate::ksucalls::get_feature(feature_id as u32)
        .with_context(|| format!("Failed to query feature {}", feature_id.name()))?
        .supported)
}

pub fn get_feature_config(id: &str) -> Result<()> {
    let feature_id = parse_feature_id(id)?;

    let features = load_binary_config()?;
    let id_u32 = feature_id as u32;

    println!("Feature: {} ({})", feature_id.name(), id_u32);
    println!("Description: {}", feature_id.description());

    if let Some(value) = features.get(&id_u32) {
        println!("Value: {value}");
        println!(
            "Status: {}",
            if *value != 0 { "enabled" } else { "disabled" }
        );
    } else {
        println!("Not set in config");
    }

    Ok(())
}

pub fn set_feature(id: &str, value: u64) -> Result<()> {
    let feature_id = parse_feature_id(id)?;
    if crate::ksucalls::get_feature(feature_id as u32).is_ok_and(|state| state.forced) {
        bail!("Feature '{}' is forced by boot config", feature_id.name());
    }

    // Check if this feature is managed by any module
    if let Ok(managed_features_map) = crate::module::get_managed_features() {
        // Find which modules manage this feature
        let managing_modules: Vec<&String> = managed_features_map
            .iter()
            .filter(|(_, features)| features.iter().any(|f| f == feature_id.name()))
            .map(|(module_id, _)| module_id)
            .collect();

        if !managing_modules.is_empty() {
            // Feature is managed, check if caller is an authorized module
            let caller_module = std::env::var("KSU_MODULE").unwrap_or_default();

            if caller_module.is_empty() || !managing_modules.contains(&&caller_module) {
                bail!(
                    "Feature '{}' is managed by module(s): {}. Direct modification is not allowed.",
                    feature_id.name(),
                    managing_modules
                        .iter()
                        .map(|s| s.as_str())
                        .collect::<Vec<_>>()
                        .join(", ")
                );
            }

            log::info!(
                "Module '{caller_module}' is setting managed feature '{}'",
                feature_id.name()
            );
        }
    }

    set_kernel_feature(feature_id, value)?;
    rebase_feature(feature_id);

    println!(
        "Feature '{}' set to {value} ({})",
        feature_id.name(),
        if value != 0 { "enabled" } else { "disabled" }
    );

    Ok(())
}

pub fn list_features() {
    println!("Available Features:");
    println!("{}", "=".repeat(80));

    // Get managed features from modules
    let managed_features_map = crate::module::get_managed_features().unwrap_or_default();

    // Build a reverse map: feature_name -> Vec<module_id>
    let mut feature_to_modules: HashMap<String, Vec<String>> = HashMap::new();
    for (module_id, feature_list) in &managed_features_map {
        for feature_name in feature_list {
            feature_to_modules
                .entry(feature_name.clone())
                .or_default()
                .push(module_id.clone());
        }
    }

    let all_features = [
        FeatureId::SuCompat,
        FeatureId::KernelUmount,
        FeatureId::Sulog,
        FeatureId::AdbRoot,
        FeatureId::SelinuxHide,
        FeatureId::AvcSpoof,
        FeatureId::ShellSuAdbOnly,
        FeatureId::UtsView,
        FeatureId::MountViewFilter,
    ];

    for feature_id in &all_features {
        let id = *feature_id as u32;
        let state = crate::ksucalls::get_feature(id).unwrap_or_default();

        let status = if !state.supported {
            "NOT_SUPPORTED".to_string()
        } else if state.value != 0 {
            format!("ENABLED ({})", state.value)
        } else {
            "DISABLED".to_string()
        };

        let managed_by = feature_to_modules.get(feature_id.name());
        let managed_mark = if state.forced {
            " [FORCED]"
        } else if managed_by.is_some() {
            " [MODULE_MANAGED]"
        } else {
            ""
        };

        println!(
            "[{}] {} (ID={}){}",
            status,
            feature_id.name(),
            id,
            managed_mark
        );
        println!("    {}", feature_id.description());

        if let Some(modules) = managed_by {
            println!(
                "    ⚠️  Managed by module(s): {} (forced to 0 on initialization)",
                modules.join(", ")
            );
        }

        println!();
    }
}

pub fn load_config_and_apply() -> Result<()> {
    let features = load_binary_config()?;

    if features.is_empty() {
        println!("No features found in config file");
        return Ok(());
    }

    apply_config(&features);
    println!("Feature configuration loaded and applied");
    Ok(())
}

pub fn save_config() -> Result<()> {
    // Preserve persisted values while boot config temporarily overrides them.
    let mut features = load_binary_config()?;
    features.remove(&(FeatureId::UtsView as u32));

    let all_features = [
        FeatureId::SuCompat,
        FeatureId::KernelUmount,
        FeatureId::Sulog,
        FeatureId::AdbRoot,
        FeatureId::SelinuxHide,
        FeatureId::AvcSpoof,
        FeatureId::ShellSuAdbOnly,
        FeatureId::MountViewFilter,
    ];

    for feature_id in &all_features {
        let id = *feature_id as u32;
        if let Ok(state) = crate::ksucalls::get_feature(id)
            && state.supported
            && !state.forced
        {
            features.insert(id, state.value);
            log::info!("Saved feature {} = {}", feature_id.name(), state.value);
        }
    }

    save_binary_config(&features)?;
    println!(
        "Current feature states saved to config file ({} features)",
        features.len()
    );
    Ok(())
}

pub fn check_feature(id: &str) -> Result<()> {
    let feature_id = parse_feature_id(id)?;
    if crate::ksucalls::get_feature(feature_id as u32).is_ok_and(|state| state.forced) {
        println!("forced");
        return Ok(());
    }

    // Check if this feature is managed by any module
    let managed_features_map = crate::module::get_managed_features().unwrap_or_default();
    let is_managed = managed_features_map
        .values()
        .any(|features| features.iter().any(|f| f == feature_id.name()));

    if is_managed {
        println!("managed");
        return Ok(());
    }

    if feature_supported(feature_id)? {
        println!("supported");
    } else {
        println!("unsupported");
    }

    Ok(())
}

pub fn init_forced_features() {
    for id in FeatureId::ALL {
        if let Ok(state) = crate::ksucalls::get_feature(id as u32)
            && state.forced
        {
            on_feature_applied(id, state.value);
        }
    }
}

pub fn init_features() -> Result<()> {
    log::info!("Initializing features from config...");

    let mut features = load_binary_config()?;
    if features.remove(&(FeatureId::UtsView as u32)).is_some() {
        log::info!("Removed UTS View from the generic feature config");
    }

    // Get managed features from active modules and skip them during init
    if let Ok(managed_features_map) = crate::module::get_managed_features() {
        if !managed_features_map.is_empty() {
            log::info!(
                "Found {} modules managing features",
                managed_features_map.len()
            );

            // Build a set of all managed feature IDs to skip
            for (module_id, feature_list) in &managed_features_map {
                log::info!(
                    "Module '{module_id}' manages {} feature(s)",
                    feature_list.len()
                );

                for feature_name in feature_list {
                    if let Ok(feature_id) = parse_feature_id(feature_name) {
                        let feature_id_u32 = feature_id as u32;
                        if crate::ksucalls::get_feature(feature_id_u32)
                            .is_ok_and(|state| state.forced)
                        {
                            continue;
                        }
                        // Remove managed features from config, let modules control them
                        if features.remove(&feature_id_u32).is_some() {
                            log::info!(
                                "  - Skipping managed feature '{feature_name}' (controlled by module: {module_id})",
                            );
                        } else {
                            log::info!(
                                "  - Feature '{feature_name}' is managed by module '{module_id}', skipping",
                            );
                        }
                    } else {
                        log::warn!(
                            "  - Unknown managed feature '{feature_name}' from module '{module_id}', ignoring",
                        );
                    }
                }
            }
        }
    } else {
        log::warn!(
            "Failed to get managed features from modules, continuing with normal initialization"
        );
    }

    if features.is_empty() {
        log::info!("No features to apply, skipping initialization");
        return Ok(());
    }

    apply_config(&features);

    // Save the configuration (excluding managed features)
    save_binary_config(&features)?;
    log::info!("Saved feature configuration to file");

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn external_change_diff_keeps_only_supported_changes() {
        let expected = BTreeMap::from([
            (FeatureId::KernelUmount as u32, 1),
            (FeatureId::SelinuxHide as u32, 1),
            (FeatureId::Sulog as u32, 0),
        ]);
        let actual = BTreeMap::from([
            (FeatureId::KernelUmount as u32, 0),
            (FeatureId::SelinuxHide as u32, 1),
        ]);

        assert_eq!(
            changed_features(&expected, &actual),
            vec![FeatureChange {
                id: FeatureId::KernelUmount as u32,
                name: "kernel_umount".to_owned(),
                expected: 1,
                actual: 0,
            }]
        );
    }

    #[test]
    fn drift_round_trips_for_the_status_api() {
        let drift = FeatureDrift {
            boot_id: "boot".to_owned(),
            stage: "post-fs-data".to_owned(),
            changes: vec![FeatureChange {
                id: 1,
                name: "kernel_umount".to_owned(),
                expected: 1,
                actual: 0,
            }],
        };
        let encoded = serde_json::to_string(&drift).unwrap();
        assert_eq!(
            serde_json::from_str::<FeatureDrift>(&encoded).unwrap(),
            drift
        );
    }
}
