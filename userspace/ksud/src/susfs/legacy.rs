use super::model::{Config, Lkm, Rule, StaticKstat, Uname};
use anyhow::{Context, Result, bail};
use serde::Deserialize;

const VERSION: u8 = 2;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
#[allow(clippy::struct_excessive_bools)]
struct LegacyConfig {
    version: u8,
    #[serde(default = "enabled_by_default")]
    enabled: bool,
    cmdline_or_bootconfig: String,
    avc_log_spoofing: bool,
    logging: bool,
    hide_sus_mnts_for_non_su_procs: bool,
    uname: LegacyUname,
    #[serde(default)]
    sus_path: Vec<LegacyPath>,
    #[serde(default)]
    sus_kstat: Vec<LegacyKstat>,
    #[serde(default)]
    open_redirect: Vec<LegacyRedirect>,
    #[serde(default)]
    sus_map: Vec<String>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct LegacyUname {
    version: String,
    release: String,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct LegacyPath {
    path: String,
    is_loop: bool,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct LegacyKstat {
    path: String,
    spoof_type: LegacyKstatType,
    statically: Option<LegacyStatic>,
}

#[derive(Deserialize)]
enum LegacyKstatType {
    Normal,
    FullClone,
    Statically,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct LegacyStatic {
    ino: Option<i64>,
    dev: Option<i64>,
    nlink: Option<i64>,
    size: Option<i64>,
    atime: Option<i64>,
    atime_nsec: Option<i64>,
    mtime: Option<i64>,
    mtime_nsec: Option<i64>,
    ctime: Option<i64>,
    ctime_nsec: Option<i64>,
    blocks: Option<i64>,
    blksize: Option<i64>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct LegacyRedirect {
    target_path: String,
    redirected_path: String,
    uid_scheme: LegacyUidScheme,
}

#[derive(Deserialize)]
#[serde(untagged)]
enum LegacyUidScheme {
    Name(String),
    Number(i32),
}

pub struct Migration {
    pub config: Config,
    pub warnings: Vec<&'static str>,
}

const fn enabled_by_default() -> bool {
    true
}

pub fn convert(value: serde_json::Value) -> Result<Migration> {
    let legacy: LegacyConfig =
        serde_json::from_value(value).context("Legacy configuration shape is invalid")?;
    if legacy.version != VERSION {
        bail!(
            "Unsupported legacy configuration version: {}",
            legacy.version
        );
    }

    let cmdline = Some(legacy.cmdline_or_bootconfig);
    let uname = Some(Uname {
        release: legacy.uname.release,
        version: legacy.uname.version,
    });
    let mut rules = Vec::new();
    for item in legacy.sus_path {
        rules.push((
            item.path,
            Rule::Path {
                path: String::new(),
                looping: item.is_loop,
            },
        ));
    }
    for item in legacy.sus_kstat {
        let rule = match item.spoof_type {
            LegacyKstatType::Normal => {
                if item.statically.is_some() {
                    bail!("Static values are only valid for static kstat rules");
                }
                Rule::Kstat {
                    path: String::new(),
                    full_clone: false,
                }
            }
            LegacyKstatType::FullClone => {
                if item.statically.is_some() {
                    bail!("Static values are only valid for static kstat rules");
                }
                Rule::Kstat {
                    path: String::new(),
                    full_clone: true,
                }
            }
            LegacyKstatType::Statically => Rule::StaticKstat {
                path: String::new(),
                fields: convert_static(
                    &item
                        .statically
                        .context("Static kstat values are required for a static rule")?,
                )?,
            },
        };
        rules.push((item.path, rule));
    }
    for item in legacy.open_redirect {
        rules.push((
            item.target_path.clone(),
            Rule::Redirect {
                path: String::new(),
                destination: item.redirected_path,
                uid_scheme: convert_uid_scheme(item.uid_scheme)?,
            },
        ));
    }
    for path in legacy.sus_map {
        rules.push((
            path,
            Rule::Map {
                path: String::new(),
            },
        ));
    }
    rules.sort_by(|left, right| {
        rule_kind(&left.1)
            .cmp(&rule_kind(&right.1))
            .then_with(|| left.0.cmp(&right.0))
    });
    let rules = rules
        .into_iter()
        .map(|(path, mut rule)| {
            set_rule_path(&mut rule, path);
            rule
        })
        .collect();

    Ok(Migration {
        config: Config {
            schema: 2,
            automatic: legacy.enabled,
            settings: super::model::Settings {
                hide_mounts: Some(legacy.hide_sus_mnts_for_non_su_procs),
                logging: Some(legacy.logging),
                avc_spoof: Some(legacy.avc_log_spoofing),
                uname,
                cmdline,
            },
            rules,
            lkm: Lkm::default(),
        },
        warnings: vec![
            "Legacy enabled state becomes automatic boot application",
            "False values are imported as explicit disables",
            "Empty cmdline and default uname are imported as explicit resets",
            "Current provider capability checks still apply after import",
        ],
    })
}

const fn rule_kind(rule: &Rule) -> u8 {
    match rule {
        Rule::Path { .. } => 0,
        Rule::Map { .. } => 1,
        Rule::Kstat { .. } | Rule::StaticKstat { .. } => 2,
        Rule::Redirect { .. } => 3,
    }
}

fn set_rule_path(rule: &mut Rule, path: String) {
    match rule {
        Rule::Path { path: target, .. }
        | Rule::Map { path: target }
        | Rule::Kstat { path: target, .. }
        | Rule::StaticKstat { path: target, .. }
        | Rule::Redirect { path: target, .. } => *target = path,
    }
}

fn convert_static(value: &LegacyStatic) -> Result<StaticKstat> {
    Ok(StaticKstat {
        ino: unsigned("ino", value.ino)?,
        dev: unsigned("dev", value.dev)?,
        nlink: unsigned("nlink", value.nlink)?,
        size: value.size,
        atime_sec: value.atime,
        atime_nsec: unsigned("atime_nsec", value.atime_nsec)?,
        mtime_sec: value.mtime,
        mtime_nsec: unsigned("mtime_nsec", value.mtime_nsec)?,
        ctime_sec: value.ctime,
        ctime_nsec: unsigned("ctime_nsec", value.ctime_nsec)?,
        blocks: value.blocks,
        blksize: value.blksize,
    })
}

fn unsigned<T>(name: &str, value: Option<i64>) -> Result<Option<T>>
where
    T: TryFrom<i64>,
{
    value
        .map(|value| T::try_from(value).map_err(|_| anyhow::anyhow!("Invalid {name} value")))
        .transpose()
}

fn convert_uid_scheme(value: LegacyUidScheme) -> Result<u32> {
    match value {
        LegacyUidScheme::Number(value) => u32::try_from(value).context("Invalid UID scheme"),
        LegacyUidScheme::Name(name) => match name.as_str() {
            "NonApp" => Ok(0),
            "RootExceptSu" => Ok(1),
            "NonSu" => Ok(2),
            "UnmountedApp" => Ok(3),
            "Unmounted" => Ok(4),
            _ => bail!("Unknown UID scheme: {name}"),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> serde_json::Value {
        serde_json::json!({
            "version": 2,
            "enabled": false,
            "cmdline_or_bootconfig": "",
            "avc_log_spoofing": true,
            "logging": false,
            "hide_sus_mnts_for_non_su_procs": false,
            "uname": {"release": "default", "version": "default"},
            "sus_path": [{"path": "/data/path", "is_loop": true}],
            "sus_kstat": [
                {"path": "/data/static", "spoof_type": "Statically", "statically": {
                    "ino": 1, "dev": 2, "nlink": 3, "size": 4,
                    "atime": 5, "atime_nsec": 6, "mtime": 7, "mtime_nsec": 8,
                    "ctime": 9, "ctime_nsec": 10, "blocks": 11, "blksize": 12
                }},
                {"path": "/data/clone", "spoof_type": "FullClone", "statically": null}
            ],
            "open_redirect": [{
                "target_path": "/data/source",
                "redirected_path": "/data/destination",
                "uid_scheme": "NonSu"
            }],
            "sus_map": ["/data/map"]
        })
    }

    #[test]
    fn converts_all_fields_without_losing_explicit_values() {
        let migration = convert(fixture()).unwrap();
        assert_eq!(migration.config.schema, 2);
        assert!(!migration.config.automatic);
        assert_eq!(migration.config.settings.hide_mounts, Some(false));
        assert_eq!(migration.config.settings.logging, Some(false));
        assert_eq!(migration.config.settings.avc_spoof, Some(true));
        assert_eq!(migration.config.settings.cmdline.as_deref(), Some(""));
        assert_eq!(
            migration
                .config
                .settings
                .uname
                .as_ref()
                .map(|uname| (uname.release.as_str(), uname.version.as_str())),
            Some(("default", "default"))
        );
        assert_eq!(migration.config.rules.len(), 5);
        let fields = migration.config.rules.iter().find_map(|rule| match rule {
            Rule::StaticKstat { fields, .. } => Some(fields),
            _ => None,
        });
        let fields = fields.unwrap();
        assert_eq!(fields.ino, Some(1));
        assert_eq!(fields.dev, Some(2));
        assert_eq!(fields.nlink, Some(3));
        assert_eq!(fields.size, Some(4));
        assert_eq!(fields.atime_sec, Some(5));
        assert_eq!(fields.atime_nsec, Some(6));
        assert_eq!(fields.mtime_sec, Some(7));
        assert_eq!(fields.mtime_nsec, Some(8));
        assert_eq!(fields.ctime_sec, Some(9));
        assert_eq!(fields.ctime_nsec, Some(10));
        assert_eq!(fields.blocks, Some(11));
        assert_eq!(fields.blksize, Some(12));
    }

    #[test]
    fn rejects_unknown_or_lossy_shapes() {
        let mut value = fixture();
        value["unknown"] = true.into();
        assert!(convert(value).is_err());

        let mut value = fixture();
        value["sus_kstat"] = serde_json::json!([{
            "path": "/data/static",
            "spoof_type": "Statically",
            "statically": null
        }]);
        assert!(convert(value).is_err());
    }
}
