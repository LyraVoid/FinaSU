use anyhow::{Result, bail};
use serde::{Deserialize, Serialize};

pub const MAX_CONFIG: usize = 128 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Config {
    pub schema: u32,
    pub automatic: bool,
    pub settings: Settings,
    pub rules: Vec<Rule>,
    pub lkm: Lkm,
    /// Built-in port of BRENE's SuSFS hardening profile. Missing section in a
    /// pre-existing file means "defaults", which keeps the profile active for
    /// users who never opened the SUSFS screen.
    #[serde(default)]
    pub brene: Brene,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            schema: 1,
            automatic: false,
            settings: Settings::default(),
            rules: Vec::new(),
            lkm: Lkm::default(),
            brene: Brene::default(),
        }
    }
}

/// BRENE-compatible on-device hardening. Defaults mirror BRENE v0.0.72's
/// config.sh; the three `Option<bool>` entries are deliberately left alone
/// unless set, because silently disabling ADB is not recoverable for everyone
/// (BRENE's own defaults switch developer options, USB and wireless ADB off).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct Brene {
    pub enabled: bool,
    pub hide_local_tmp: bool,
    pub hide_android_dirs: bool,
    pub hide_non_standard_storage: bool,
    pub hide_ksu_dirs: bool,
    pub hide_custom_recovery: bool,
    pub hide_custom_rom_paths: bool,
    pub hide_custom_rom_paths_deep: bool,
    pub hide_addon_d: bool,
    pub hide_suspicious_pty: bool,
    pub hide_framework_res: bool,
    pub hide_lineage_strings: bool,
    pub hide_third_party_apps: bool,
    pub hide_sus_mounts: bool,
    pub avc_log_spoofing: bool,
    pub spoof_uname: bool,
    pub spoof_cmdline: bool,
    pub spoof_hosts: bool,
    pub spoof_system_properties: bool,
    pub spoof_fingerprint: bool,
    pub spoof_build_dates: bool,
    pub spoof_security_patch: bool,
    pub spoof_libstagefright: bool,
    pub fix_local_tmp: bool,
    pub shrink_log_buffers: bool,
    pub selinux_enforcing: bool,
    pub su_compat: bool,
    pub kernel_umount: bool,
    pub selinux_hide: bool,
    pub remove_rom_props: bool,
    pub remove_pif_props: bool,
    pub disable_child_process_restrictions: bool,
    pub max_saturation: bool,
    pub show_refresh_rate: bool,
    pub verified_boot_hash: String,
    pub custom_uname_release: String,
    pub custom_uname_version: String,
    pub developer_options: Option<bool>,
    pub usb_debugging: Option<bool>,
    pub wireless_debugging: Option<bool>,
    pub sus_paths: Vec<String>,
    pub sus_path_loops: Vec<String>,
    pub sus_maps: Vec<String>,
    pub kernel_umounts: Vec<String>,
    pub redirects: Vec<BreneRedirect>,
}

impl Default for Brene {
    fn default() -> Self {
        Self {
            enabled: true,
            hide_local_tmp: true,
            hide_android_dirs: true,
            hide_non_standard_storage: false,
            hide_ksu_dirs: true,
            hide_custom_recovery: true,
            hide_custom_rom_paths: false,
            hide_custom_rom_paths_deep: false,
            hide_addon_d: false,
            hide_suspicious_pty: false,
            hide_framework_res: false,
            hide_lineage_strings: false,
            hide_third_party_apps: true,
            hide_sus_mounts: true,
            avc_log_spoofing: true,
            spoof_uname: true,
            spoof_cmdline: false,
            spoof_hosts: false,
            spoof_system_properties: true,
            spoof_fingerprint: true,
            spoof_build_dates: true,
            spoof_security_patch: true,
            spoof_libstagefright: false,
            fix_local_tmp: true,
            shrink_log_buffers: true,
            selinux_enforcing: true,
            su_compat: true,
            kernel_umount: true,
            selinux_hide: true,
            remove_rom_props: false,
            remove_pif_props: false,
            disable_child_process_restrictions: true,
            max_saturation: false,
            show_refresh_rate: false,
            verified_boot_hash: String::new(),
            custom_uname_release: String::new(),
            custom_uname_version: String::new(),
            developer_options: None,
            usb_debugging: None,
            wireless_debugging: None,
            sus_paths: Vec::new(),
            sus_path_loops: Vec::new(),
            sus_maps: Vec::new(),
            kernel_umounts: Vec::new(),
            redirects: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct BreneRedirect {
    pub path: String,
    pub destination: String,
    pub uid_scheme: u32,
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
        if self.schema != 1
            || self.rules.len() > 128
            || self.lkm.hidden_modules.len() > 16
            || self.lkm.mount_prefixes.len() > 8
        {
            bail!("Unsupported schema or too many rules");
        }
        self.brene.validate()?;
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

impl Brene {
    fn validate(&self) -> Result<()> {
        for list in [
            &self.sus_paths,
            &self.sus_path_loops,
            &self.sus_maps,
            &self.kernel_umounts,
        ] {
            if list.len() > 256 {
                bail!("Too many BRENE paths");
            }
            for value in list {
                path(value)?;
            }
        }
        if self.redirects.len() > 32 {
            bail!("Too many BRENE redirects");
        }
        for redirect in &self.redirects {
            path(&redirect.path)?;
            path(&redirect.destination)?;
            if redirect.uid_scheme > 4 || redirect.path == redirect.destination {
                bail!("Invalid BRENE redirect");
            }
        }
        text(&self.custom_uname_release, 65)?;
        text(&self.custom_uname_version, 65)?;
        if self.verified_boot_hash.len() > 128
            || self
                .verified_boot_hash
                .chars()
                .any(|c| !c.is_ascii_hexdigit())
        {
            bail!("Invalid verified boot hash");
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn default_is_opt_in() {
        let config = Config::default();
        assert!(!config.automatic);
        assert!(Config::parse(&serde_json::to_string(&config).unwrap()).is_ok());
    }
    #[test]
    fn brene_defaults_are_active_and_old_files_gain_them() {
        let mut old = serde_json::to_value(Config::default()).unwrap();
        old.as_object_mut().unwrap().remove("brene");
        let parsed = Config::parse(&old.to_string()).unwrap();
        assert!(parsed.brene.enabled);
        assert!(parsed.brene.hide_local_tmp);
        assert!(parsed.brene.hide_sus_mounts);
        assert_eq!(parsed.brene.usb_debugging, None);

        let mut off = serde_json::to_value(Config::default()).unwrap();
        off["brene"]["enabled"] = false.into();
        off["brene"]["sus_paths"] = serde_json::json!(["/data/test"]);
        let parsed = Config::parse(&off.to_string()).unwrap();
        assert!(!parsed.brene.enabled);
        assert_eq!(parsed.brene.sus_paths, ["/data/test"]);
    }

    #[test]
    fn strict_import() {
        let original = serde_json::to_value(Config::default()).unwrap();
        let mut unknown = original.clone();
        unknown["execute"] = true.into();
        assert!(Config::parse(&unknown.to_string()).is_err());
        let mut schema = original;
        schema["schema"] = 2.into();
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
