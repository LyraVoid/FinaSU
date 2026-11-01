<div align="center">
<img src="logo.png" width="180" alt="FinaSU logo">

[![Latest Release](https://img.shields.io/github/v/release/LyraVoid/FinaSU?label=Release&logo=github)](https://github.com/LyraVoid/FinaSU/releases/latest)
[![Telegram](https://img.shields.io/badge/Telegram-FinaSU-blue?logo=telegram)](https://t.me/FinaSU)
[![License](https://img.shields.io/github/license/LyraVoid/FinaSU?logo=gnu)](/LICENSE)

</div>

**Language:** [English](./README.md) / [中文](./README_CN.md)

FinaSU is a kernel-based root solution for Android, built on [KernelSU](https://github.com/tiann/KernelSU). It combines kernel-level `su` and root access management with a module system, a built-in Zygisk engine, SUSFS support, and a configurable modern manager.

---

## Overview

FinaSU provides:

- Kernel-based `su` with whitelist-based root access management
- A Magisk-like module system based on overlayfs and metamodules
- App Profile, to confine root power to a per-app policy
- The built-in FolkZygisk engine, with manager controls
- The built-in Sui extension, which serves the Shizuku API to supported apps
- SUSFS support, gated by kernel capability
- Compile-time syscall hook selection: Tracepoint by default, or SUSFS Inline for ARM64 Built-In kernels
- Kernel-level hiding: mount hiding, SELinux hiding, AVC log spoofing, and unmount policy
- `.fpt` themes with FolkPatch-compatible exchange, plus an online theme store
- Multi-page wallpapers, custom fonts, and a launcher icon switcher
- Module repository browsing and one-tap installation
- An in-app language picker with community translations

## Requirements

- An ARM64 (arm64-v8a) Android device; `x86_64` and `riscv64` hooks are also present
- A GKI kernel (5.10+) is recommended; 4.14+ is supported but requires building the kernel manually
- Installation via LKM (a loadable `kernelsu.ko`), a patched boot image, or an AnyKernel3 package

## Manager

- Multiple home layouts, including GridUI and dashboard-style pages
- Configurable theming: colors, wallpapers, and fonts
- A category-based settings hub covering general, appearance, behavior, function, security, and multimedia options
- Optional update checks and boot-time module blocking

## Modules and Extensions

### KernelSU modules

Standard KernelSU modules are managed from the manager. A single active metamodule can provide the mount backend, and FinaSU warns when an enabled metamodule may not be compatible with the built-in engine.

### FolkZygisk

FolkZygisk is a built-in Zygisk engine. The manager exposes its installation and activation controls, and `ksud` steps the built-in engine aside whenever an external Zygisk provider is active, so provider modules install normally.

### Sui

Sui is the built-in Shizuku API backend, for the apps that talk to Shizuku: they reach this extension instead of a separate Shizuku installation. The payload is built by this repository and embedded in ksud, so installation, enable and disable, and repair belong to FinaSU rather than to Sui's own installer, and `ksud` supervises the injected root and shell servers.

The manager hosts the whole interface: the per-app Shizuku permission list, the default permission, the apps hidden from Sui, diagnostics, and the prompt for a real permission request, which is answered over the management channel. Sui's own management UI is not shipped.

### SUSFS

SUSFS management is capability-gated. The controls appear only on kernels that support it, and every configuration change is applied with a receipt. `CONFIG_KSU_SUSFS` enables the SUSFS feature layer; it is separate from the syscall hook backend. Source-integrated ARM64 kernels can select the direct SUSFS Inline backend at build time, while LKM releases continue to use Tracepoint. The selected backend is reported by the kernel ABI and cannot be switched in the Manager.

### Folk-Mount

Folk-Mount is the built-in module mount engine. It can run in auto, built-in, or metamodule mode.

### NoMount

NoMount is the built-in VFS redirection backend: instead of mounting anything it takes over path resolution and directory iteration in a kernel module, so `/proc/mounts` stays clean. The module ships inside ksud (embedded per KMI, built by this repository); the backend needs neither a metamodule nor the `nm` binary.

The manager exposes the driver state, the number of active rules, visibility exclusions and a "mounting off" mode; the backend can be set to automatic, built-in Folk Mount, metamodule only, NoMount or off. Mounting stays on the default `automatic` mode, so NoMount is only entered when it is selected. Choosing NoMount falls back to the built-in engine when the driver is unavailable and says so on the settings page. ksud also records each risky step before it runs: if a NoMount (or metamodule) injection never reached boot completion, the next boot skips that provider and mounts with the built-in engine instead, and the reason lands in `/data/adb/ksu/log/mount.log` — the same bounded, rotated log that records how many rules each boot injected and which directories they landed in, which is what a bug report needs to tell whether an injection touched a boot-critical path. To find out what a stuck boot was waiting for, arm `ksud mount capture on` first (150s after a boot that never finishes, or `ksud mount capture on 60`; `off` disarms): the capture lands in `/data/adb/ksu/log/mount_capture.log` with the blocked-task stacks from `sysrq w`, the end of the kernel log, the D-state tasks and pstore. Visibility is an exception list kept by ksud: every app sees the injected files until it is added, and the NoMount page copies each app profile's "unmount modules" verdict into that list on demand, so later profile changes need another copy.

Directory symlinks are traversed as directories and merged file by file, with cycle detection and per-module path deduplication. A `.replace` marker or opaque directory defaults to file merging; only the explicitly allowlisted `/product/fonts` target may become a directory redirect, and only when its stock target is absent or empty. Partition roots and directories named `lib64`, `lib`, `bin` or `etc` are never directory-replaced. Downgrades are logged, `mount.log` includes `dir_replace=N`, and `ksud nomount inject --dry-run --verbose` labels file redirects and directory replacements separately.

The built-in NoMount provider also starts a detached UI-readiness observer (180 seconds by default). It requires `sys.boot_completed=1`, a running SurfaceFlinger and a stopped boot animation, not just the boot-completed callback. An observation that runs and does not confirm readiness leaves a persistent marker, so the next boot uses the built-in mount engine and records a warning; one that never ran—the device rebooted before the deadline, or the helper died—is treated as inconclusive, is dropped, and NoMount keeps running. These signals do not guarantee a working display, and this is not an in-kernel loader timeout. Set `/data/adb/ksu/mount_ui_guard` to `delay=300` to change the observation deadline (30–900 seconds), or `off` to stop arming new observations. A confirmed-failure marker clears itself once a boot with the built-in engine mounts successfully, so keep the `/data/adb/ksu/log/mount.log` from the failed boot before retrying.

## Security

The manager authenticates itself by APK signature: only the trusted manager and apps the user has explicitly claimed can manage the kernel. Root access is whitelist-based, so only granted apps can reach `su`. See [SECURITY.md](./SECURITY.md) for vulnerability reporting.

## Translation

English and Chinese are the reference languages. Only submit corrections for the target language, and include the entire translated file when adding a new language.

## Download and Installation

1. Download the latest package from the [Releases page](https://github.com/LyraVoid/FinaSU/releases/latest).
2. Install the manager on the device.
3. Follow the in-app flow to install the kernel module, patch a boot image, or flash an AnyKernel3 package.

## Building from Source

The repository ships a local mirror of the CI pipeline:

```bash
./build.sh ksud     # build the Rust userspace daemon
./build.sh lkm      # build the kernel module
./build.sh apk      # build the manager APK (requires the ksud build)
./build.sh all      # full local run
```

See [AGENTS.md](./AGENTS.md) for the per-component build and check commands.

## Credits

- [KernelSU](https://github.com/tiann/KernelSU) - upstream project
- [ReZygisk](https://github.com/PerformanC/ReZygisk) - FolkZygisk is adapted from it
- [YukiSU](https://github.com/Anatdx/YukiSU) - dynamic manager reference
- [BakaSU](https://github.com/Baka-SU/BakaSU) - AnyKernel3 flashing and smaller-feature reference
- [Magisk](https://github.com/topjohnwu/Magisk) - `magiskpolicy` and module concepts
- [SUSFS](https://gitlab.com/simonpunk/susfs4ksu) - kernel-level hiding
- [Sui](https://github.com/XiaoTong6666/Sui) - the built-in Shizuku API backend
- [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) - the Shizuku client API library
- [FolkPatch](https://github.com/LyraVoid/FolkPatch) - theme format and design reference
- [MMRL](https://github.com/MMRLApp/MMRL) - module repository format reference

## License

- Files under the `kernel` directory are [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- All other parts are [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html).

## Community

- Telegram: [@FinaSU](https://t.me/FinaSU)
