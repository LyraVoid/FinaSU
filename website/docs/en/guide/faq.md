---
title: FAQ
description: Common questions about FinaSU installation, root, modules, updates and troubleshooting
createTime: 2026/10/24 00:00:00
permalink: /en/guide/faq/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# FAQ

<Badge text="Check here first" type="tip" /> <Badge text="Do not guess partitions" type="danger" />

This page collects common questions by stage of use. For anything that involves a specific operation, read the matching install or switching article first; do not flash a device based on a single sentence from a Q&A.

::: tip Already rooted?

If the device currently gets root from Magisk, KernelSU or one of its forks, or APatch/KernelPatch, read the matching switching article first:

- [Switching from Magisk](/en/guide/switch/magisk/)
- [Switching from KernelSU](/en/guide/switch/kernelsu/)
- [Switching from APatch](/en/guide/switch/apatch/)

:::

## Quick Navigation

::: table Find by question

| Stage | Common questions | Read first |
| --- | --- | --- |
| Learning about FinaSU | What it is, how it relates to KernelSU/Magisk | [Basic Questions](#basic-questions) |
| Before installing | Device support, bootloader, which option to pick | [Install and Boot](#install-and-boot) |
| After installing | Will not boot, root does not work, authorization prompt | [Root and Permissions](#root-and-permissions) |
| Modules | Module does not work, boot loop, Zygisk/Sui/SUSFS | [Modules and Engines](#modules-and-engines) |
| Maintenance | OTA, updating the driver, uninstalling | [Update and Uninstall](#update-and-uninstall) |
| Feedback | Logs, versions, reproduction steps | [Reporting Issues](#reporting-issues) |

:::

## Basic Questions

### What is FinaSU?

FinaSU is a downstream fork of the KernelSU family in which the core root decision happens in the kernel. It provides a kernel-level `su` with an allowlist, Folk Mount, App Profile, FolkZygisk, Sui, SUSFS capability interfaces, NoMount, `.fpt` themes and a module repository.

::: important It is not just a Manager

The Manager APK only handles the interface, authorization and payload publishing; the kernel driver is what actually provides root. Installing the APK alone does not grant root.

:::

### How do FinaSU, KernelSU and Magisk relate?

Like the KernelSU family, FinaSU has the kernel make the final authorization decision; Magisk provides root by patching the boot image and running a daemon in userspace. The Managers, module mounting and boot chain modifications of the three must not be mixed at will.

::: warning Do not keep two root solutions at once

When FinaSU, Magisk, KernelSU or its forks, and APatch/KernelPatch take over the boot chain at the same time, you can get authorization errors, module conflicts or a failure to boot. Remove the old solution before switching.

:::

### Which devices does FinaSU support?

The official prebuilt LKM currently covers these KMIs:

::: table KMI range of the official LKM

| Android baseline | Kernel version |
| --- | --- |
| Android 12 | 5.10 |
| Android 13 | 5.10, 5.15 |
| Android 14 | 5.15, 6.1 |
| Android 15 | 6.6 |
| Android 16 | 6.12 |
| Android 17 | 6.18 |

:::

The architecture and boot method must match as well:

- The official prebuilt driver supports `arm64-v8a` and `x86_64`.
- riscv64 currently has no LKM driver asset, and Sui has no riscv64 payload either.
- The device's actual KMI must match the supported list exactly; do not go by the Android version or kernel version alone.
- Kernels outside the prebuilt list usually need source integration or porting; do not force-install a `.ko` with a similar name.

### Do I need to unlock the bootloader?

**LKM and Built-In require an unlocked bootloader**, because they modify `boot` or `init_boot`.

Jailbreak mode does not modify the boot partition, so it can be used on devices with a locked bootloader, provided the device has a working CVE or Magica privilege escalation entry. Do not flash the boot partition on a locked device to "make it permanent", or you may break AVB and end up unable to boot.

## Install and Boot

### Should I choose LKM, Built-In or Jailbreak?

::: table The three installation options

| Option | Modifies the boot partition | Root survives a reboot | Who it suits |
| --- | --- | --- | --- |
| [LKM](/en/guide/install/lkm/) | Yes, usually `init_boot` or `boot` | Yes | Most users |
| [Built-In](/en/guide/install/built-in/) | Yes, usually `boot` | Yes | Users who build their own kernel |
| [Jailbreak mode](/en/guide/install/jailbreak/) | No | No | Devices that cannot or should not modify the boot partition |

:::

When unsure, start from the category menu in [Installation Options](/en/guide/install/). Do not install two options at once.

### Which partition does installation modify?

- **LKM**: modifies the ramdisk. If the device has `init_boot` and the KMI is not `android12-*`, that is usually `init_boot`; otherwise it is `boot`.
- **Built-In**: replaces the Linux kernel, usually in `boot`; `init_boot` is not a target for a built-in driver.
- **Jailbreak**: writes no boot partition.

The partition the Manager shows is authoritative. `boot` and `init_boot` cannot be flashed interchangeably, and A/B devices also need the current slot confirmed.

### What if the device will not boot after installation?

Stop repeated reboots immediately and restore the partition that installation actually modified, as described in [Bootloop Rescue](/en/guide/rescue-bootloop/). Check first:

- Whether the partition is correct (`boot` / `init_boot` / `vendor_boot`).
- Whether the KMI matches.
- Whether the A/B slot was written correctly.
- Whether two root solutions exist at the same time.
- Whether the image came from the current device and system version.

::: danger Stop guessing

Flashing different boot images one after another widens the damage. If you have no matching stock image, find the complete firmware package first; do not use "universal rescue images" from unknown sources.

:::

### Can I keep an existing root and install FinaSU alongside it?

Not recommended. LKM, Built-In and an older root may modify the boot chain or the kernel entry at the same time. A device that already has root should first restore the old driver as described in the switching article, then install using the target option.

The only transitional exception: if the old LKM still provides a working root shell and the target partition matches the old LKM, you can replace it with FinaSU's "direct install"; reboot and remove the old Manager as soon as it finishes. See [Switching from KernelSU](/en/guide/switch/kernelsu/) for the exact conditions.

## Root and Permissions

### Root is granted but the app still says there is no root

First determine whether the problem is in the driver or in the app's own detection:

1. Run `su -c id` in a terminal app and check whether it returns `uid=0`.
2. Check that app's App Profile, especially the module unmount policy and the SELinux domain.
3. Force-stop and reopen the app so that it runs its detection again.
4. Check whether the UAPI of the Manager and the kernel match.

If `su -c id` works but the app still reports an error, the problem is usually the app's own detection or its Profile configuration.

### Why is there no root authorization prompt?

- The app may already be on the allowlist and allowed by default.
- The app may already have been denied, with the record on the "Superuser" page.
- The default policy may be set to allow or deny.
- The app may have cached the result of its previous detection.

Open the Superuser page in the Manager and look at the records first, then adjust that app's authorization. Do not try your luck by repeatedly clearing data.

### Why does authentication fail after reinstalling the Manager?

The kernel identifies the Manager by its APK signature. Debug builds, self-built builds or builds signed with another key are not trusted automatically. Prefer the official build; if you really need another signature, register it through the [Dynamic Manager](/en/manager/dynamic-manager/) flow and understand that this widens the kernel's trust scope.

::: danger Do not casually enable "allow any app as a dynamic manager"

That experimental option significantly widens the range of Managers that can be trusted. Use it only with an APK you built and verified yourself.

:::

### How do I hide root?

There is no single switch that works against every detection. Combine, per app:

- The module unmount policy in App Profile.
- Path redirection through NoMount.
- The SELinux hiding and AVC log handling provided by the kernel.
- Rules configured only for the apps that need them, instead of turning protection off globally.

First confirm what the target app actually detects, then adjust one item at a time; do not enable every hiding feature at once.

## Modules and Engines

### The module is installed but does not take effect

Check:

- Whether the module is enabled.
- Whether Folk Mount is set to "mount disabled".
- Whether the module needs a reboot before it works.
- Whether the Zygisk, Sui or configuration app the module depends on is ready.
- Whether the module supports FinaSU's mount method.

### The device will not boot after installing a module

When the device starts booting and the screen has just lit up, quickly press and release the volume down key 3 times to try to enter FinaSU safe mode.

**If that still fails**, restore the stock boot partition and give the boot-stage logs to the module author. If the failure comes from a wrong LKM or KMI, the volume key safe mode may never get a chance to run.

### What is a Metamodule and do I need one?

A Metamodule takes over the mount or installation flow for regular modules, and only one can be enabled at a time. The built-in Folk Mount in FinaSU already handles regular modules; install a Metamodule only when you need a specific mount implementation.

### What is the difference between Folk Mount and NoMount?

Folk Mount is the engine that selects a mount provider and injects modules; NoMount injects files through kernel VFS path redirection without creating a traditional mount. NoMount ships with `ksud` and is matched by KMI.

### What is FolkZygisk and do I need another Zygisk provider?

FolkZygisk is the built-in Zygisk engine. Enable it and reboot to run Zygisk modules; when an external provider is detected, the built-in engine steps aside. FinaSU keeps only one active Zygisk implementation.

### Does FinaSU support Xposed?

Yes. Install a modern Xposed-derived framework such as LSPosed on an available Zygisk provider. Only one of the built-in FolkZygisk and an external provider can be kept.

### What is Sui and do I still need Shizuku?

Sui is the built-in Shizuku API backend, so compatible apps do not need a separate Shizuku installation. App permissions, default policy, hiding rules and diagnostics are managed by the FinaSU Manager; Sui depends on the Zygisk backend.

### Why can't I find the SUSFS settings?

SUSFS is a kernel-side capability. FinaSU can work with a source-integrated build or an already loaded SUSFS-LKM; when the current kernel does not report support, the related switches are unavailable. FinaSU itself does not provide a SUSFS implementation and does not load the LKM. The source-integrated hook type is fixed at build time as Tracepoint or the ARM64 Built-In-only SUSFS Inline backend and cannot be switched from the Manager.

## Update and Uninstall

### Root disappeared after a system update

An OTA replaces the boot image. A/B devices should use "Install to inactive slot" while the OTA has not rebooted yet; non-A/B devices need to restore the stock image to finish the OTA, then patch the boot image of the current system again. Modules and configuration in `/data` are usually still there.

### The driver is still the old version after updating the Manager

An APK update does not automatically replace an LKM or Built-In driver that has already been flashed. Patch or flash again according to the matching option in [Updating FinaSU](/en/guide/update/); jailbreak mode needs a full reboot and another jailbreak.

### How do I uninstall FinaSU?

In LKM mode, "uninstall" on the Settings home screen offers a permanent uninstall and a restore of the stock image. The first starts the uninstall flashing flow, the second flashes back the matching stock boot partition; they are not the same operation.

::: steps

1. Back up the boot image and the modules you want to keep.
2. Choose "uninstall" on the Settings home screen.
3. Choose permanent uninstall, or choose to restore the stock image and prepare the matching image.
4. After the reboot, confirm that root is gone before cleaning up directories you no longer need.

:::

Do not delete the whole `/data/adb`; it also holds modules, authorization and FinaSU state.

## Reporting Issues

### What should I include when reporting an issue?

::: table Information for a report

| Information | Example |
| --- | --- |
| Device and system | Model, Android version, system build |
| Kernel | Kernel version, full KMI |
| FinaSU | Manager version, kernel build version, run mode |
| Installation | LKM / Built-In / Jailbreak, the partition actually modified |
| Modules | Names and versions of the relevant modules |
| Reproduction | Steps, expected result, actual result |
| Logs | The log exported by the Manager and the boot-stage log |

:::

A single screenshot is usually not enough to locate a problem. If the issue involves a boot failure, state whether Fastboot, Recovery or safe mode can still be reached.

## Quick Q&A

::: chat title="FinaSU quick Q&A"
{User}
Can I get root by installing only the Manager APK?

{.}
No. You also need the matching kernel driver, boot image or jailbreak payload.

{User}
LKM or Built-In, which one suits me?

{.}
Most users pick LKM; pick Built-In when you need a built-in driver, full source integration of SUSFS or to maintain the kernel yourself. If you only need SUSFS, you can also evaluate the less featureful SUSFS-LKM.

{User}
Does a jailbreak survive a reboot?

{.}
No. A jailbreak is valid only for the current boot session and must be run again after a reboot.

{User}
What do I do if the device will not boot after installing a module?

{.}
Press the volume down key three times in a row early in boot to try safe mode; if that does not help, restore the stock boot partition and do not keep flashing unknown images.

{User}
Can I switch directly from Magisk/KernelSU/APatch?

{.}
Read the matching switching article first. LKM can sometimes use the old root to install directly, while a built-in driver requires cleaning up the old kernel patch first.

{User}
Why did the root version not change after updating the APK?

{.}
The APK only updates the userspace; the driver must be updated separately according to LKM, Built-In or jailbreak mode.
:::

## Related Pages

- [Installation Options](/en/guide/install/)
- [LKM Installation](/en/guide/install/lkm/)
- [Built-In Installation](/en/guide/install/built-in/)
- [Jailbreak Mode Installation](/en/guide/install/jailbreak/)
- [Updating FinaSU](/en/guide/update/)
- [Bootloop Rescue](/en/guide/rescue-bootloop/)
- [Switching from Magisk](/en/guide/switch/magisk/)
- [Switching from KernelSU](/en/guide/switch/kernelsu/)
- [Switching from APatch](/en/guide/switch/apatch/)

*[LKM]: Loadable Kernel Module, a kernel module that can be loaded at runtime
*[KMI]: Kernel Module Interface, the Android kernel module interface version
*[GKI]: Generic Kernel Image, the Android generic kernel image
*[OTA]: Over-The-Air, online system update
*[UAPI]: the stable interface between the kernel and userspace
*[AVB]: Android Verified Boot
*[SUSFS]: hiding and path spoofing capabilities implemented on the kernel side
