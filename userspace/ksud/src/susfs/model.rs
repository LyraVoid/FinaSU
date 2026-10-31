use anyhow::{Result, bail};
use serde::{Deserialize, Serialize};

pub const MAX_CONFIG: usize = 128 * 1024;

#[allow(dead_code)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ManagementMode {
    Builtin,
    External,
}

#[allow(dead_code)]
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ManagementSwitch {
    pub target: ManagementMode,
    pub requested_boot_id: String,
}

#[allow(dead_code)]
impl ManagementSwitch {
    pub fn may_confirm(&self, boot_id: &str) -> bool {
        !boot_id.is_empty()
            && !self.requested_boot_id.is_empty()
            && self.requested_boot_id != boot_id
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Config {
    pub schema: u32,
    pub automatic: bool,
    pub settings: Settings,
    pub rules: Vec<Rule>,
    pub lkm: Lkm,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            schema: 1,
            automatic: false,
            settings: Settings::default(),
            rules: Vec::new(),
            lkm: Lkm::default(),
        }
    }
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Settings {
    pub hide_mounts: Option<bool>,
    pub logging: Option<bool>,
    pub avc_spoof: Option<bool>,
    pub uname: Option<Uname>,
    pub cmdline: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Uname {
    pub release: String,
    pub version: String,
}

impl Uname {
    pub fn is_default(&self) -> bool {
        self.release == "default" && self.version == "default"
    }
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Lkm {
    pub hidden_modules: Vec<String>,
    pub mount_prefixes: Vec<String>,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct StaticKstat {
    pub ino: Option<u64>,
    pub dev: Option<u64>,
    pub nlink: Option<u32>,
    pub size: Option<i64>,
    pub atime_sec: Option<i64>,
    pub atime_nsec: Option<u32>,
    pub mtime_sec: Option<i64>,
    pub mtime_nsec: Option<u32>,
    pub ctime_sec: Option<i64>,
    pub ctime_nsec: Option<u32>,
    pub blocks: Option<i64>,
    pub blksize: Option<i64>,
}

impl StaticKstat {
    pub fn flags(&self) -> i32 {
        [
            self.ino.is_some(),
            self.dev.is_some(),
            self.nlink.is_some(),
            self.size.is_some(),
            self.atime_sec.is_some(),
            self.atime_nsec.is_some(),
            self.mtime_sec.is_some(),
            self.mtime_nsec.is_some(),
            self.ctime_sec.is_some(),
            self.ctime_nsec.is_some(),
            self.blocks.is_some(),
            self.blksize.is_some(),
        ]
        .into_iter()
        .enumerate()
        .fold(0, |flags, (bit, present)| {
            flags | (i32::from(present) << bit)
        })
    }

    pub fn validate(&self) -> Result<()> {
        if self.flags() == 0 {
            bail!("Static kstat requires at least one field");
        }
        if [self.size, self.blocks, self.blksize]
            .into_iter()
            .flatten()
            .any(|value| value < 0)
            || [self.atime_nsec, self.mtime_nsec, self.ctime_nsec]
                .into_iter()
                .flatten()
                .any(|value| value >= 1_000_000_000)
        {
            bail!("Static kstat field out of range");
        }
        Ok(())
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case", deny_unknown_fields)]
pub enum Rule {
    Path {
        path: String,
        looping: bool,
    },
    Map {
        path: String,
    },
    Redirect {
        path: String,
        destination: String,
        uid_scheme: u32,
    },
    Kstat {
        path: String,
        full_clone: bool,
    },
    StaticKstat {
        path: String,
        fields: StaticKstat,
    },
}

pub fn path(value: &str) -> Result<()> {
    if !value.starts_with('/')
        || value == "/"
        || value.len() >= 256
        || value.chars().any(char::is_whitespace)
        || value.chars().any(char::is_control)
        || value.split('/').any(|p| matches!(p, "." | ".."))
        || value.contains("//")
    {
        bail!("Invalid absolute SUSFS path");
    }
    Ok(())
}

fn text(value: &str, max: usize) -> Result<()> {
    if value.len() >= max || value.chars().any(char::is_control) {
        bail!("Invalid SUSFS text field");
    }
    Ok(())
}

impl Config {
    pub fn parse(input: &str) -> Result<Self> {
        if input.len() > MAX_CONFIG {
            bail!("SUSFS config is too large");
        }
        let config: Self = serde_json::from_str(input)?;
        config.validate()?;
        Ok(config)
    }

    pub fn validate(&self) -> Result<()> {
        if !matches!(self.schema, 1 | 2)
            || self.rules.len() > 128
            || self.lkm.hidden_modules.len() > 16
            || self.lkm.mount_prefixes.len() > 8
        {
            bail!("Unsupported schema or too many rules");
        }
        if let Some(uname) = &self.settings.uname {
            text(&uname.release, 65)?;
            text(&uname.version, 65)?;
        }
        if let Some(cmdline) = &self.settings.cmdline {
            text(cmdline, 8192)?;
        }
        let mut identities = std::collections::HashSet::new();
        for rule in &self.rules {
            let (kind, target) = match rule {
                Rule::Path { path, .. } => ("path", path),
                Rule::Map { path } => ("map", path),
                Rule::Kstat { path, .. } => ("kstat", path),
                Rule::StaticKstat { path, fields } => {
                    if self.schema != 2 {
                        bail!("Static kstat requires schema 2");
                    }
                    fields.validate()?;
                    ("kstat", path)
                }
                Rule::Redirect {
                    path,
                    destination,
                    uid_scheme,
                } => {
                    crate::susfs::model::path(destination)?;
                    if *uid_scheme > 4 || path == destination {
                        bail!("Invalid redirect");
                    }
                    ("redirect", path)
                }
            };
            path(target)?;
            if !identities.insert((kind, target)) {
                bail!("Duplicate SUSFS rule");
            }
        }
        for name in &self.lkm.hidden_modules {
            if name.is_empty()
                || name.len() > 63
                || !name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
            {
                bail!("Invalid module name");
            }
            if !identities.insert(("module", name)) {
                bail!("Duplicate hidden module");
            }
        }
        for prefix in &self.lkm.mount_prefixes {
            path(prefix)?;
            if !identities.insert(("prefix", prefix)) {
                bail!("Duplicate hidden mount prefix");
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ownership_switch_requires_a_distinct_known_boot() {
        for target in [ManagementMode::Builtin, ManagementMode::External] {
            let pending = ManagementSwitch {
                target,
                requested_boot_id: "boot-a".to_owned(),
            };
            assert!(!pending.may_confirm("boot-a"));
            assert!(!pending.may_confirm(""));
            assert!(pending.may_confirm("boot-b"));
            let decoded: ManagementSwitch =
                serde_json::from_str(&serde_json::to_string(&pending).unwrap()).unwrap();
            assert_eq!(decoded, pending);
        }
        let pending = ManagementSwitch {
            target: ManagementMode::Builtin,
            requested_boot_id: String::new(),
        };
        assert!(!pending.may_confirm("boot-b"));
    }

    #[test]
    fn static_kstat_validates_optional_fields_without_conflating_zero_and_absent() {
        assert!(StaticKstat::default().validate().is_err());
        let mut fields: StaticKstat = serde_json::from_str(r#"{"size":0,"ctime_sec":-1}"#).unwrap();
        assert!(fields.validate().is_ok());
        assert_eq!(fields.size, Some(0));
        assert_eq!(fields.ino, None);
        assert_eq!(fields.flags(), 0x108);
        fields.ctime_nsec = Some(999_999_999);
        assert!(fields.validate().is_ok());
        fields.ctime_nsec = Some(1_000_000_000);
        assert!(fields.validate().is_err());
        fields.ctime_nsec = None;
        fields.size = Some(-1);
        assert!(fields.validate().is_err());
        assert!(serde_json::from_str::<StaticKstat>(r#"{"nlink":4294967296}"#).is_err());
        assert!(serde_json::from_str::<StaticKstat>(r#"{"unknown":1}"#).is_err());
    }

    #[test]
    fn static_kstat_each_field_has_its_own_flag() {
        for (bit, name) in [
            "ino",
            "dev",
            "nlink",
            "size",
            "atime_sec",
            "atime_nsec",
            "mtime_sec",
            "mtime_nsec",
            "ctime_sec",
            "ctime_nsec",
            "blocks",
            "blksize",
        ]
        .into_iter()
        .enumerate()
        {
            let fields: StaticKstat = serde_json::from_value(serde_json::json!({name: 0})).unwrap();
            assert!(fields.validate().is_ok());
            assert_eq!(fields.flags(), 1 << bit);
        }
    }

    #[test]
    fn static_rules_require_schema_two_and_share_dynamic_identity() {
        let mut config = Config::default();
        config.rules.push(Rule::StaticKstat {
            path: "/data/test".to_owned(),
            fields: StaticKstat {
                size: Some(0),
                ..StaticKstat::default()
            },
        });
        assert!(config.validate().is_err());
        config.schema = 2;
        assert!(Config::parse(&serde_json::to_string(&config).unwrap()).is_ok());
        config.rules.push(Rule::Kstat {
            path: "/data/test".to_owned(),
            full_clone: false,
        });
        assert!(config.validate().is_err());
    }
    #[test]
    fn default_is_opt_in() {
        let config = Config::default();
        assert!(!config.automatic);
        assert!(Config::parse(&serde_json::to_string(&config).unwrap()).is_ok());
    }
    #[test]
    fn strict_import() {
        let original = serde_json::to_value(Config::default()).unwrap();
        let mut unknown = original.clone();
        unknown["execute"] = true.into();
        assert!(Config::parse(&unknown.to_string()).is_err());
        let mut schema = original;
        schema["schema"] = 3.into();
        assert!(Config::parse(&schema.to_string()).is_err());
        assert!(Config::parse(&" ".repeat(MAX_CONFIG + 1)).is_err());
        for invalid in [
            "relative",
            "/",
            "/data/../x",
            "/data/a\nb",
            "/data//a",
            "/data/a b",
        ] {
            assert!(path(invalid).is_err());
        }
    }

    #[test]
    fn rejects_duplicate_rules_and_extensions() {
        let mut config = Config::default();
        config.rules = vec![
            Rule::Map {
                path: "/data/test".to_owned()
            };
            2
        ];
        assert!(config.validate().is_err());
        config.rules.clear();
        config.lkm.hidden_modules = vec!["susfs".to_owned(); 2];
        assert!(config.validate().is_err());
        config.lkm.hidden_modules.clear();
        config.lkm.mount_prefixes = vec!["/data/test".to_owned(); 2];
        assert!(config.validate().is_err());
    }
}
