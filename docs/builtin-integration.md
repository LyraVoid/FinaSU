# Building FinaSU into a kernel image

FinaSU ships as a loadable module (`CONFIG_KSU=m`) by default. A kernel builder can
also compile it into the image (`CONFIG_KSU=y`); the manager then reports
`Built-In` instead of `LKM`. This document is the supported path for that.

Unlike some forks, FinaSU does not keep a separate kernel-only branch: the hooks are
kprobe/tracepoint based and work in both build modes, so the same tree is used.

## Requirements

| Option | Why |
| --- | --- |
| `CONFIG_KSU=y` | Builds the driver into `vmlinux` instead of a module |
| `CONFIG_KPROBES=y` | `Kconfig` depends on it; the hook manager needs it |
| `CONFIG_KALLSYMS_ALL=y` | Required for runtime lookup of data symbols such as `sys_call_table` |
| `CONFIG_EXT4_FS=y` | `Kconfig` depends on it |
| `CONFIG_HEADERS_INSTALL=y` | Required to compile the preinit runner; without it preinit is disabled |
| `CONFIG_THREAD_INFO_IN_TASK=y` | Only for `CONFIG_KSU_SUSFS` |

Pass `KSU_REQUIRE_PREINIT=1` on the make command line. Without it a missing
`$(objtree)/usr/include` silently turns preinit off.

## Steps

Run from the kernel source root (the directory that contains `drivers/`, or
`common/` for GKI trees):

```sh
./kernel/setup.sh                 # default: clone FinaSU, checkout ${FOLKSU_REF:-dev}
./kernel/setup.sh v1.0.7          # or an explicit tag/commit
./kernel/setup.sh --dir /path/to/FinaSU
./kernel/setup.sh --cleanup
```

The script symlinks the checkout's `kernel/` to `drivers/kernelsu`, then adds
`obj-$(CONFIG_KSU) += kernelsu/` to `drivers/Makefile` and sources
`drivers/kernelsu/Kconfig` from `drivers/Kconfig`.

Then enable the options and build:

```sh
scripts/config --enable KSU
scripts/config --enable KPROBES
scripts/config --enable KALLSYMS_ALL
scripts/config --enable EXT4_FS
scripts/config --enable HEADERS_INSTALL
make ... KSU_REQUIRE_PREINIT=1
```

The produced kernel image has to be flashed the usual way (AnyKernel3, `magiskboot`
unpack/repack, or the vendor boot image tool). Nothing in the repack step is
FinaSU-specific.

## Manager signature

The kernel validates the manager APK against the size and hash compiled into it. The
defaults in `kernel/Kbuild` are placeholders; pass the values of the manager build you
intend to install:

```sh
make ... \
  KSU_EXPECTED_SIZE=0x<hex> \
  KSU_EXPECTED_HASH=<sha256>
```

If the manager is signed with a second release key, use the second slot as well:

```sh
make ... \
  KSU_EXPECTED_SIZE=0x<primary-size> \
  KSU_EXPECTED_HASH=<primary-hash> \
  KSU_EXPECTED_SIZE2=0x<second-size> \
  KSU_EXPECTED_HASH2=<second-hash> \
  KSU_SECOND_SIGNATURE_RELEASE=1
```

`build.sh` derives both slots from `manager/keystore.properties` for the LKM build, so
the values it prints are the ones to reuse here.

A kernel built without the matching signature boots, but the manager is not recognized
and reports the driver as unavailable.

## Version string

`kernel/Kbuild` embeds `KSU_VERSION_FULL`, formatted as
`v<tag>-<short-sha>@<branch>` (for example `v1.0.7-9a8a26fc@dev`). It is only
available when the checkout keeps its `.git` directory; copying `kernel/` alone falls
back to `v0.0.0-00000000@unknown`. Keep the checkout in place or run the script with
`--dir` pointing at a real clone.

The string is served through `KSU_IOCTL_GET_FULL_VERSION` and shown by the manager as
「内核完整版本」 / "Kernel build". It identifies the driver source only; built-in versus
LKM is reported separately through `KSU_GET_INFO_FLAG_LKM`.

## SUSFS

SUSFS support in the kernel requires `CONFIG_KSU_SUSFS`, which this tree exposes under
`KernelSU - SUSFS` in `Kconfig`. The in-tree integration covers:

- `susfs_init()` is called from the driver init.
- `kernel/selinux/selinux.c` provides the SID helpers SUSFS expects
  (`susfs_is_current_*_domain`, `susfs_set_batch_sid`, ...).
- `apply_kernelsu_rules()` adds the zygote `labeledfs` unmount allow rule and refreshes
  the cached SIDs after every policy reload.
- `my_write_context` / `my_write_access` / `ksu_selinux_hide_running` are exported from
  the SELinux hide hooks, so a kernel-side SUSFS patch can call them instead of
  carrying its own copy.
- `kernel/extension/susfs.c` provides the process-state and command-dispatch entry
  points used by the kernel-side patch. Existing kprobe paths remain responsible for
  sucompat, preinit, init.rc and input handling.

What is **not** in this repository is the SUSFS kernel implementation itself
(`fs/susfs.c`, `include/linux/susfs.h`, the per-KMI `fs/*` hooks). Apply it from the
SUSFS source that matches your kernel version. For `android15-6.6`, the minimal
kernel-side patch applies cleanly to a pristine reference tree at upstream commit
`4a4cb1be85a4694372d3efb9420de69982eac29a`. Do not apply the driver-side patch as a
whole; this tree already implements many of its hunks and its hook model differs.

| Upstream patch hunk | Action |
| --- | --- |
| `kernel/Kconfig` SUSFS menu | Skip; the options are already here |
| `kernel/Kbuild` / `kernel/Makefile` defines | Skip; handled by `CONFIG_KSU_SUSFS` and `kernel/extension/susfs.c` |
| `kernel/selinux/rules.c` sid refresh and zygote rule | Skip; already applied |
| `kernel/selinux/selinux.c` / `.h` SID helpers | Skip; already applied |
| `kernel/feature/selinux_hide.c` symbol visibility | Skip; already exported |
| Hook-site rewrites (sucompat, setuid, adb_root, preinit, sulog, supercall) | Do not patch the driver; use the compatible entry points provided here |

The kernel-side patch adds direct calls for the paths it needs. This tree keeps its
kprobe/tracepoint hooks for the existing integration and supplies compatible entry
points for the remaining direct calls.

Then build with `CONFIG_KSU_SUSFS=y` and the sub-options you need.

An `android15-6.6` reference image with all SUSFS sub-options enabled has been built
successfully for this tree. It has not yet been booted or exercised on a device.

Before moving to a newer SUSFS patch set, run the compatibility check from an
`android15-6.6` DDK image with a pristine kernel source:

```sh
scripts/check-susfs-upstream.sh --upstream /path/to/SUSFS
```

The script verifies the pinned commit, Kconfig option set, KernelSU-side symbols,
non-deprecated command dispatch, the reviewed 10-patch failure fingerprint, and that
the 50 patch applies without rejects. Update the pin or failure baseline only after
reviewing the reported hunks.

Current SUSFS patches reference KernelSU symbols such as `my_write_context` directly
and do not export them for modules, so a SUSFS kernel has to carry FinaSU built in
(`CONFIG_KSU=y`). Loading FinaSU as a module against such a kernel fails at modpost.

## Limitations

- Built-in builds are not covered by CI. Validate boot, root grant, module mounts, and
  SELinux state on the target device before shipping.
- Preinit needs the installed UAPI headers; without them, keep `KSU_REQUIRE_PREINIT`
  unset and expect no preinit scripts.
- SUSFS patches are kernel-version specific. Local KMI variants need their own
  adaptation; a patch that applies to one kernel version does not imply the others.
