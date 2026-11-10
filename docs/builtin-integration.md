# Building FinaSU into a kernel image

FinaSU ships as a loadable module (`CONFIG_KSU=m`) by default. A kernel builder can
also compile it into the image (`CONFIG_KSU=y`); the manager then reports
`Built-In` instead of `LKM`. This document is the supported path for that.

Unlike some forks, FinaSU does not keep a separate kernel-only branch. LKM builds use
the default Tracepoint backend; a Built-In kernel can use either Tracepoint or the
SUSFS Inline backend. The backend is selected at compile time, not at runtime.

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
./kernel/setup.sh                 # default: clone FinaSU, checkout ${FINASU_REF:-dev}
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

## Syscall hook backend

`KernelSU syscall hook backend` in `kernel/Kconfig` is a build-time choice:

| Backend | Configuration | Requirements | Entry path |
| --- | --- | --- | --- |
| Tracepoint | `CONFIG_KSU_HOOK_TRACEPOINT=y` | `HAVE_SYSCALL_TRACEPOINTS`; `KSU=m` or `KSU=y` | `sys_enter` tracepoint plus the shared syscall dispatcher and the remaining kprobe hooks |
| SUSFS Inline | `CONFIG_KSU_HOOK_SUSFS_INLINE=y` | `ARM64=y`, `KSU=y`, `KSU_SUSFS=y` | Direct `ksu_handle_*` calls added by the matching SUSFS kernel patch |

Tracepoint is the default. Inline is a Built-In-only backend and does not register the
generic syscall tracepoint, shared dispatcher, or the kprobes whose events are replaced
by direct SUSFS entry points. The driver may still parse `sys_call_table` for
independent hooks; that table lookup is separate from installing the generic
dispatcher.

There is no runtime switch and no automatic fallback. If Inline setup fails, `GET_INFO`
keeps reporting the selected backend but omits `KSU_GET_INFO_FLAG_HOOK_READY`. The
Manager then shows the hook type as abnormal instead of silently switching back to
Tracepoint.

`CONFIG_KSU_SUSFS` enables the SUSFS-facing feature and command layer. It is not the
backend switch:

| Build | `KSU_SUSFS` | Hook backend |
| --- | --- | --- |
| Tracepoint | n | Tracepoint |
| Tracepoint + SUSFS | y | Tracepoint |
| SUSFS Inline | y | SUSFS Inline |

A valid Inline configuration looks like this:

```sh
scripts/config --enable KSU
scripts/config --enable KSU_SUSFS
scripts/config --enable KSU_HOOK_SUSFS_INLINE
make ... KSU_REQUIRE_PREINIT=1
```

`CONFIG_KSU_HOOK_SUSFS_INLINE=y` automatically selects the Kconfig choice instead of
`CONFIG_KSU_HOOK_TRACEPOINT`. Check the final `.config`; do not enable both.

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
  points used by the kernel-side patch.

`CONFIG_KSU_SUSFS` enables this feature layer under either syscall backend. With
Tracepoint, the existing dispatcher and kprobe paths remain active. With SUSFS Inline,
the matching kernel patch must call FinaSU at the sites that would otherwise be
intercepted by kprobes or syscall replacement.

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
| `kernel/selinux/rules.c` `latest_granting` / `reset_avc_cache` (v2.4.0 and later) | Applied when the patch set provides the registration entry points, detected at build time; see `susfs-kernel-contract.md` |
| `kernel/selinux/sepolicy.c` sus type and avd registration (v2.4.0 and later) | Same build-time detection; with an older patch set these call sites are compiled out |
| `kernel/selinux/selinux.c` / `.h` SID helpers | Skip; already applied |
| `kernel/feature/selinux_hide.c` symbol visibility | Skip; already exported |
| Hook-site rewrites (sucompat, setuid, adb_root, preinit, sulog, supercall) | Do not patch the driver; use the compatible entry points provided here |

`susfs-kernel-contract.md` lists, per SUSFS version, which symbols this tree has to
export and which call sites it has to provide. The table above covers the symbols; the
call sites are the part that has no build-time symptom when missing.

For SUSFS Inline, the kernel-side patch must contain the following direct calls. The
build checks the kernel source selected by Kbuild.

| Kernel file | Required entry point | Covered path |
| --- | --- | --- |
| `drivers/input/input.c` | `ksu_handle_input_handle_event` | input and safe-mode trigger |
| `fs/exec.c` | `ksu_handle_execveat`, `ksu_handle_execveat_sucompat`, `ksu_handle_post_execveat_sucompat` | exec, sucompat and post-exec |
| `fs/exec.c` | `ksu_setup_preinit_file`, `ksu_preinit_handle_post_execveat` | preinit handoff and rollback |
| `fs/open.c` | `ksu_handle_faccessat` | `faccessat` sucompat probe |
| `fs/stat.c` | `ksu_handle_stat`, `ksu_handle_vfs_fstat` | `vfs_statx` / `vfs_fstat` and init.rc handling |
| `fs/read_write.c` | `ksu_handle_sys_read` | read-side init.rc handling |
| `kernel/reboot.c` | `ksu_handle_sys_reboot` | supercall FD install and SUSFS commands |
| `kernel/sys.c` | `ksu_handle_setresuid` | setuid, FD install and credential path |

`kernel/Kbuild` runs `kernel/tools/check_susfs_inline.sh` whenever Inline is enabled.
The check also rejects the old conflicting names (`ksu_vfs_read_hook`,
`ksu_input_hook`, `ksu_execveat_hook`, `ksu_init_rc_hook`) and any
`CONFIG_KSU_MANUAL_HOOK` reference at those files. A missing or conflicting call site
fails the build.

Then build with `CONFIG_KSU_SUSFS=y`, `CONFIG_KSU_HOOK_SUSFS_INLINE=y`, and the
SUSFS sub-options you need.

An `android15-6.6` reference image with all SUSFS sub-options enabled has been built
and booted on one test device; the kernel reported `SUSFS Inline` and the Manager
completed a root session. This does not validate other KMIs or every optional feature.

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

Before release, build all supported configurations and treat an unsupported one as a
build failure rather than a fallback:

| Configuration | Expected result |
| --- | --- |
| Tracepoint, `KSU=m` | LKM builds and reports Tracepoint |
| Tracepoint + SUSFS, `KSU=y` | Built-In builds and reports Tracepoint |
| SUSFS Inline, `KSU=y` | Built-In builds and reports SUSFS Inline |
| SUSFS Inline, `KSU=m` | Build fails |
| SUSFS Inline without `CONFIG_KSU_SUSFS=y` | Build fails |
| Inline with missing or conflicting direct call sites | Build fails |

## Limitations

- Built-in builds are not covered by CI. Validate boot, root grant, module mounts, and
  SELinux state on the target device before shipping.
- Preinit needs the installed UAPI headers; without them, keep `KSU_REQUIRE_PREINIT`
  unset and expect no preinit scripts.
- SUSFS Inline is currently limited to ARM64 Built-In kernels. It is not available to
  the packaged LKM.
- SUSFS patches are kernel-version specific. Local KMI variants need their own
  adaptation; a patch that applies to one kernel version does not imply the others.
