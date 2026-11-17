//! NoMount rule generation from the module directory.
//!
//! Directory links are merged recursively. Directory replacement is restricted
//! to unprotected targets that are absent or empty.

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

use crate::nomount::Rule;

/// Partition directories probed inside each module, in upstream order.
pub const TARGET_PARTITIONS: [&str; 21] = [
    "system",
    "system_ext",
    "vendor",
    "odm",
    "product",
    "apex",
    "oem",
    "optics",
    "prism",
    "mi_ext",
    "my_bigball",
    "my_carrier",
    "my_company",
    "my_engineering",
    "my_heytap",
    "my_manifest",
    "my_preload",
    "my_product",
    "my_region",
    "my_reserve",
    "my_stock",
];

/// Names that are remapped out of `/system` when a module nests them there.
pub const REMAPPED_PARTITIONS: [&str; 9] = [
    "vendor",
    "system_ext",
    "product",
    "odm",
    "apex",
    "oem",
    "optics",
    "prism",
    "mi_ext",
];

/// Prefix of OEM partitions that are always remapped out of `/system`.
pub const REMAPPED_PREFIX: &str = "my_";

/// Marker meaning "replace the directory that contains this file".
pub const REPLACE_MARKER: &str = ".replace";

/// Marker meaning "remove the directory that contains this file".
pub const REMOVE_MARKER: &str = ".remove";

/// Module lifecycle marks that keep a module out of the injection.
pub const SKIP_MARKS: [&str; 3] = ["disable", "remove", "skip_mount"];

/// Entry classification used by the walker.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EntryKind {
    File,
    Dir,
    Symlink,
    /// A character device with `rdev == 0`: Magisk's "delete this path".
    Whiteout,
    /// Sockets, FIFOs and block devices: not injectable.
    Other,
}

/// Filesystem access, injected so tests can drive every case without root.
pub trait PlanFs {
    /// Resolve a directory, following symlinks, or return `None` for other kinds.
    ///
    /// # Errors
    /// Returns an error if resolving the path fails.
    fn canonical_dir(&self, path: &Path) -> Result<Option<PathBuf>>;

    /// Entry names inside `dir`, unsorted.
    ///
    /// # Errors
    /// Returns an error when the directory cannot be read.
    fn read_dir(&self, dir: &Path) -> Result<Vec<String>>;

    /// Classify `path` without following a final symlink.
    ///
    /// # Errors
    /// Returns an error when the path cannot be stat'ed for a reason other than
    /// "not found".
    fn kind(&self, path: &Path) -> Result<EntryKind>;

    /// Whether `path` exists (following symlinks).
    ///
    /// # Errors
    /// Returns an error when the check itself fails.
    fn exists(&self, path: &Path) -> Result<bool>;

    /// Whether a directory carries `trusted.overlay.opaque=y`.
    ///
    /// # Errors
    /// Returns an error when the attribute cannot be read.
    fn is_opaque(&self, path: &Path) -> Result<bool>;
}

/// The real filesystem.
pub struct StdFs<F> {
    opaque: F,
}

impl<F: Fn(&Path) -> Result<bool>> StdFs<F> {
    pub const fn new(opaque: F) -> Self {
        Self { opaque }
    }
}

impl<F: Fn(&Path) -> Result<bool>> PlanFs for StdFs<F> {
    fn canonical_dir(&self, path: &Path) -> Result<Option<PathBuf>> {
        match std::fs::metadata(path) {
            Ok(meta) if meta.is_dir() => Ok(Some(std::fs::canonicalize(path)?)),
            Ok(_) => Ok(None),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(error) => Err(error).with_context(|| format!("resolve {}", path.display())),
        }
    }

    fn read_dir(&self, dir: &Path) -> Result<Vec<String>> {
        let mut names = Vec::new();
        for entry in std::fs::read_dir(dir).with_context(|| format!("read {}", dir.display()))? {
            let entry = entry.with_context(|| format!("read entry in {}", dir.display()))?;
            if let Some(name) = entry.file_name().to_str() {
                names.push(name.to_string());
            }
        }
        Ok(names)
    }

    fn kind(&self, path: &Path) -> Result<EntryKind> {
        let meta = match std::fs::symlink_metadata(path) {
            Ok(meta) => meta,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                return Ok(EntryKind::Other);
            }
            Err(error) => return Err(error).with_context(|| format!("stat {}", path.display())),
        };
        let kind = if meta.is_symlink() {
            EntryKind::Symlink
        } else if meta.is_dir() {
            EntryKind::Dir
        } else if meta.is_file() {
            EntryKind::File
        } else if is_whiteout(&meta) {
            EntryKind::Whiteout
        } else {
            EntryKind::Other
        };
        Ok(kind)
    }

    fn exists(&self, path: &Path) -> Result<bool> {
        Ok(path.exists())
    }

    fn is_opaque(&self, path: &Path) -> Result<bool> {
        (self.opaque)(path)
    }
}

/// Whether a character device is the `0/0` whiteout marker.
#[must_use]
pub fn is_whiteout(meta: &std::fs::Metadata) -> bool {
    use std::os::unix::fs::{FileTypeExt, MetadataExt};
    meta.file_type().is_char_device() && meta.rdev() == 0
}

/// Where the module tree lives and which partitions to consider.
#[derive(Debug, Clone, Copy)]
pub struct PlanConfig<'a> {
    pub modules_dir: &'a Path,
    /// Real root, normally `/`.
    pub root: &'a Path,
    /// Real system root, normally `/system`.
    pub system_root: &'a Path,
    pub partitions: &'a [&'a str],
}

impl<'a> PlanConfig<'a> {
    #[must_use]
    pub const fn new(modules_dir: &'a Path, root: &'a Path, system_root: &'a Path) -> Self {
        PlanConfig {
            modules_dir,
            root,
            system_root,
            partitions: &TARGET_PARTITIONS,
        }
    }
}

/// Result of one plan: the ordered rules plus what was skipped.
#[derive(Debug, Default, Clone)]
pub struct Plan {
    pub rules: Vec<Rule>,
    pub directory_replacements: HashSet<String>,
    /// Module ids that were skipped, with the reason.
    pub skipped: Vec<(String, String)>,
}

/// Map a module-relative path (for example `system/etc/hosts`) to its virtual
/// path, remapping nested partitions the way upstream's `rule-paths.sh` does.
#[must_use]
pub fn virtual_path(relative: &str) -> String {
    if let Some(rest) = relative.strip_prefix("system/") {
        let (first, tail) = rest.split_once('/').unwrap_or((rest, ""));
        if is_remapped(first) {
            return if tail.is_empty() {
                format!("/{first}")
            } else {
                format!("/{first}/{tail}")
            };
        }
    }
    format!("/{relative}")
}

fn is_remapped(name: &str) -> bool {
    REMAPPED_PARTITIONS.contains(&name) || name.starts_with(REMAPPED_PREFIX)
}

/// Build the rule list for every mountable module.
///
/// # Errors
/// Returns an error when the modules directory or a reachable module subtree
/// cannot be read.
pub fn collect_rules(fs: &impl PlanFs, cfg: &PlanConfig<'_>) -> Result<Plan> {
    collect_rules_filtered(fs, cfg, None, true)
}

/// Build the rule list for one module, for the per-module hot load/unload path.
///
/// # Errors
/// Returns an error when the modules directory or the module subtree cannot be
/// read. An unknown id yields an empty plan.
pub fn collect_rules_for(fs: &impl PlanFs, cfg: &PlanConfig<'_>, id: &str) -> Result<Plan> {
    collect_rules_filtered(fs, cfg, Some(id), true)
}

/// Every rule a module would contribute, ignoring its `disable`/`skip_mount`
/// marks: the paths a hot unload has to remove even though the module is off.
///
/// # Errors
/// Returns an error when the module subtree cannot be read.
pub fn collect_rules_ignoring_marks(
    fs: &impl PlanFs,
    cfg: &PlanConfig<'_>,
    id: &str,
) -> Result<Plan> {
    collect_rules_filtered(fs, cfg, Some(id), false)
}

/// Rule count for every module that contributes rules, in plan order.
///
/// # Errors
/// Returns an error when the modules directory or a module subtree cannot be
/// read.
pub fn rule_counts(fs: &impl PlanFs, cfg: &PlanConfig<'_>) -> Result<Vec<(String, usize)>> {
    let mut counts = Vec::new();
    for id in module_ids(fs, cfg)? {
        let plan = collect_rules_filtered(fs, cfg, Some(&id), true)?;
        if !plan.rules.is_empty() {
            counts.push((id, plan.rules.len()));
        }
    }
    Ok(counts)
}

fn module_ids(fs: &impl PlanFs, cfg: &PlanConfig<'_>) -> Result<Vec<String>> {
    let mut modules = fs.read_dir(cfg.modules_dir)?;
    modules.sort_unstable();
    modules.retain(|id| matches!(fs.kind(&cfg.modules_dir.join(id)), Ok(EntryKind::Dir)));
    Ok(modules)
}

fn collect_rules_filtered(
    fs: &impl PlanFs,
    cfg: &PlanConfig<'_>,
    only: Option<&str>,
    honor_marks: bool,
) -> Result<Plan> {
    let mut plan = Plan::default();

    for id in module_ids(fs, cfg)? {
        if only.is_some_and(|only| only != id) {
            continue;
        }
        let module = cfg.modules_dir.join(&id);
        if honor_marks
            && let Some(mark) = SKIP_MARKS
                .iter()
                .find(|mark| fs.exists(&module.join(mark)).unwrap_or(false))
        {
            plan.skipped.push((id, (*mark).to_string()));
            continue;
        }
        let start = plan.rules.len();
        let mut walker = Walker {
            fs,
            cfg,
            id: &id,
            plan: &mut plan,
            ancestors: HashSet::new(),
            seen: HashMap::new(),
        };
        for partition in cfg.partitions {
            let dir = module.join(partition);
            if !matches!(fs.kind(&dir)?, EntryKind::Dir | EntryKind::Symlink) {
                continue;
            }
            let real = cfg.root.join(partition);
            let nested = cfg.system_root.join(partition);
            if !fs.exists(&real)? && !fs.exists(&nested)? {
                continue;
            }
            walker.walk(&dir, partition)?;
        }
        if plan.rules.len() == start {
            plan.skipped.push((id, "no partition content".to_string()));
        }
    }
    Ok(plan)
}

struct Walker<'a, F> {
    fs: &'a F,
    cfg: &'a PlanConfig<'a>,
    id: &'a str,
    plan: &'a mut Plan,
    ancestors: HashSet<PathBuf>,
    seen: HashMap<String, usize>,
}

fn protected_directory(path: &str) -> bool {
    TARGET_PARTITIONS
        .iter()
        .any(|part| path == format!("/{part}"))
        || Path::new(path).file_name().is_some_and(|name| {
            ["lib64", "lib", "bin", "etc"]
                .iter()
                .any(|item| name == *item)
        })
}

impl<F: PlanFs> Walker<'_, F> {
    fn note(&mut self, reason: &str, path: &str) {
        self.plan
            .skipped
            .push((self.id.to_string(), format!("{reason}:{path}")));
    }

    fn push(&mut self, rule: Rule) {
        if let Some(index) = self.seen.get(&rule.virtual_path) {
            self.plan.rules[*index] = rule;
        } else {
            self.seen
                .insert(rule.virtual_path.clone(), self.plan.rules.len());
            self.plan.rules.push(rule);
        }
    }

    fn walk(&mut self, dir: &Path, relative: &str) -> Result<()> {
        let target = virtual_path(relative);
        let canonical = match self.fs.canonical_dir(dir) {
            Ok(Some(path)) => path,
            Ok(None) => return Ok(()),
            Err(_) if self.fs.kind(dir)? == EntryKind::Symlink => {
                self.note("symlink-unresolved", &target);
                return Ok(());
            }
            Err(error) => return Err(error),
        };
        if self.ancestors.len() >= 64 || !self.ancestors.insert(canonical.clone()) {
            self.note("directory-cycle-or-depth", &target);
            return Ok(());
        }
        let result = self.walk_contents(dir, relative, &target);
        self.ancestors.remove(&canonical);
        result
    }

    fn walk_contents(&mut self, dir: &Path, relative: &str, target: &str) -> Result<()> {
        let mut names = self.fs.read_dir(dir)?;
        names.sort_unstable();
        if names.iter().any(|name| name == REMOVE_MARKER) {
            if protected_directory(target) {
                self.note("dir-remove-blocked", target);
            } else {
                self.push(Rule::whiteout(target));
                return Ok(());
            }
        }
        let replace = names.iter().any(|name| name == REPLACE_MARKER) || self.fs.is_opaque(dir)?;
        if replace {
            let stock = self.cfg.root.join(target.trim_start_matches('/'));
            let blocked = if protected_directory(target) || target != "/product/fonts" {
                Some("dir-replace-blocked")
            } else if self.fs.exists(&stock)?
                && (self.fs.canonical_dir(&stock)?.is_none()
                    || !self.fs.read_dir(&stock)?.is_empty())
            {
                Some("dir-replace-nonempty")
            } else {
                None
            };
            if let Some(reason) = blocked {
                self.note(reason, target);
            } else {
                self.plan.directory_replacements.insert(target.to_string());
                self.push(Rule::redirect(target, dir.to_string_lossy().to_string()));
                return Ok(());
            }
        }
        for name in names {
            if name == REPLACE_MARKER || name == REMOVE_MARKER {
                continue;
            }
            let child = dir.join(&name);
            let child_relative = format!("{relative}/{name}");
            let kind = self.fs.kind(&child)?;
            if matches!(kind, EntryKind::Dir | EntryKind::Symlink) {
                match self.fs.canonical_dir(&child) {
                    Ok(Some(_)) => {
                        self.walk(&child, &child_relative)?;
                        continue;
                    }
                    Err(_) if kind == EntryKind::Symlink => {
                        self.note("symlink-unresolved", &virtual_path(&child_relative));
                        continue;
                    }
                    Err(error) => return Err(error),
                    Ok(None) => {}
                }
            }
            match kind {
                EntryKind::File | EntryKind::Symlink => self.push(Rule::redirect(
                    virtual_path(&child_relative),
                    child.to_string_lossy().to_string(),
                )),
                EntryKind::Whiteout => self.push(Rule::whiteout(virtual_path(&child_relative))),
                EntryKind::Dir | EntryKind::Other => {}
            }
        }
        Ok(())
    }
}

#[cfg(test)]
#[path = "nomount_plan_tests.rs"]
mod tests;
