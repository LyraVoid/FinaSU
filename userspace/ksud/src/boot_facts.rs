use android_bootimg::parser::{BootImage, BootImageVersion};
use anyhow::{Context, Result, anyhow, bail};
use memmap2::MmapOptions;
use serde::Serialize;
use sha256::digest;
use std::collections::{BTreeMap, BTreeSet};
use std::fs::{self, File};
use std::io::{Seek, SeekFrom, Write};
use std::os::unix::fs::FileExt;
use std::path::{Path, PathBuf};

const BY_NAME_DIR: &str = "/dev/block/by-name";
const MAPPER_DIR: &str = "/dev/block/mapper";
const AVB_FOOTER_MAGIC: &[u8; 4] = b"AVBf";
const AVB_FOOTER_SIZE: u64 = 64;
const BLKGETSIZE64: u32 = 0x8008_1272;
const PARSER: &str = "finasu-boot-facts/1";
const MAX_KERNEL_PROBE: usize = 256 * 1024 * 1024;

#[derive(Debug, Serialize)]
pub struct Report {
    pub schema: u32,
    pub parser: &'static str,
    pub read_at: String,
    pub current_slot: Option<String>,
    pub slot_evidence: Vec<SlotEvidence>,
    pub conflicts: Vec<String>,
    pub slots: Vec<SlotFacts>,
}

#[derive(Debug, Clone, Serialize)]
pub struct SlotEvidence {
    pub source: String,
    pub slot: Option<String>,
    pub current: bool,
    pub status: String,
    pub detail: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct SlotFacts {
    pub id: String,
    pub current: bool,
    pub exists: bool,
    pub readable: bool,
    pub confidence: String,
    pub reason: Option<String>,
    pub images: Vec<ImageFacts>,
    pub fields: FieldSet,
    pub missing_fields: Vec<String>,
}

#[derive(Debug, Default, Serialize)]
pub struct FieldSet {
    pub release: Option<FieldValue>,
    pub version: Option<FieldValue>,
    pub cmdline: Vec<FieldValue>,
    pub bootconfig: Vec<FieldValue>,
}

#[derive(Debug, Clone, Serialize)]
pub struct FieldValue {
    pub value: String,
    pub source: String,
    pub confidence: String,
}

#[derive(Debug, Serialize)]
pub struct ImageFacts {
    pub role: String,
    pub name: String,
    pub path: String,
    pub resolved_path: Option<String>,
    pub exists: bool,
    pub readable: bool,
    pub format: String,
    pub header_version: Option<u32>,
    pub payload_size: Option<u64>,
    pub sha256: Option<String>,
    pub avb: Option<AvbFacts>,
    pub reason: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct AvbFacts {
    pub footer_version: String,
    pub original_image_size: u64,
    pub vbmeta_offset: u64,
    pub vbmeta_size: u64,
    pub signature_verified: bool,
}

#[derive(Debug, Clone)]
struct Partition {
    role: &'static str,
    slot: String,
    name: String,
    path: PathBuf,
    source: String,
}

#[derive(Debug)]
struct ParsedImage {
    format: String,
    header_version: Option<u32>,
    payload_size: u64,
    sha256: String,
    avb: Option<AvbFacts>,
    release: Option<String>,
    version: Option<String>,
    cmdline: Vec<FieldValue>,
    bootconfig: Vec<FieldValue>,
    reason: Option<String>,
}

pub fn collect() -> Report {
    let mut evidence = property_evidence();
    evidence.extend(bootctl_evidence());

    let mut partitions = Vec::new();
    for root in [Path::new(BY_NAME_DIR), Path::new(MAPPER_DIR)] {
        partitions.extend(discover_partitions(root));
    }
    collect_report(evidence, partitions)
}

fn property_evidence() -> Vec<SlotEvidence> {
    #[cfg(target_os = "android")]
    {
        let mut evidence = Vec::new();
        for (source, name) in [
            ("ro.boot.slot_suffix", "ro.boot.slot_suffix"),
            ("ro.boot.slot", "ro.boot.slot"),
        ] {
            match crate::utils::getprop(name) {
                Some(value) if !value.trim().is_empty() => {
                    let slot = normalize_slot(value.trim());
                    evidence.push(SlotEvidence {
                        source: source.to_owned(),
                        slot,
                        current: true,
                        status: "ok".to_owned(),
                        detail: Some(value),
                    });
                }
                Some(value) => evidence.push(SlotEvidence {
                    source: source.to_owned(),
                    slot: None,
                    current: true,
                    status: "empty".to_owned(),
                    detail: Some(value),
                }),
                None => evidence.push(SlotEvidence {
                    source: source.to_owned(),
                    slot: None,
                    current: true,
                    status: "unavailable".to_owned(),
                    detail: None,
                }),
            }
        }
        evidence
    }
    #[cfg(not(target_os = "android"))]
    {
        Vec::new()
    }
}

#[cfg(target_os = "android")]
fn bootctl_evidence() -> Vec<SlotEvidence> {
    let path = Path::new(crate::assets::BOOTCTL_PATH);
    let Some(current) = run_bounded(path, &["get-current-slot"]) else {
        return vec![SlotEvidence {
            source: "bootctl".to_owned(),
            slot: None,
            current: true,
            status: "unavailable".to_owned(),
            detail: None,
        }];
    };
    let index = current.trim();
    if !index.bytes().all(|byte| byte.is_ascii_digit()) {
        return vec![SlotEvidence {
            source: "bootctl".to_owned(),
            slot: None,
            current: true,
            status: "invalid".to_owned(),
            detail: Some(current),
        }];
    }
    let Some(suffix) = run_bounded(path, &["get-suffix", index]) else {
        return vec![SlotEvidence {
            source: "bootctl".to_owned(),
            slot: None,
            current: true,
            status: "suffix_unavailable".to_owned(),
            detail: Some(format!("slot_index={index}")),
        }];
    };
    vec![SlotEvidence {
        source: "bootctl".to_owned(),
        slot: normalize_slot(suffix.trim()),
        current: true,
        status: "ok".to_owned(),
        detail: Some(format!("slot_index={index}, suffix={}", suffix.trim())),
    }]
}

#[cfg(not(target_os = "android"))]
fn bootctl_evidence() -> Vec<SlotEvidence> {
    Vec::new()
}

#[cfg(target_os = "android")]
fn run_bounded(path: &Path, args: &[&str]) -> Option<String> {
    use std::io::Read;
    use std::process::{Command, Stdio};
    use std::time::{Duration, Instant};

    const COMMAND_TIMEOUT: Duration = Duration::from_secs(2);
    let mut child = Command::new(path)
        .args(args)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;
    let deadline = Instant::now() + COMMAND_TIMEOUT;
    loop {
        match child.try_wait() {
            Ok(Some(status)) if status.success() => break,
            Ok(Some(_)) => return None,
            Ok(None) if Instant::now() < deadline => {
                std::thread::sleep(Duration::from_millis(10));
            }
            Ok(None) | Err(_) => {
                let _ = child.kill();
                let _ = child.wait();
                return None;
            }
        }
    }
    let mut output = String::new();
    child.stdout.take()?.read_to_string(&mut output).ok()?;
    Some(output)
}

fn normalize_slot(value: &str) -> Option<String> {
    let slot = value.trim().trim_start_matches('_');
    (!slot.is_empty() && slot.len() <= 32 && slot.bytes().all(|byte| byte.is_ascii_alphanumeric()))
        .then(|| slot.to_ascii_lowercase())
}

fn discover_partitions(root: &Path) -> Vec<Partition> {
    let Ok(entries) = fs::read_dir(root) else {
        return Vec::new();
    };
    let mut partitions = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let name = entry.file_name().to_string_lossy().to_string();
            let (role, slot) = classify_partition(&name)?;
            let path = entry.path();
            Some(Partition {
                role,
                slot,
                name,
                path,
                source: root.file_name().map_or_else(
                    || "partition".to_owned(),
                    |value| value.to_string_lossy().into_owned(),
                ),
            })
        })
        .collect::<Vec<_>>();
    partitions.sort_by(|left, right| left.name.cmp(&right.name));
    partitions
}

fn classify_partition(name: &str) -> Option<(&'static str, String)> {
    for role in ["init_boot", "vendor_boot", "boot"] {
        if name == role {
            return Some((role, "single".to_owned()));
        }
        if let Some(slot) = name.strip_prefix(role).and_then(|s| s.strip_prefix('_'))
            && let Some(slot) = normalize_slot(slot)
        {
            return Some((role, slot));
        }
    }
    None
}

fn collect_report(evidence: Vec<SlotEvidence>, partitions: Vec<Partition>) -> Report {
    let mut evidence = evidence;
    evidence.extend(partitions.iter().map(|partition| SlotEvidence {
        source: partition.source.clone(),
        slot: Some(partition.slot.clone()),
        current: false,
        status: "present".to_owned(),
        detail: Some(partition.name.clone()),
    }));

    let mut slots = BTreeMap::<String, Vec<Partition>>::new();
    for partition in partitions {
        let slot = slots.entry(partition.slot.clone()).or_default();
        if slot
            .iter()
            .any(|current| current.role == partition.role && current.name == partition.name)
        {
            continue;
        }
        slot.push(partition);
    }

    let mut collected = slots
        .into_iter()
        .map(|(id, mut partitions)| {
            partitions.sort_by_key(|partition| role_order(partition.role));
            collect_slot(&id, false, &partitions)
        })
        .collect::<Vec<_>>();

    evidence.extend(collected.iter().flat_map(bootconfig_slot_evidence));

    let signaled = evidence
        .iter()
        .filter(|entry| entry.current)
        .filter_map(|entry| entry.slot.clone())
        .collect::<BTreeSet<_>>();

    let mut conflicts = Vec::new();
    let current_slot = if signaled.len() == 1 {
        signaled.first().cloned()
    } else {
        if signaled.len() > 1 {
            conflicts.push(format!(
                "Current slot evidence disagrees: {}",
                signaled.iter().cloned().collect::<Vec<_>>().join(", ")
            ));
        }
        None
    };

    for slot in &signaled {
        if !collected.iter().any(|facts| &facts.id == slot) {
            collected.push(collect_slot(slot, false, &[]));
        }
        if slot == "a" || slot == "b" {
            let other = if slot == "a" { "b" } else { "a" };
            if !collected.iter().any(|facts| facts.id == other) {
                collected.push(collect_slot(other, false, &[]));
            }
        }
    }
    collected.sort_by(|left, right| left.id.cmp(&right.id));
    let slots = collected
        .into_iter()
        .map(|mut slot| {
            slot.current = current_slot.as_deref() == Some(slot.id.as_str());
            slot
        })
        .collect();

    Report {
        schema: 1,
        parser: PARSER,
        read_at: chrono::Utc::now().to_rfc3339(),
        current_slot,
        slot_evidence: evidence,
        conflicts,
        slots,
    }
}

fn bootconfig_slot_evidence(slot: &SlotFacts) -> Vec<SlotEvidence> {
    slot.fields
        .bootconfig
        .iter()
        .flat_map(|value| {
            value.value.lines().filter_map(|line| {
                let (key, raw) = line.split_once('=')?;
                if key != "androidboot.slot_suffix" && key != "androidboot.slot" {
                    return None;
                }
                Some(SlotEvidence {
                    source: format!("{}:{key}", value.source),
                    slot: normalize_slot(raw),
                    current: false,
                    status: "image_metadata".to_owned(),
                    detail: Some(line.to_owned()),
                })
            })
        })
        .collect()
}

const fn role_order(role: &str) -> u8 {
    match role.as_bytes() {
        b"boot" => 0,
        b"init_boot" => 1,
        _ => 2,
    }
}

fn collect_slot(id: &str, current: bool, partitions: &[Partition]) -> SlotFacts {
    let mut images = Vec::new();
    let mut parsed = Vec::new();
    let mut reasons = Vec::new();
    for partition in partitions {
        let (image, data) = inspect_image(partition);
        if let Some(reason) = &image.reason {
            reasons.push(format!("{}: {reason}", partition.name));
        }
        images.push(image);
        if let Some(data) = data {
            parsed.push((partition, data));
        }
    }

    let mut fields = FieldSet::default();
    let releases = parsed
        .iter()
        .filter_map(|(partition, image)| {
            image.release.as_ref().map(|value| {
                (
                    value.clone(),
                    FieldValue {
                        value: value.clone(),
                        source: format!("{}:linux_banner", partition.name),
                        confidence: "high".to_owned(),
                    },
                )
            })
        })
        .collect::<Vec<_>>();
    let versions = parsed
        .iter()
        .filter_map(|(partition, image)| {
            image.version.as_ref().map(|value| {
                (
                    value.clone(),
                    FieldValue {
                        value: value.clone(),
                        source: format!("{}:linux_banner", partition.name),
                        confidence: "high".to_owned(),
                    },
                )
            })
        })
        .collect::<Vec<_>>();
    fields.release = unique_field(&releases, "release", &mut reasons);
    fields.version = unique_field(&versions, "version", &mut reasons);

    for (partition, image) in &parsed {
        fields
            .cmdline
            .extend(image.cmdline.iter().cloned().map(|mut value| {
                value.source = format!("{}:{value}", partition.name, value = value.source);
                value
            }));
        fields
            .bootconfig
            .extend(image.bootconfig.iter().cloned().map(|mut value| {
                value.source = format!("{}:{value}", partition.name, value = value.source);
                value
            }));
    }
    let mut missing_fields = Vec::new();
    if fields.release.is_none() {
        missing_fields.push("release".to_owned());
    }
    if fields.version.is_none() {
        missing_fields.push("version".to_owned());
    }
    if fields.cmdline.is_empty() {
        missing_fields.push("cmdline".to_owned());
    }
    if fields.bootconfig.is_empty() {
        missing_fields.push("bootconfig".to_owned());
    }

    let exists = !images.is_empty();
    let readable = images.iter().any(|image| image.readable);
    let confidence = if fields.release.is_some() && fields.version.is_some() && reasons.is_empty() {
        "high"
    } else if readable {
        "medium"
    } else {
        "unavailable"
    };
    let reason = if reasons.is_empty() {
        None
    } else {
        Some(reasons.join("; "))
    };

    SlotFacts {
        id: id.to_owned(),
        current,
        exists,
        readable,
        confidence: confidence.to_owned(),
        reason,
        images,
        fields,
        missing_fields,
    }
}

fn unique_field(
    values: &[(String, FieldValue)],
    field: &str,
    reasons: &mut Vec<String>,
) -> Option<FieldValue> {
    let unique = values
        .iter()
        .map(|(value, _)| value)
        .collect::<BTreeSet<_>>();
    match unique.len() {
        0 => None,
        1 => values.first().map(|(_, value)| value.clone()),
        _ => {
            reasons.push(format!("ambiguous {field} values in slot images"));
            None
        }
    }
}

fn inspect_image(partition: &Partition) -> (ImageFacts, Option<ParsedImage>) {
    let resolved_path = fs::canonicalize(&partition.path)
        .ok()
        .map(|path| path.display().to_string());
    let mut image = ImageFacts {
        role: partition.role.to_owned(),
        name: partition.name.clone(),
        path: partition.path.display().to_string(),
        resolved_path,
        exists: true,
        readable: File::open(&partition.path).is_ok(),
        format: "unknown".to_owned(),
        header_version: None,
        payload_size: None,
        sha256: None,
        avb: None,
        reason: None,
    };
    match parse_image(&partition.path) {
        Ok(parsed) => {
            image.format.clone_from(&parsed.format);
            image.header_version = parsed.header_version;
            image.payload_size = Some(parsed.payload_size);
            image.sha256 = Some(parsed.sha256.clone());
            image.avb.clone_from(&parsed.avb);
            image.reason.clone_from(&parsed.reason);
            (image, Some(parsed))
        }
        Err(error) => {
            image.reason = Some(format!("{error:#}"));
            (image, None)
        }
    }
}

fn parse_image(path: &Path) -> Result<ParsedImage> {
    let file = File::open(path).with_context(|| format!("open {}", path.display()))?;
    let source_size = source_size(&file)?;
    if source_size < AVB_FOOTER_SIZE {
        bail!("image is too small");
    }
    let (payload_size, avb) = read_avb(&file, source_size)?;
    if payload_size == 0 || payload_size > source_size {
        bail!("invalid image payload size");
    }
    let payload_size_usize =
        usize::try_from(payload_size).context("image is too large for this device")?;
    let mapping = unsafe { MmapOptions::new().len(payload_size_usize).map(&file) }
        .context("map image read-only")?;
    check_header(&mapping)?;
    let digest = digest(&mapping[..]);
    let boot = std::panic::catch_unwind(|| BootImage::parse(&mapping))
        .map_err(|_| anyhow!("boot image parser panicked"))?
        .context("parse boot image")?;
    let header = boot.get_header();
    let (format, header_version) = match header.get_version() {
        BootImageVersion::Android(version) => (format!("boot_v{version}"), Some(version)),
        BootImageVersion::Vendor(version) => (format!("vendor_boot_v{version}"), Some(version)),
        BootImageVersion::RawRamdisk => ("raw_ramdisk".to_owned(), None),
    };

    let (release, version, kernel_reason) =
        boot.get_blocks()
            .get_kernel()
            .map_or((None, None, None), |kernel| {
                let mut output = ProbeOutput::new(MAX_KERNEL_PROBE);
                let dumped = kernel.dump(&mut output, false);
                if output.exceeded {
                    (None, None, Some("kernel exceeds parser limit".to_owned()))
                } else {
                    match dumped {
                        Ok(()) => match linux_banner(&output.bytes) {
                            Ok((release, version)) => (Some(release), Some(version), None),
                            Err(error) => (None, None, Some(format!("{error:#}"))),
                        },
                        Err(error) => (None, None, Some(format!("decompress kernel: {error:#}"))),
                    }
                }
            });

    let mut cmdline = Vec::new();
    let cmdline_value = header_cmdline(&boot);
    if let Some(value) = cmdline_value {
        cmdline.push(FieldValue {
            value,
            source: "header".to_owned(),
            confidence: "high".to_owned(),
        });
    }
    let (bootconfig, bootconfig_reason) = match bootconfig_value(&boot, &mapping) {
        Ok(Some(value)) => (
            vec![FieldValue {
                value,
                source: "header".to_owned(),
                confidence: "high".to_owned(),
            }],
            None,
        ),
        Ok(None) => (Vec::new(), None),
        Err(error) => (Vec::new(), Some(format!("{error:#}"))),
    };
    let reasons = [kernel_reason, bootconfig_reason]
        .into_iter()
        .flatten()
        .collect::<Vec<_>>();

    Ok(ParsedImage {
        format,
        header_version,
        payload_size,
        sha256: digest,
        avb,
        release,
        version,
        cmdline,
        bootconfig,
        reason: (!reasons.is_empty()).then(|| reasons.join("; ")),
    })
}

struct ProbeOutput {
    bytes: Vec<u8>,
    limit: usize,
    exceeded: bool,
}

impl ProbeOutput {
    const fn new(limit: usize) -> Self {
        Self {
            bytes: Vec::new(),
            limit,
            exceeded: false,
        }
    }
}

impl Write for ProbeOutput {
    fn write(&mut self, buffer: &[u8]) -> std::io::Result<usize> {
        if self.bytes.len().saturating_add(buffer.len()) > self.limit {
            self.exceeded = true;
            return Err(std::io::Error::other("kernel parser output limit exceeded"));
        }
        self.bytes.extend_from_slice(buffer);
        Ok(buffer.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

fn source_size(file: &File) -> Result<u64> {
    let metadata = file.metadata()?;
    if metadata.is_file() {
        return Ok(metadata.len());
    }
    let mut size = 0_u64;
    if unsafe {
        libc::ioctl(
            std::os::fd::AsRawFd::as_raw_fd(file),
            BLKGETSIZE64 as _,
            &raw mut size,
        )
    } == 0
    {
        return Ok(size);
    }
    let mut seekable = file.try_clone()?;
    seekable
        .seek(SeekFrom::End(0))
        .context("determine block device size")
}

fn read_avb(file: &File, source_size: u64) -> Result<(u64, Option<AvbFacts>)> {
    if source_size < AVB_FOOTER_SIZE {
        return Ok((source_size, None));
    }
    let mut footer = [0_u8; AVB_FOOTER_SIZE as usize];
    file.read_exact_at(&mut footer, source_size - AVB_FOOTER_SIZE)
        .context("read AVB footer")?;
    if &footer[..4] != AVB_FOOTER_MAGIC {
        return Ok((source_size, None));
    }
    let major = u32::from_be_bytes(footer[4..8].try_into().unwrap());
    let minor = u32::from_be_bytes(footer[8..12].try_into().unwrap());
    let original_image_size = u64::from_be_bytes(footer[12..20].try_into().unwrap());
    let vbmeta_offset = u64::from_be_bytes(footer[20..28].try_into().unwrap());
    let vbmeta_size = u64::from_be_bytes(footer[28..36].try_into().unwrap());
    if major != 1
        || original_image_size == 0
        || original_image_size > source_size
        || vbmeta_offset > source_size
        || vbmeta_size > source_size.saturating_sub(vbmeta_offset)
    {
        bail!("invalid AVB footer");
    }
    Ok((
        original_image_size,
        Some(AvbFacts {
            footer_version: format!("{major}.{minor}"),
            original_image_size,
            vbmeta_offset,
            vbmeta_size,
            signature_verified: false,
        }),
    ))
}

fn check_header(data: &[u8]) -> Result<()> {
    if data.len() < 48 {
        bail!("truncated boot header");
    }
    if data.starts_with(b"ANDROID!") {
        let version = u32::from_le_bytes(data[40..44].try_into().unwrap());
        let total = match version {
            0 => 1632,
            1 => 1648,
            2 => 1660,
            3 => 1580,
            4 => 1584,
            _ => bail!("unsupported boot header version {version}"),
        };
        if data.len() < total {
            bail!("truncated boot v{version} header");
        }
        return Ok(());
    }
    if data.starts_with(b"VNDRBOOT") {
        let version = u32::from_le_bytes(data[8..12].try_into().unwrap());
        let total = match version {
            3 => 2112,
            4 => 2128,
            _ => bail!("unsupported vendor_boot header version {version}"),
        };
        if data.len() < total {
            bail!("truncated vendor_boot v{version} header");
        }
        return Ok(());
    }
    bail!("unknown boot image format")
}

fn header_cmdline(boot: &BootImage<'_>) -> Option<String> {
    let header = boot.get_header();
    let mut parts = Vec::new();
    if header.has_cmdline()
        && let Some(value) = c_string(header.get_cmdline())
    {
        parts.push(value);
    }
    if header.has_extra_cmdline()
        && let Some(value) = c_string(header.get_extra_cmdline())
    {
        parts.push(value);
    }
    (!parts.is_empty()).then(|| parts.join(" "))
}

fn bootconfig_value(boot: &BootImage<'_>, payload: &[u8]) -> Result<Option<String>> {
    if !matches!(boot.get_header().get_version(), BootImageVersion::Vendor(4)) {
        return Ok(None);
    }
    let header = boot.get_header();
    let size = header.get_bootconfig_size() as usize;
    if size == 0 {
        return Ok(None);
    }
    let page = header.page_size();
    let ramdisk = align_up(header.get_ramdisk_size() as usize, page)
        .context("invalid bootconfig page size")?;
    let dtb =
        align_up(header.get_dtb_size() as usize, page).context("invalid bootconfig page size")?;
    let table = align_up(header.get_vendor_ramdisk_table_size() as usize, page)
        .context("invalid bootconfig page size")?;
    let offset = header
        .hdr_space()
        .checked_add(ramdisk)
        .and_then(|value| value.checked_add(dtb))
        .and_then(|value| value.checked_add(table))
        .context("bootconfig offset overflow")?;
    let bytes = payload
        .get(
            offset
                ..offset
                    .checked_add(size)
                    .context("bootconfig offset overflow")?,
        )
        .context("truncated bootconfig")?;
    let end = bytes
        .iter()
        .position(|byte| *byte == 0)
        .unwrap_or(bytes.len());
    let value = std::str::from_utf8(&bytes[..end])
        .context("bootconfig is not UTF-8")?
        .trim_end();
    Ok((!value.is_empty()).then(|| value.to_owned()))
}

fn align_up(value: usize, alignment: usize) -> Option<usize> {
    if alignment == 0 {
        return None;
    }
    value
        .checked_add(alignment - 1)
        .map(|sum| sum / alignment * alignment)
}

fn c_string(bytes: &[u8]) -> Option<String> {
    let end = bytes.iter().position(|byte| *byte == 0)?;
    std::str::from_utf8(&bytes[..end])
        .ok()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
}

fn linux_banner(kernel: &[u8]) -> Result<(String, String)> {
    const NEEDLE: &[u8] = b"Linux version ";
    let mut candidates = BTreeSet::new();
    let mut offset = 0;
    while let Some(found) = kernel[offset..]
        .windows(NEEDLE.len())
        .position(|window| window == NEEDLE)
    {
        let start = offset + found;
        let end = kernel[start..]
            .iter()
            .position(|byte| matches!(*byte, b'\0' | b'\n'))
            .map_or(kernel.len(), |length| start + length);
        if let Ok(line) = std::str::from_utf8(&kernel[start..end])
            && let Some((release, version)) = parse_banner_line(line)
        {
            candidates.insert((release, version));
        }
        offset = start + NEEDLE.len();
        if offset >= kernel.len() {
            break;
        }
    }
    match candidates.len() {
        0 => bail!("no valid linux_banner"),
        1 => Ok(candidates.into_iter().next().unwrap()),
        _ => bail!("multiple distinct linux_banner values"),
    }
}

fn parse_banner_line(line: &str) -> Option<(String, String)> {
    let release = line
        .strip_prefix("Linux version ")?
        .split_whitespace()
        .next()?;
    if release.len() > 64
        || !release
            .bytes()
            .all(|byte| byte.is_ascii_graphic() || byte == b' ')
    {
        return None;
    }
    let version_start = line.rfind(") #").map(|offset| offset + 2)?;
    let version = line[version_start..].trim();
    if version.is_empty() || version.len() > 64 || version.chars().any(char::is_control) {
        return None;
    }
    Some((release.to_owned(), version.to_owned()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::symlink;
    use tempfile::tempdir;

    fn boot_image(version: u32, kernel: &[u8], cmdline: &str) -> Vec<u8> {
        let page = if version >= 3 { 4096_usize } else { 2048_usize };
        let total = match version {
            0 => 1632,
            1 => 1648,
            2 => 1660,
            3 => 1580,
            4 => 1584,
            _ => unreachable!(),
        };
        let mut image =
            vec![0_u8; align_up(total, page).unwrap() + align_up(kernel.len(), page).unwrap()];
        image[..8].copy_from_slice(b"ANDROID!");
        image[8..12].copy_from_slice(&(kernel.len() as u32).to_le_bytes());
        if version < 3 {
            image[36..40].copy_from_slice(&(page as u32).to_le_bytes());
        }
        image[40..44].copy_from_slice(&version.to_le_bytes());
        let cmdline_offset = if version >= 3 { 44 } else { 64 };
        let cmdline_size = if version >= 3 { 1536 } else { 512 };
        image[cmdline_offset..cmdline_offset + cmdline.len()].copy_from_slice(cmdline.as_bytes());
        assert!(cmdline.len() < cmdline_size);
        let kernel_offset = align_up(total, page).unwrap();
        image[kernel_offset..kernel_offset + kernel.len()].copy_from_slice(kernel);
        image
    }

    fn vendor_image(version: u32, ramdisk: &[u8], bootconfig: &[u8]) -> Vec<u8> {
        let page = 4096_usize;
        let header = match version {
            3 => 2112,
            4 => 2128,
            _ => unreachable!(),
        };
        let total = align_up(header, page).unwrap();
        let ramdisk_area = align_up(ramdisk.len(), page).unwrap();
        let mut image =
            vec![0_u8; total + ramdisk_area + align_up(bootconfig.len(), page).unwrap()];
        image[..8].copy_from_slice(b"VNDRBOOT");
        image[8..12].copy_from_slice(&version.to_le_bytes());
        image[12..16].copy_from_slice(&(page as u32).to_le_bytes());
        image[24..28].copy_from_slice(&(ramdisk.len() as u32).to_le_bytes());
        let cmdline = b"console=ttynull bootconfig";
        image[28..28 + cmdline.len()].copy_from_slice(cmdline);
        image[2096..2100].copy_from_slice(&(header as u32).to_le_bytes());
        if version == 4 {
            image[2124..2128].copy_from_slice(&(bootconfig.len() as u32).to_le_bytes());
        }
        image[total..total + ramdisk.len()].copy_from_slice(ramdisk);
        let config_offset = total + ramdisk_area;
        image[config_offset..config_offset + bootconfig.len()].copy_from_slice(bootconfig);
        image
    }

    const KERNEL: &[u8] = b"prefix\0Linux version 6.6.30-android15-8-test (builder@host) (clang) #1 SMP PREEMPT Tue Jan  1 00:00:00 UTC 2030\0suffix";

    #[test]
    fn parses_kernel_banner_and_cmdline_for_boot_headers_v0_to_v4() {
        for version in 0..=4 {
            let image = boot_image(version, KERNEL, "console=ttynull");
            let parsed = parse_image_from_bytes(&image).unwrap();
            assert_eq!(parsed.release.as_deref(), Some("6.6.30-android15-8-test"));
            assert_eq!(
                parsed.version.as_deref(),
                Some("#1 SMP PREEMPT Tue Jan  1 00:00:00 UTC 2030")
            );
            assert_eq!(parsed.cmdline[0].value, "console=ttynull");
            assert_eq!(parsed.header_version, Some(version));
        }
    }

    #[test]
    fn parses_vendor_boot_v4_cmdline_and_bootconfig() {
        let image = vendor_image(
            4,
            b"ramdisk",
            b"androidboot.hardware=qcom\nandroidboot.slot_suffix=_a\n",
        );
        let parsed = parse_image_from_bytes(&image).unwrap();
        assert_eq!(parsed.header_version, Some(4));
        assert_eq!(parsed.cmdline[0].value, "console=ttynull bootconfig");
        assert_eq!(
            parsed.bootconfig[0].value,
            "androidboot.hardware=qcom\nandroidboot.slot_suffix=_a"
        );
    }

    #[test]
    fn parses_vendor_boot_v3_cmdline_without_inventing_bootconfig() {
        let image = vendor_image(3, b"ramdisk", b"not bootconfig in v3");
        let parsed = parse_image_from_bytes(&image).unwrap();
        assert_eq!(parsed.format, "vendor_boot_v3");
        assert_eq!(parsed.cmdline[0].value, "console=ttynull bootconfig");
        assert!(parsed.bootconfig.is_empty());
    }

    #[test]
    fn truncated_and_unknown_images_are_unavailable_not_panics() {
        assert!(parse_image_from_bytes(b"ANDROID!\0\0").is_err());
        assert!(parse_image_from_bytes(b"not-a-boot-image").is_err());
    }

    #[test]
    fn invalid_bootconfig_does_not_invent_a_value() {
        let image = vendor_image(4, b"ramdisk", &[0xff, 0xfe, 0xfd]);
        let parsed = parse_image_from_bytes(&image).unwrap();
        assert!(parsed.bootconfig.is_empty());
        assert!(
            parsed
                .reason
                .as_deref()
                .is_some_and(|reason| reason.contains("bootconfig is not UTF-8"))
        );
    }

    #[test]
    fn avb_footer_limits_hash_and_parse_to_original_image() {
        let mut image = boot_image(4, KERNEL, "console=ttynull");
        let original_size = image.len() as u64;
        let mut footer = [0_u8; AVB_FOOTER_SIZE as usize];
        footer[..4].copy_from_slice(AVB_FOOTER_MAGIC);
        footer[4..8].copy_from_slice(&1_u32.to_be_bytes());
        footer[8..12].copy_from_slice(&0_u32.to_be_bytes());
        footer[12..20].copy_from_slice(&original_size.to_be_bytes());
        footer[20..28].copy_from_slice(&original_size.to_be_bytes());
        footer[28..36].copy_from_slice(&0_u64.to_be_bytes());
        image.extend_from_slice(&footer);

        let parsed = parse_image_from_bytes(&image).unwrap();
        assert_eq!(parsed.payload_size, original_size);
        assert_eq!(parsed.sha256, digest(&image[..original_size as usize]));
        let avb = parsed.avb.unwrap();
        assert_eq!(avb.original_image_size, original_size);
        assert!(!avb.signature_verified);
    }

    #[test]
    fn discovers_ab_and_partial_slots_without_fixed_names() {
        let root = tempdir().unwrap();
        fs::write(root.path().join("boot_a"), b"a").unwrap();
        fs::write(root.path().join("boot_b"), b"b").unwrap();
        fs::write(root.path().join("vendor_boot_b"), b"v").unwrap();
        fs::write(root.path().join("bootloader"), b"x").unwrap();
        let partitions = discover_partitions(root.path());
        assert!(
            partitions
                .iter()
                .any(|p| p.name == "boot_a" && p.slot == "a")
        );
        assert!(
            partitions
                .iter()
                .any(|p| p.name == "vendor_boot_b" && p.role == "vendor_boot")
        );
        assert!(!partitions.iter().any(|p| p.name == "bootloader"));
    }

    #[test]
    fn conflicting_slot_evidence_is_preserved() {
        let report = collect_report(
            vec![
                SlotEvidence {
                    source: "prop".to_owned(),
                    slot: Some("a".to_owned()),
                    current: true,
                    status: "ok".to_owned(),
                    detail: None,
                },
                SlotEvidence {
                    source: "bootctl".to_owned(),
                    slot: Some("b".to_owned()),
                    current: true,
                    status: "ok".to_owned(),
                    detail: None,
                },
            ],
            Vec::new(),
        );
        assert_eq!(report.current_slot, None);
        assert_eq!(report.conflicts.len(), 1);
        assert_eq!(report.slots.len(), 2);
    }

    #[test]
    fn empty_and_broken_slots_keep_existence_readability_and_reason() {
        let root = tempdir().unwrap();
        let image = boot_image(4, KERNEL, "console=ttynull");
        fs::write(root.path().join("boot_a"), image).unwrap();
        symlink("missing-target", root.path().join("vendor_boot_b")).unwrap();

        let report = collect_report(
            vec![SlotEvidence {
                source: "prop".to_owned(),
                slot: Some("a".to_owned()),
                current: true,
                status: "ok".to_owned(),
                detail: None,
            }],
            discover_partitions(root.path()),
        );
        let slot_a = report.slots.iter().find(|slot| slot.id == "a").unwrap();
        let slot_b = report.slots.iter().find(|slot| slot.id == "b").unwrap();
        assert!(slot_a.readable);
        assert_eq!(
            slot_a
                .fields
                .release
                .as_ref()
                .map(|field| field.value.as_str()),
            Some("6.6.30-android15-8-test")
        );
        assert!(slot_b.exists);
        assert!(!slot_b.readable);
        assert!(
            slot_b
                .reason
                .as_deref()
                .is_some_and(|reason| reason.contains("open"))
        );
    }

    #[test]
    fn bootconfig_slot_metadata_is_evidence_but_not_current_slot_proof() {
        let root = tempdir().unwrap();
        fs::write(
            root.path().join("vendor_boot_a"),
            vendor_image(4, b"ramdisk", b"androidboot.slot_suffix=_a\n"),
        )
        .unwrap();
        let report = collect_report(Vec::new(), discover_partitions(root.path()));
        assert_eq!(report.current_slot, None);
        assert!(report.slot_evidence.iter().any(|entry| {
            entry.status == "image_metadata" && entry.slot.as_deref() == Some("a") && !entry.current
        }));
        let slot = report.slots.iter().find(|slot| slot.id == "a").unwrap();
        assert!(slot.readable);
        assert!(slot.missing_fields.contains(&"release".to_owned()));
        assert!(slot.missing_fields.contains(&"version".to_owned()));
    }

    #[test]
    fn unknown_image_is_readable_but_has_no_original_kernel_values() {
        let root = tempdir().unwrap();
        fs::write(root.path().join("boot_a"), vec![0_u8; 128]).unwrap();
        let report = collect_report(
            vec![SlotEvidence {
                source: "bootctl".to_owned(),
                slot: Some("b".to_owned()),
                current: true,
                status: "ok".to_owned(),
                detail: None,
            }],
            discover_partitions(root.path()),
        );

        assert_eq!(report.current_slot.as_deref(), Some("b"));
        let slot_a = report.slots.iter().find(|slot| slot.id == "a").unwrap();
        assert!(slot_a.exists);
        assert!(slot_a.readable);
        assert_eq!(slot_a.images.first().unwrap().format, "unknown");
        assert!(
            slot_a
                .reason
                .as_deref()
                .is_some_and(|reason| reason.contains("unknown boot image format"))
        );
        assert!(
            report
                .slots
                .iter()
                .find(|slot| slot.id == "b")
                .unwrap()
                .current
        );
    }

    #[test]
    fn parser_output_limit_is_enforced_while_writing() {
        let mut output = ProbeOutput::new(3);
        assert!(output.write_all(b"toolong").is_err());
        assert!(output.exceeded);
        assert!(output.bytes.is_empty());
    }

    #[test]
    fn probing_valid_and_corrupt_images_never_modifies_source_bytes() {
        let root = tempdir().unwrap();
        let valid = boot_image(4, KERNEL, "console=ttynull");
        let corrupt = vec![0x5a_u8; 256];
        fs::write(root.path().join("boot_a"), &valid).unwrap();
        fs::write(root.path().join("boot_b"), &corrupt).unwrap();

        let report = collect_report(
            vec![SlotEvidence {
                source: "prop".to_owned(),
                slot: Some("a".to_owned()),
                current: true,
                status: "ok".to_owned(),
                detail: None,
            }],
            discover_partitions(root.path()),
        );

        assert!(
            report
                .slots
                .iter()
                .any(|slot| slot.id == "a" && slot.readable)
        );
        assert!(
            report.slots.iter().any(|slot| {
                slot.id == "b" && slot.readable && slot.reason.as_deref().is_some()
            })
        );
        assert_eq!(fs::read(root.path().join("boot_a")).unwrap(), valid);
        assert_eq!(fs::read(root.path().join("boot_b")).unwrap(), corrupt);
    }

    #[test]
    fn symlinked_logical_partition_is_recorded() {
        let root = tempdir().unwrap();
        let target = root.path().join("dm-0");
        fs::write(&target, b"x").unwrap();
        symlink(&target, root.path().join("boot_a")).unwrap();
        let partitions = discover_partitions(root.path());
        let boot = partitions
            .iter()
            .find(|partition| partition.name == "boot_a")
            .unwrap();
        assert_eq!(fs::canonicalize(&boot.path).unwrap(), target);
    }

    #[test]
    #[ignore = "requires the recorded PKX110 A/B backup"]
    fn recorded_pkx110_ab_images_match_known_original_values() {
        let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../../.research/cross-slot-validation/backup-20261025-211341");
        for (name, release, hash) in [
            (
                "boot_a.img",
                "6.6.118-android15-8-g93e223c276e7-abogki500782043-4k",
                "32c3b6cf1765126f022de8abb44d42b4e1cd3039a907fcbeef2b50227e098be0",
            ),
            (
                "boot_b.img",
                "6.6.118-android15-8-g2e6b9c3812c5-ab15114928-4k",
                "f8482cd032975d82aa6c882f60f56728b934c3570a013ab08f9ec43bd56c8ea0",
            ),
        ] {
            let parsed = parse_image(&root.join(name)).unwrap();
            assert_eq!(parsed.release.as_deref(), Some(release));
            assert!(
                parsed
                    .version
                    .as_deref()
                    .is_some_and(|value| value.starts_with("#1 SMP"))
            );
            assert_eq!(parsed.sha256, hash);
            assert_eq!(parsed.format, "boot_v4");
        }

        let expected_cmdline = "video=vfb:640x400,bpp=32,memsize=3072000 log_buf_len=2M nosoftlockup console=ttynull qcom_geni_serial.con_enabled=0 nohugevmalloc bootconfig buildvariant=user";
        let expected_bootconfig = "androidboot.hardware=qcom\nandroidboot.memcg=1\nandroidboot.usbcontroller=a600000.dwc3\nandroidboot.load_modules_parallel=true\nandroidboot.hypervisor.protected_vm.supported=true\nandroidboot.vendor.qspa=true\nandroidboot.serialconsole=0";
        for (name, hash) in [
            (
                "vendor_boot_a.img",
                "93a1246d30e658170fd7983a550420c5c97965844c2e0ffc97ae8b9d4f97c70d",
            ),
            (
                "vendor_boot_b.img",
                "7aac3c12c45d44f99017288a6f30277d51a269cbd84d48d75ce99d5f38727ae1",
            ),
        ] {
            let parsed = parse_image(&root.join(name)).unwrap();
            assert_eq!(parsed.format, "vendor_boot_v4");
            assert_eq!(parsed.cmdline[0].value, expected_cmdline);
            assert_eq!(parsed.bootconfig[0].value, expected_bootconfig);
            assert_eq!(parsed.sha256, hash);
        }
    }

    fn parse_image_from_bytes(data: &[u8]) -> Result<ParsedImage> {
        let directory = tempdir()?;
        let path = directory.path().join("image");
        fs::write(&path, data)?;
        parse_image(&path)
    }
}
