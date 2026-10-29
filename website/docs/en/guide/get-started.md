---
title: Quick Start
createTime: 2026/10/24 00:00:00
permalink: /en/guide/get-started/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Quick Start

This page covers only two persistent driver methods: **LKM** and **kernel source integration**. Jailbreak / late load, which only lasts for the current boot, is out of scope here. Most users should choose LKM first: it does not require you to compile the Linux kernel, and the Manager places a `kernelsu.ko` matching the device KMI into the boot image. Move to source integration only when LKM does not support your device, or when you explicitly need a kernel-side change such as a built-in driver or full source-level SUSFS integration.

::: tip Beginners Should Start With LKM

- **Normal install and upgrade**: use the official LKM, and the Manager selects the matching driver automatically.
- **Do not compile the kernel first**: source integration needs the full device kernel source, a build environment and flashing experience, and is an advanced route.

:::

| Method | When to use |
| --- | --- |
| **LKM (recommended)** | The official build supports the current KMI; no kernel compilation; later driver updates are easier |
| Kernel source integration (advanced) | LKM does not support the device; a built-in driver is required; source-level SUSFS integration is required |

::: warning The LKM Here Is Not a Third-party Kernel Module

"LKM" refers specifically to the FinaSU root driver `kernelsu.ko`. Entries on the Manager's "Kernel Modules" page are managed by `ksud kmod` and have nothing to do with gaining root.

:::

## LKM Support Scope

Think of the KMI as the interface version between the kernel and the driver. It is not the Android system version, and the kernel version number alone is not enough. The prebuilt LKM in official releases currently covers these KMIs:

| Android baseline | Kernel version |
| --- | --- |
| Android 12 | 5.10 |
| Android 13 | 5.10, 5.15 |
| Android 14 | 5.15, 6.1 |
| Android 15 | 6.6 |
| Android 16 | 6.12 |
| Android 17 | 6.18 |

The corresponding directory names are `android12-5.10`, `android13-5.10`, `android13-5.15`, `android14-5.15`, `android14-6.1`, `android15-6.6`, `android16-6.12` and `android17-6.18`.

The following conditions must also hold:

- **Architecture**: official prebuilt drivers cover `arm64-v8a` and `x86_64`. There is no LKM driver asset for riscv64, so installing the Manager alone will not give you root.
- **KMI**: the device's actual KMI must match the directory name exactly. A nearby Android version or a similar kernel version does not replace a KMI match.
- **Boot method**: physical devices usually need an unlocked bootloader (the program that loads Android at power-on), and the ability to patch and flash the boot partition the Manager identifies; for emulators and similar environments, follow the matching image flow the Manager provides.
- **System state**: remove other kernel-level root solutions before installing, so several drivers do not take over the boot chain at once.

::: important Stop at the Manager When Unsure

If the Manager cannot identify the current KMI, or the KMI is not in the prebuilt list, do not download a similarly named `.ko` and force an install. The device is outside official LKM coverage, so confirm compatibility first; old kernels, non-GKI kernels and heavily vendor-modified kernels usually need source integration or a port.

:::

## Recommended: Use LKM

::: steps

1. **Prepare the Manager and a backup**

   Install the official FinaSU Manager, confirm the current KMI, and back up `boot`, `init_boot` or whichever partition the Manager names.

2. **Choose an installation method in the Manager**

   | Device state | Suggested method |
   | --- | --- |
   | First install with a usable boot image | Select a file and patch |
   | Complete OTA or factory image available | Download a file and patch |
   | The device already has working root | Direct install |
   | A/B device updated by OTA, not yet rebooted | Install to the inactive slot |

3. **Use the driver the Manager provides**

   Keep the default built-in LKM selection; you usually do not need to pick a `.ko` file by hand. Direct install uses the current device KMI; for a downloaded OTA or factory image, the Manager probes the KMI from the image. When you select a local boot image, first confirm that the image really belongs to the current device and system.

4. **Patch and flash the boot partition**

   After patching, write to the same partition the Manager names, using `fastboot` or the flashing method the device supports. Devices that split the ramdisk as of Android 13 and later usually patch `init_boot`, while other devices may patch `boot`; the partition the Manager identifies is authoritative.

5. **Reboot and verify**

   Open the Manager home screen and confirm the driver shows `LKM`; then request root once from a terminal app and confirm the authorization prompt appears.

:::

### What the Manager Does

The Manager patches the boot image: it puts `ksuinit` and the `kernelsu.ko` matching the device KMI into the ramdisk, and `ksuinit` loads the driver early in boot. It does not put a `.ko` into `/data/adb/modules/`, and this is not an ordinary third-party module from the Manager's "Kernel Modules" page.

The core patch is equivalent to the command below; ordinary users do not need to run it by hand:

```sh
ksud boot-patch -b boot.img -m android15-6.6_kernelsu.ko
```

When `-m` is omitted, `ksud` determines the KMI from available information such as the current device and the input image, and uses its own embedded matching driver. When the Manager actually runs it, it also passes parameters such as the output file and target partition; the command above shows only the core patch. Do not put the LKM into `/data/adb/modules/`, and do not try to replace boot image patching with a plain `insmod` boot script.

### Where the Driver Files Come From

A FinaSU release provides `FinaSU_<tag>_kernel-bundle.zip`, which contains the driver for each KMI on arm64 and x86_64 plus `ksud`:

::: file-tree title="kernel-bundle.zip"

- FinaSU_\<tag\>_kernel-bundle.zip
  - arm64/
    - android15-6.6_kernelsu.ko
    - ...
  - x86_64/
    - android15-6.6_kernelsu.ko
    - ...
  - ksud/
    - android-arm64
    - android-x86_64
    - ...

:::

For installation on a real device, prefer the complete release assets built into the Manager. If you must select a local `.ko` by hand, use a driver FinaSU built for the same KMI; a matching file name does not prove the file is compatible. Do not load a `.ko` of unknown origin.

::: warning A Local LKM Is Not Interchangeable

One `android15-6.6_kernelsu.ko` only fits the matching KMI. An architecture or KMI mismatch can stop the driver from loading; if the Manager signature built into the driver does not match, the Manager will not treat it as manageable even when the driver does load.

:::

### LKM Updates

In LKM mode, when the built-in driver shipped with the Manager differs from the running version, the Manager can offer an install; the update takes effect after a reboot and does not require recompiling the kernel. Kernel source integration requires rebuilding and flashing the whole kernel image.

## Advanced: Integrate from Kernel Source

::: warning Beginners Can Stop Here

The flow below is for users who can build and flash a device kernel. It needs kernel source that exactly matches the device system, and it is not covered by the CI device boot tests. As long as the official LKM supports your KMI, you do not need this route.

:::

Typical cases for source integration:

- The official LKM does not provide the current KMI.
- The driver must be fixed inside the kernel image.
- Full source-level SUSFS integration is required, and the current integration requires `CONFIG_KSU=y`.

Use the kernel source, branch, commit and configuration that correspond to the device's current system. Kernel source for another version can carry different vendor modifications even with the same version number, so it cannot be reused directly.

:::: steps

1. **Add the FinaSU driver**

   Run this at the root of the target kernel source:

   ```sh
   /path/to/FinaSU/kernel/setup.sh --dir /path/to/FinaSU
   ```

   `setup.sh` creates the `drivers/kernelsu` link and adds the driver entry to `drivers/Makefile` and `drivers/Kconfig`. GKI source uses `common/drivers` automatically. Keep the FinaSU repository's `.git`, so that the full version number displays correctly.

2. **Enable kernel options**

   ```sh
   scripts/config --enable KSU
   scripts/config --enable KPROBES
   scripts/config --enable KALLSYMS_ALL
   scripts/config --enable EXT4_FS
   scripts/config --enable HEADERS_INSTALL
   ```

   | Configuration | Purpose |
   | --- | --- |
   | `CONFIG_KSU=y` | Build the driver into the kernel image |
   | `CONFIG_KPROBES=y` | Install the kprobe/tracepoint hooks the driver uses |
   | `CONFIG_KALLSYMS_ALL=y` | Resolve data symbols such as `sys_call_table` at runtime |
   | `CONFIG_EXT4_FS=y` | Build dependency of the driver |
   | `CONFIG_HEADERS_INSTALL=y` | UAPI headers needed to build the preinit runner |

3. **Build the kernel**

   Add `KSU_REQUIRE_PREINIT=1` explicitly to the target kernel's existing build arguments. The `...` below is only a placeholder and must be replaced with the device kernel's original `make` arguments. Otherwise, when the target UAPI headers are missing, the build only disables preinit and does not fail:

   ```sh
   make ... KSU_REQUIRE_PREINIT=1
   ```

4. **Package and flash**

   Follow the target device's existing kernel build, image packaging and flashing flow. After the image is produced, confirm the boot partition, slot and AVB handling before flashing.

::::

### Manager Signature

The kernel verifies the DER data of the signing certificate inside the Manager APK, not the hash of the APK file itself. The signature information of the official FinaSU Manager certificate (`CN=FolkSU`) is public and sits in the second signature slot:

| Parameter | Official FinaSU Manager |
| --- | --- |
| Certificate length | `0x02cc` (716 bytes) |
| Certificate SHA-256 | `2d89b50aaf1db013c2b6738f973f48b62ea21a3a8d7216bfec3521065d448458` |

Pass the official signature and the second trusted signature slot to the kernel build:

```sh
make ... \
  KSU_REQUIRE_PREINIT=1 \
  KSU_EXPECTED_SIZE2=0x02cc \
  KSU_EXPECTED_HASH2=2d89b50aaf1db013c2b6738f973f48b62ea21a3a8d7216bfec3521065d448458 \
  KSU_SECOND_SIGNATURE_RELEASE=1
```

When the signature does not match, the kernel usually still boots, but the Manager does not recognize the driver, so you cannot manage authorization and configuration through the Manager.

::: details Using a Self-signed Manager

A re-signed APK cannot use the official hash above; export the certificate from **the APK you will actually install**. Prepare `apksigner` from Android SDK Build-Tools:

```sh
manager_apk=/path/to/FinaSU_vX.Y.Z.apk

apksigner verify --print-certs-pem "$manager_apk" |
  openssl x509 -outform DER > manager-cert.der

cert_size=$(printf '0x%04x' "$(wc -c < manager-cert.der)")
cert_hash=$(sha256sum manager-cert.der | awk '{print $1}')

printf 'size=%s\nhash=%s\n' "$cert_size" "$cert_hash"
```

Replace `KSU_EXPECTED_SIZE2` and `KSU_EXPECTED_HASH2` with the output values and keep `KSU_SECOND_SIGNATURE_RELEASE=1`. You can also cross-check the certificate SHA-256 with `apksigner verify --print-certs "$manager_apk"`. `apksigner` is usually at `build-tools/<version>/apksigner` in the Android SDK.

:::

### SUSFS

Apply the SUSFS patch and enable `CONFIG_KSU_SUSFS` only when it matches the target kernel version. The current integration also requires `CONFIG_KSU=y` and `CONFIG_THREAD_INFO_IN_TASK=y`. The FinaSU repository provides the integration point but does not include the SUSFS kernel implementation (`fs/susfs.c` and the hooks for each kernel version).

Full kernel-side documentation is in [Kernel Integration](/en/developer/builtin-integration/#susfs-integration) and [SUSFS](/en/builtin/susfs/).

### Building the LKM Yourself

This is a maintainer flow and is not needed for a normal install. It requires Docker and the DDK image for the target KMI; FinaSU builds only `android15-6.6` by default:

```sh
KMIS=android15-6.6 ./build.sh lkm
```

The output is `kernel/android15-6.6_kernelsu.ko`, staged as well into `userspace/ksud/bin/aarch64/` for `ksud` to package. One local build covers only the KMIs listed in `KMIS`; when you need several devices or the full release matrix, use the kernel bundle produced by CI.

## Verification

Open the Manager home screen, confirm the driver shows `Built-In` or `LKM`, and open the "Superuser" page. The authorization prompt should appear when a terminal app requests root for the first time. Changes to modules, Zygisk and Sui usually take effect only after a reboot.

Source integration is outside the CI device tests. At minimum, verify boot, root authorization, module mounting and SELinux status on the target device; a successful build alone does not mean the result is ready to release.

::: danger

A wrong KMI, unmatched system source or inconsistent Manager signature can all leave the driver unusable; flashing an unverified kernel image can also prevent the device from booting. Back up first and have a working recovery path ready.

:::

::: warning
Do not try unfamiliar modules or kernel files without a backup of the boot image. If the device will not boot, see [Bootloop Rescue](/en/guide/rescue-bootloop/).
:::
