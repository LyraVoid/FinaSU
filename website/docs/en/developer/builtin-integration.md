---
title: Built-In Kernel Integration
description: Combine the FinaSU driver, preinit and trust certificates into a target kernel image
createTime: 2026/10/25 00:00:00
permalink: /en/developer/builtin-integration/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Built-In Kernel Integration

<Badge text="CONFIG_KSU=y" type="tip" /> <Badge text="Signature must match" type="warning" /> <Badge text="Back up the boot image" type="danger" />

Built-In compiles the FinaSU driver directly into the target kernel image instead of loading it via `kernelsu.ko` after boot. It shares the same driver source, hooks and Manager interfaces with LKM; ==the differences are mainly the build method, the location in the boot image and the failure recovery path==.

:::: table Built-In vs LKM

| Item | Built-In | LKM |
| --- | --- | --- |
| Kernel configuration | `CONFIG_KSU=y` | `CONFIG_KSU=m` |
| Driver location | `vmlinux` / kernel image | standalone `kernelsu.ko` |
| Installation medium | replace the kernel image | patch the ramdisk, usually `init_boot` or `boot` |
| Kernel updates | re-integrate on every kernel change | still needs an LKM matching the new kernel/KMI |
| Impact of failure | the system may not boot | recovery through the boot image is usually still possible |

::::

The switch procedure for a device that already has root is not repeated here. First follow [Switching to FinaSU](/en/guide/switch/kernelsu/) to find out where the old driver lives, then choose LKM or Built-In.

## Prerequisites

Built-In must be built from the kernel source that the target device actually runs. The same version number is not enough; vendor kernel configuration, exported symbols, in-tree modifications and source branches may all differ.

Prepare:

- Kernel source and a build configuration that match the device firmware.
- A Linux kernel build environment, the target cross compiler and the correct `ARCH`.
- A recoverable boot image, plus an explicit flash and rollback procedure.
- Signature certificate information matching the Manager APK you intend to install.
- The target kernel's `defconfig` or the vendor's full build parameters.

The hard Kconfig dependencies are:

:::: table Required Options

| Option | Reason |
| --- | --- |
| `CONFIG_KSU=y` | compile the driver into the kernel |
| `CONFIG_KPROBES=y` | the driver installs kprobe/tracepoint hooks |
| `CONFIG_EXT4_FS=y` | the driver depends on `ext4_unregister_sysfs` |
| `CONFIG_HEADERS_INSTALL=y` | install the target UAPI headers, needed to build the preinit runner |

::::

`CONFIG_KSU_SUSFS` also requires `CONFIG_KSU=y` and `CONFIG_THREAD_INFO_IN_TASK=y`. Enable it only when you plan to attach to an existing kernel-side SUSFS implementation.

## Integrate the Source into the Kernel Tree

Run `kernel/setup.sh` from the repository, with the kernel source root as the working directory. The script detects `common/drivers` or `drivers`, creates the `kernelsu` symlink, and adds the driver entry to `Makefile` and `Kconfig`.

```sh
cd /path/to/kernel-source

# use the current FinaSU checkout
/path/to/FinaSU/kernel/setup.sh --dir /path/to/FinaSU

# or let the script clone/update on its own
/path/to/FinaSU/kernel/setup.sh
```

Remove the integration changes the script made to the kernel tree:

```sh
/path/to/FinaSU/kernel/setup.sh --cleanup
```

::: warning Cleanup Does Not Restore Arbitrary Vendor Changes

`--cleanup` only removes the symlinks created by the script and the Makefile/Kconfig lines it added itself. Whether vendor changes, other patches and the source clone are kept is still up to you to check.

:::

## Configure and Build

Start from the device's existing configuration instead of overwriting the vendor config with a generic `defconfig`:

```sh
scripts/config --enable KSU
scripts/config --enable KPROBES
scripts/config --enable KALLSYMS_ALL
scripts/config --enable EXT4_FS
scripts/config --enable HEADERS_INSTALL
```

Then use the kernel's original build command with the FinaSU arguments appended:

```sh
make ... \
  KSU_REQUIRE_PREINIT=1 \
  KSU_EXPECTED_SIZE=0x<certificate length> \
  KSU_EXPECTED_HASH=<certificate SHA-256>
```

`...` stands for the original architecture, device tree, compiler, parallelism and other parameters; replace it with the real build command for your machine.

:::: steps

1. **Confirm the current configuration**

   Check the final values of `CONFIG_KSU`, `CONFIG_KPROBES`, `CONFIG_EXT4_FS` and `CONFIG_HEADERS_INSTALL` in the build log instead of only looking at `.config`.

2. **Confirm the UAPI headers**

   Keep `KSU_REQUIRE_PREINIT=1`. When the target UAPI headers are missing the build fails immediately instead of silently producing a driver without preinit.

3. **Confirm the certificate slot**

   Look at the manager signature size/hash printed in the build log. They must match the signature of the Manager APK you install afterwards.

4. **Produce a flashable image**

   Use the device's original image packaging method. FinaSU does not require a specific flasher; `AnyKernel3`, `magiskboot` or vendor tools only wrap the kernel image.

5. **Verify on a recoverable device first**

   At minimum verify boot, Manager recognition, root grants, module mounts, SELinux state and the target ABI. A successful compile does not mean the device boots.

:::::

## Manager Signature

The kernel checks the Manager APK signature at runtime, not just the package name. The build variables `KSU_EXPECTED_SIZE` and `KSU_EXPECTED_HASH` correspond to the length and SHA-256 of the signing certificate DER.

```sh
make ... \
  KSU_EXPECTED_SIZE=0x02cc \
  KSU_EXPECTED_HASH=<sha256>
```

Second trust slot:

```sh
make ... \
  KSU_EXPECTED_SIZE=0x<primary certificate length> \
  KSU_EXPECTED_HASH=<primary certificate SHA-256> \
  KSU_EXPECTED_SIZE2=0x<second certificate length> \
  KSU_EXPECTED_HASH2=<second certificate SHA-256> \
  KSU_SECOND_SIGNATURE_RELEASE=1
```

If `KSU_EXPECTED_SIZE2` is set without `KSU_EXPECTED_HASH2`, the build fails immediately. When you actually use the second slot, add `KSU_SECOND_SIGNATURE_RELEASE=1` explicitly; otherwise the build marks the second slot as a test/PR trust slot.

::: danger A Signature Mismatch Is Not Fixed by Swapping the Manager

The kernel can boot normally and still not recognize the current Manager as a trusted identity. Rebuild a matching Manager and driver, or re-sign the APK; do not work around it by changing the package name, faking the version number or bypassing the identity check.

:::

## Preinit

Preinit lets some module configuration and `initrc` enter the boot flow at an earlier stage. It depends on the UAPI headers installed into the target tree at build time:

```text
$(objtree)/usr/include
```

`CONFIG_HEADERS_INSTALL=y` generates these headers; `KSU_REQUIRE_PREINIT=1` turns missing headers from a warning into a build failure.

Without preinit, ordinary modules can still mount and run, but `initrc/*.rc`, `preinit/` and some early synchronization will not work as expected. Do not fake the preinit state by touching files.

## UAPI Synchronization Boundaries

The UAPI used by a Built-In kernel must be provided to all of these at the same time:

- `kernel/include/uapi/` in the kernel.
- The repository-root `uapi/` mirrored headers used by the Manager JNI.
- `userspace/ksud/src/ksu_uapi.rs` and `ksucalls.rs`.
- The Manager JNI and the Kotlin call layer.

The current `KERNEL_SU_UAPI_VERSION` is `6`. Changing only the kernel structs without updating userspace can desynchronize request lengths, field offsets or command numbers. See [SuperCall & UAPI](/en/developer/supercall/) for the detailed constraints.

## Version String

The kernel build attempts to generate a full version string from the Git information of the FinaSU checkout and expose it to the Manager over the UAPI. If the build tree has no Git metadata for `kernel/`, the version degrades to an unknown value.

The version string only identifies the driver source; it does not indicate Built-In/LKM, preinit, late-load or SUSFS state. Those are reported separately through the capability flags of `GET_INFO`.

## SUSFS Integration

The FinaSU repository does not contain a kernel-side SUSFS implementation. To enable kernel-integrated SUSFS you must apply a SUSFS patch that strictly matches the version to the target kernel, and enable this in the FinaSU build:

```text
CONFIG_KSU=y
CONFIG_KSU_SUSFS=y
CONFIG_THREAD_INFO_IN_TASK=y
```

This repository provides the SUSFS integration points, the SELinux helper symbols and the configuration layer; `fs/susfs.c`, `include/linux/susfs.h` and the path hooks for each kernel version come from an external SUSFS project. A patch for one kernel version cannot be used directly on another version, and the driver-side interface in FinaSU is not a complete SUSFS implementation.

There is also an external `susfs-lkm` approach: it implements the SUSFS side as a loadable kernel module, which the kernel module loader can manage or which can be delivered as a Magisk/FinaSU module. It may differ from source-integrated versions in visible features, hook coverage and compatibility; the capabilities still depend on the specific `susfs-lkm` version and the kernel. Do not describe it as fully equivalent to the kernel patch version.

::: warning SUSFS Is Not an Ordinary Built-In Capability

A Built-In driver does not mean SUSFS is enabled. Only after a matching kernel implementation exists, followed by `CONFIG_KSU_SUSFS` and the related configuration, do the Settings in the Manager have a real object to act on.

:::

## Common Faults

::::: collapse accordion

:::: collapse UAPI headers missing during the build
Confirm `CONFIG_HEADERS_INSTALL=y`, clean and prepare the target kernel headers again; keep `KSU_REQUIRE_PREINIT=1` instead of deleting it to get a release build through.
::::

:::: collapse The kernel boots but the Manager shows it as unavailable
Check the Manager APK signature against the DER size/hash in the build log, then check whether the device actually booted the image you just built. The same version number does not prove the image was updated.
::::

:::: collapse Module files do not take effect
Check driver identity, the current mount backend, whether the module is enabled, `skip_mount`, the App Profile and safe mode separately. Built-In only changes how the driver is compiled; it does not fix mount configuration on its own.
::::

:::: collapse modpost or symbol failures after enabling SUSFS
Confirm you are using the SUSFS patch for the matching kernel version and `CONFIG_KSU=y`, and check whether the patch expects symbols this repository already provides. Do not stack an entire upstream driver patch set on top.
::::

:::: collapse Can I flash an image from another KMI to try it
No. KMI, kernel configuration, partition layout, AVB and vendor patches may all differ. A successful build for one KMI does not prove any other KMI can boot.
::::

:::::

## Related Pages

- [Built-In installation](/en/guide/install/built-in/): partitions, flashing and recovery flow for installers.
- [SuperCall & UAPI](/en/developer/supercall/): driver FD, ioctl permissions and ABI synchronization.
- [Build FinaSU](/en/developer/build/): LKM, payloads, APK and the final release flow.
