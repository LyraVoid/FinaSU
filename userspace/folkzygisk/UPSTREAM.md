# FolkZygisk upstream provenance

FolkZygisk is FinaSU's built-in adaptation of ReZygisk. Upstream source and
contributor notices are retained; this directory does not contain Zygisk Next.

External provider recognition uses module IDs, not display names. Zygisk Next,
NeoZygisk and NyaZygisk share `zygisksu`; ReZygisk uses `rezygisk`, OnyxZygisk
uses `onyxzygisk`, and YukiZygisk uses
`yukizygisk` ([module.prop](https://github.com/Rouyashiki/YukiZygisk/blob/main/module/module.prop)).
BreZygisk builds under ReZygisk's `rezygisk` ID and also recognises its legacy
`brezygisk` module directory
([customize.sh](https://github.com/rrr333nnn333/BreZygisk/blob/main/module/src/customize.sh)).
NyaZygisk's ID is declared in
https://github.com/HSSkyBoy/NyaZygisk/blob/master/build.gradle.kts. No NyaZygisk
code is imported by this integration.

## Pinned sources

- ReZygisk: https://github.com/PerformanC/ReZygisk
  commit `5b0a02bd92b42382c1ffb42b555df54968c802dd`.
  Imported loader, zygiskd, common.mk, LICENSE and NOTICE.
- CSOLoader: https://github.com/ThePedroo/CSOLoader
  commit `3762c529854666df067c29d0102e6baf5b5af2ab`.
  Vendored under loader/src/external/csoloader with its AGPLv3 LICENSE.
- PLTI: https://github.com/PerformanC/PLTI
  commit `3e7cc55d321e905df92ecba5fffc457b290468b0`.
  Vendored under loader/src/external/plti with its BSD-3-Clause LICENSE.
  Some source comments additionally attribute AGPL code to CSOLoader.

Original authors include The PerformanC Organization and ThePedroo; see
individual source headers and the upstream repositories for contributions.
No copyright notices have been replaced with FinaSU attribution.

## Retained license terms

ReZygisk's LICENSE contains GPLv3 while NOTICE says AGPLv3-or-later.
Both files are preserved verbatim, not silently rewritten. CSOLoader is AGPLv3.
Do not advertise the combined engine as GPL-only. FinaSU retains both license
texts and all original notices; release approval does not rewrite upstream terms
or claim that the discrepancy was resolved by upstream. Binary releases must
provide matching modified source, vendored dependencies and build instructions.
FinaSU modifications recorded below were made on 2026-10-11.

## FinaSU modifications

- Added standalone engine-only build and staging targets. These do not install
  a module, run its installer or start injection.
- Native build output is kept separate from ksud embedded assets until boot
  integration, safe-mode handling and runtime acknowledgement are ready.

- Centralized built-in engine paths in include/folkzygisk_paths.h and moved
  sockets/state to /data/adb/ksu/.zygisk. Daemon/tracer launch paths are absolute.
- Removed module.prop rewriting. The monitor atomically publishes mode-0600
  schema-v1 state.json with boot ID, monitor PID/start ticks, readiness and
  per-ABI daemon/zygote identity and injection acknowledgements. Injection flags
  reset on a new zygote or daemon exit; acknowledgements include the socket peer
  PID and must match the currently observed zygote.
- Readiness is published only after init tracing and both event listeners are
  registered. ksud must still provide runtime directory ownership/labeling,
  single-monitor locking, bounded readiness waiting and liveness validation.

The ksud startup guard is now wired to post-fs-data with a two-second readiness
budget, current-boot attempt journal and a three-failed-boot latch. Safe mode
skips startup; services only inspects health; boot completion clears the counter
only with validated injection. The monitor holds a lifetime flock and does not
inherit it across exec. Daemons no longer implicitly disable kernel_umount.
Public activation is available following maintainer release approval and Android
16/arm64 device acceptance. Missing engine and the
startup failure latch remain operational blockers. There is no development-only
enable feature. Changes affect the next boot, never restart zygote automatically.
Startup clears stale runtime artifacts while preserving and checking the monitor
lock inode, detaches the child session and cgroup before exec, and applies the
built-in SELinux policy. Socket binding removes the stale socket under that lock.

## Native build

```sh
make -C userspace/folkzygisk BUILD_TYPE=release ARCHS=arm64-v8a \
  NDK_PATH="$ANDROID_HOME/ndk/29.0.14206865"
```

Output: build/stage/release/arm64-v8a/{bin,lib64}. Each ABI is staged separately;
packaging compatible 32-bit companions is an explicit later step, not inferred
from the host CPU. Override NDK_PATH to an installed Android NDK.

## ksud asset packaging

`package.py` validates and combines arm64-v8a/armeabi-v7a for aarch64, or
x86_64/x86 for x86_64. It preserves the notices and refuses existing destinations.
No RISC-V engine exists; never package a different architecture as a substitute.

```sh
python3 userspace/folkzygisk/package.py \
  --stage /path/to/build/stage/release \
  --target aarch64 --assets userspace/ksud/bin/aarch64
```

Generated packages are ignored by git and embedded by RustEmbed when prepared.
Release CI prepares supported arm64/x86_64 pairs; RISC-V has no engine and remains
unsupported. Generic asset extraction skips this directory; it must not overwrite
a live engine. The Manager installs a missing engine before enabling it.

## Explicit engine installation

`ksud zygisk install-engine` installs a complete embedded ABI pair and license
texts into immutable SHA-256-addressed folkzygisk-versions directories. It
checks existing contents and atomically switches the folkzygisk symlink only
after installation and SELinux labeling. Older versions are retained; there is
no automatic garbage collection or legacy-directory replacement. Installation
is refused while a validated monitor is alive or startup was attempted this
boot, since running daemons resolve libraries through the engine path. It does
not change provider selection or launch injection.

## External provider precedence

Provider recognition drives the display and the built-in engine's boot
step-aside: the Manager names a known external implementation (`zygisksu`,
`zygisknext`, `rezygisk`, `neozygisk`, `shirokozygisk`, `onyxzygisk`,
`yukizygisk`, `brezygisk`) in the home row and the FolkZygisk settings summary,
and `post_fs_data` lets it win.
Recognition never blocks installation, activation or uninstallation of any
module. Module operations run the same commands as upstream KernelSU and take no
Zygisk lock, so a module archive installs whether or not it registers the Zygisk
API.

An active external provider wins. `post_fs_data` reads the provider set before
touching the boot guard and returns early, so yielding consumes no startup
attempt and cannot latch startup protection. `services` returns early in the
same case and reports no built-in health; the settings summary then shows the
step-aside line rather than an injection state. The next-boot configuration is
left untouched, so removing or disabling the external provider lets the built-in
engine resume on the following boot without re-enabling anything. Only a module
ID from the recognized list is treated as a provider, so a provider built under
another ID would not be detected and both engines would inject.

`ksud zygisk enable` writes `enabled: true` and nothing else. It never creates
`disable` markers on provider modules; when a provider is present it reports
that the provider takes precedence and the built-in engine will step aside. The
only module-tree writes on enable and undo-removal are the marker clears in
`module_markers.rs`: the `disable` (or `remove`) marker is cleared in the
installed and pending copies together, and both are restored if rc regeneration
fails. Clearing only the installed copy would let a bare `enable` or
`undo uninstall` silently revert on boot promotion.

## Kernel unmount coordination

The daemon queries kernel_umount on each process-flags request. When the kernel
owns profile-based unmounting, the injector skips its automatic clean-namespace
switch and initial clean-namespace warmup. Public denylist flags remain intact;
the ownership flag is private and masked from the module API. Explicit plugin
FORCE_DENYLIST_UNMOUNT requests still use the userspace path. An unavailable
ownership query conservatively skips automatic userspace switching. No feature
or app profile is changed. Runtime feature changes between the query and
specialization are not synchronized. Acceptance on other devices, policies and
plugin-requested unmount behavior remains necessary; one device is not universal
compatibility evidence.
