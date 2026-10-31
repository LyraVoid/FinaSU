// SPDX-License-Identifier: GPL-2.0-only
// Wire definitions for the SUSFS v2.3.0 control interface. Keep native C
// alignment, including the trailing error sentinel.
use anyhow::{Result, bail};

#[repr(C)]
pub struct Text<const N: usize> {
    pub text: [u8; N],
    pub err: i32,
}

impl<const N: usize> Text<N> {
    pub const fn empty() -> Self {
        Self {
            text: [0; N],
            err: 126,
        }
    }
}

#[repr(C)]
pub struct Toggle {
    pub enabled: bool,
    pub err: i32,
}

#[repr(C)]
pub struct Redirect {
    pub target: [u8; 256],
    pub destination: [u8; 256],
    pub uid_scheme: i32,
    pub err: i32,
}

#[repr(C)]
pub struct Uname {
    pub release: [u8; 65],
    pub version: [u8; 65],
    pub err: i32,
}

#[repr(C)]
#[derive(Clone)]
pub struct Kstat {
    pub is_static: i32,
    pub target_ino: libc::c_ulong,
    pub path: [u8; 256],
    pub ino: libc::c_ulong,
    pub dev: libc::c_ulong,
    pub nlink: u32,
    pub size: i64,
    pub atime_sec: libc::c_long,
    pub atime_nsec: libc::c_ulong,
    pub mtime_sec: libc::c_long,
    pub mtime_nsec: libc::c_ulong,
    pub ctime_sec: libc::c_long,
    pub ctime_nsec: libc::c_ulong,
    pub blocks: i64,
    pub blksize: libc::c_long,
    pub flags: i32,
    pub err: i32,
}

impl Default for Kstat {
    fn default() -> Self {
        Self {
            is_static: 0,
            target_ino: 0,
            path: [0; 256],
            ino: 0,
            dev: 0,
            nlink: 0,
            size: 0,
            atime_sec: 0,
            atime_nsec: 0,
            mtime_sec: 0,
            mtime_nsec: 0,
            ctime_sec: 0,
            ctime_nsec: 0,
            blocks: 0,
            blksize: 0,
            flags: 0,
            err: 126,
        }
    }
}

pub fn static_kstat_profile(detection: &super::Detection) -> bool {
    detection.supports("CONFIG_KSU_SUSFS_SUS_KSTAT")
}

pub fn static_kstat_fields(supported: bool) -> Vec<&'static str> {
    if supported {
        vec![
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
    } else {
        Vec::new()
    }
}

#[allow(clippy::useless_conversion)]
pub fn static_kstat_payload(
    path: &str,
    fields: &super::model::StaticKstat,
    metadata: &std::fs::Metadata,
) -> Result<Kstat> {
    use std::os::unix::fs::MetadataExt;
    fields.validate()?;
    Ok(Kstat {
        is_static: 1,
        target_ino: metadata.ino().try_into()?,
        path: bytes(path)?,
        ino: fields.ino.unwrap_or_else(|| metadata.ino()),
        dev: fields.dev.unwrap_or_else(|| metadata.dev()),
        nlink: fields.nlink.unwrap_or_else(|| metadata.nlink() as u32),
        size: fields.size.unwrap_or(metadata.size().try_into()?),
        atime_sec: fields.atime_sec.unwrap_or_else(|| metadata.atime()),
        atime_nsec: fields.atime_nsec.map_or_else(
            || metadata.atime_nsec().try_into(),
            |value| Ok(value.into()),
        )?,
        mtime_sec: fields.mtime_sec.unwrap_or_else(|| metadata.mtime()),
        mtime_nsec: fields.mtime_nsec.map_or_else(
            || metadata.mtime_nsec().try_into(),
            |value| Ok(value.into()),
        )?,
        ctime_sec: fields
            .ctime_sec
            .unwrap_or_else(|| metadata.ctime())
            .try_into()?,
        ctime_nsec: fields.ctime_nsec.map_or_else(
            || metadata.ctime_nsec().try_into(),
            |value| Ok(value.into()),
        )?,
        blocks: fields.blocks.unwrap_or(metadata.blocks().try_into()?),
        blksize: fields.blksize.unwrap_or(metadata.blksize().try_into()?),
        flags: fields.flags(),
        ..Kstat::default()
    })
}

pub fn call_checked<T>(command: u32, payload: &mut T) -> Result<()> {
    let result = unsafe {
        libc::syscall(
            libc::SYS_reboot,
            0xdead_beef_u32,
            0xfafa_fafa_u32,
            command,
            std::ptr::from_mut(payload),
        )
    };
    if result != 0 {
        bail!("SUSFS syscall failed: {}", std::io::Error::last_os_error());
    }
    Ok(())
}

pub fn call<T>(command: u32, payload: &mut T) {
    // Invalid reboot magics are rejected by stock kernels; never use real reboot magics.
    // Payload remains owned and live until the syscall/task_work has returned.
    unsafe {
        libc::syscall(
            libc::SYS_reboot,
            0xdead_beef_u32,
            0xfafa_fafa_u32,
            command,
            std::ptr::from_mut(payload),
        );
    }
}

pub fn checked(err: i32) -> Result<()> {
    if err != 0 {
        bail!("SUSFS returned error {err}");
    }
    Ok(())
}

pub fn bytes<const N: usize>(value: &str) -> Result<[u8; N]> {
    if value.len() >= N || value.as_bytes().contains(&0) {
        bail!("SUSFS field exceeds ABI length");
    }
    let mut result = [0; N];
    result[..value.len()].copy_from_slice(value.as_bytes());
    Ok(result)
}

pub fn string(value: &[u8]) -> Option<String> {
    let end = value.iter().position(|c| *c == 0)?;
    std::str::from_utf8(&value[..end]).ok().map(str::to_owned)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn static_payload_only_accepts_verified_fields() {
        let file = tempfile::NamedTempFile::new().unwrap();
        let metadata = file.as_file().metadata().unwrap();
        let fields = super::super::model::StaticKstat {
            size: Some(0),
            ..Default::default()
        };
        let payload = static_kstat_payload("/data/test", &fields, &metadata).unwrap();
        assert_eq!(payload.size, 0);
        assert_eq!(payload.flags, 0x8);
        assert_eq!(payload.err, 126);
        let combined = super::super::model::StaticKstat {
            size: Some(0),
            ctime_sec: Some(-1),
            ..Default::default()
        };
        let payload = static_kstat_payload("/data/test", &combined, &metadata).unwrap();
        assert_eq!(payload.flags, 0x108);
        let all = super::super::model::StaticKstat {
            ino: Some(1),
            dev: Some(2),
            nlink: Some(3),
            size: Some(4),
            atime_sec: Some(5),
            atime_nsec: Some(6),
            mtime_sec: Some(7),
            mtime_nsec: Some(8),
            ctime_sec: Some(9),
            ctime_nsec: Some(10),
            blocks: Some(11),
            blksize: Some(12),
        };
        let payload = static_kstat_payload("/data/test", &all, &metadata).unwrap();
        assert_eq!(payload.flags, 0xfff);
        assert_eq!(payload.ino, 1);
        assert_eq!(payload.dev, 2);
        assert_eq!(payload.nlink, 3);
        assert_eq!(payload.size, 4);
        assert_eq!(payload.atime_sec, 5);
        assert_eq!(payload.atime_nsec, 6);
        assert_eq!(payload.mtime_sec, 7);
        assert_eq!(payload.mtime_nsec, 8);
        assert_eq!(payload.ctime_sec, 9);
        assert_eq!(payload.ctime_nsec, 10);
        assert_eq!(payload.blocks, 11);
        assert_eq!(payload.blksize, 12);
        assert_eq!(
            static_kstat_fields(true),
            [
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
        );
        assert!(static_kstat_fields(false).is_empty());
    }
    #[test]
    fn arm64_layouts() {
        assert_eq!(size_of::<Text<16>>(), 20);
        assert_eq!(std::mem::offset_of!(Text<8192>, err), 8192);
        assert_eq!(size_of::<Toggle>(), 8);
        assert_eq!(size_of::<Redirect>(), 520);
        assert_eq!(std::mem::offset_of!(Uname, err), 132);
        if size_of::<libc::c_ulong>() == 8 {
            assert_eq!(std::mem::offset_of!(Kstat, target_ino), 8);
            assert_eq!(std::mem::offset_of!(Kstat, flags), 368);
            assert_eq!(std::mem::offset_of!(Kstat, err), 372);
            assert_eq!(size_of::<Kstat>(), 376);
        }
    }
    #[test]
    fn strings_are_bounded_and_terminated() {
        assert!(bytes::<16>(&"x".repeat(16)).is_err());
        assert!(bytes::<16>("a\0b").is_err());
        assert!(string(&[b'x'; 16]).is_none());
    }
}
