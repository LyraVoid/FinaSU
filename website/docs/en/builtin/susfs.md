---
title: SUSFS
description: Overview of SUSFS kernel source integration and the SUSFS-LKM alternative
createTime: 2026/10/25 00:00:00
permalink: /en/builtin/susfs/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# SUSFS

<Badge text="Requires Kernel Support" type="warning" /> <Badge text="Advanced" type="danger" />

==SUSFS is a set of capabilities that hide traces such as paths, mounts and file metadata at the kernel level==. FinaSU does not include a SUSFS implementation; it only detects, configures and calls an existing SUSFS interface.

::: important SUSFS-LKM and FinaSU's LKM Installation Are Not the Same Thing

FinaSU itself can be installed through the LKM method, which does not mean the device automatically has SUSFS. SUSFS still needs a separate source-integrated build or SUSFS-LKM.

:::

## Kernel Source Integration

The complete solution requires modifying the kernel source of the target device:

1. Obtain kernel source matching the device's Linux version.
2. Apply the SUSFS patch for the corresponding version.
3. Enable `CONFIG_KSU_SUSFS` and the related sub-options.
4. Recompile the kernel and flash the complete `boot` image.

The current integration usually also requires FinaSU to be compiled into the kernel with `CONFIG_KSU=y`, because the patch references driver symbols. See [Kernel Integration](/en/developer/builtin-integration/#susfs-integration) for the specific configuration.

Source integration can provide a fairly complete set of hooks and capabilities, but building, upgrading and re-adapting after a system OTA are all more cumbersome. It suits users who can maintain kernel source and explicitly need the full SUSFS capabilities.

## Hook Type

A source-integrated build can use either compile-time syscall backend:

| Hook type | Configuration | Boundary |
| --- | --- | --- |
| Tracepoint | The default `CONFIG_KSU_HOOK_TRACEPOINT` | Works with LKM or Built-In; keeps the generic tracepoint, dispatcher and kprobes |
| SUSFS Inline | `CONFIG_KSU_HOOK_SUSFS_INLINE=y` | ARM64 Built-In only; the matching SUSFS patch calls FinaSU directly |

`CONFIG_KSU_SUSFS=y` enables the SUSFS feature layer; it does not select Inline. **Tracepoint + SUSFS** and **SUSFS Inline** are separate builds. The latter also requires `CONFIG_KSU=y` and cannot be loaded as an LKM.

Inline does not register the generic syscall tracepoint, dispatcher or kprobes replaced by direct entries. The build checks the kernel source that is actually being compiled for the required `ksu_handle_*` calls. Missing entries, old hook names or Manual Hook conflicts fail the build; there is no fallback to Tracepoint.

## The SUSFS-LKM Alternative

SUSFS-LKM packages SUSFS as a loadable kernel module, without recompiling the whole kernel. It is usually used in two ways:

- Load a `.ko` matching the current kernel directly with the kernel module loader.
- Package it as a Magisk module, select the matching KMI in a boot script, and let the module loader load it.

Both ways ultimately load a kernel module into the current system rather than modifying kernel source. You must choose a module matching the real kernel version, KMI and vendor ABI; rewriting vermagic automatically or a successful load does not mean the ABI is necessarily compatible.

::: warning Do Not Stack Two SUSFS Implementations

A source-integrated version and SUSFS-LKM, or two SUSFS-LKMs from different sources, should not be loaded at the same time. Disable the old implementation and reboot when switching implementations; deleting the `.ko` file does not unload a module that is already running in memory.

:::

## Gaps Compared with the Source-Integrated Version

SUSFS-LKM is an alternative, not an equivalent replacement for the source-integrated version. The actual capabilities depend on the interfaces the module exports, and the following gaps usually exist:

| Aspect | Kernel source integration | SUSFS-LKM |
| --- | --- | --- |
| Capability scope | Decided by the kernel patch and build options, with usually more complete coverage | Limited by the module implementation, exported symbols and proc nodes; may offer only some capabilities |
| Boot timing | Can attach early in kernel boot | Usually attaches later at the module loading stage after the system has booted |
| Dynamic kstat | Can sample fully around mounts | May be deferred to the next reboot when loaded too late |
| File redirection UID scheme | Can use the full range supported by the source version | Some UID schemes are not equivalent to the source version, and FinaSU refuses unsafe usage |
| Update method | Recompile and flash the kernel | Replace the module and reboot, but KMI compatibility must be re-confirmed |
| Compatibility risk | A patch mismatched with the kernel version causes build or boot problems | Vendor kernel ABI, symbols and module loading restrictions may directly cause load failure |

Therefore SUSFS-LKM suits users who cannot compile a kernel but can accept fewer features and compatibility risk. When full capabilities or long-term stable maintenance are needed, source integration should still be preferred.

## What FinaSU Shows

After opening **Settings → Features → SUSFS**, FinaSU detects whether the current kernel provides a compatible SUSFS control interface and only exposes the capabilities the other side actually reports.

```mermaid
flowchart LR
    A["Kernel source integration or SUSFS-LKM"] --> B["SUSFS control interface"]
    B --> C["FinaSU capability detection"]
    C --> D["Save and apply rules"]
```

- No interface detected: there is currently no usable SUSFS implementation.
- Unknown protocol: read-only display, no unverified control is executed.
- Compatible protocol: configuration is exposed according to what the kernel reports.
- Saving the configuration does not mean it is applied; changing rules that are already in effect usually requires a reboot.

The "Hook type" row in the home information card shows the ABI state reported by `GET_INFO`: `Tracepoint` or `SUSFS Inline`. A kernel that predates these capability bits shows "Legacy / unknown"; a selected backend whose entry point is not ready is shown as abnormal. Detecting SUSFS-LKM does not relabel the hook type as Inline.

FinaSU will not patch the kernel, install a `.ko` or load an LKM for you. Loading the LKM and starting it at boot are the responsibility of the module or loader you choose.

## Usage Boundaries

- Do not load kernel modules from unknown sources. An LKM runs in kernel mode, and a wrong module can make the device unable to boot.
- Do not treat "the module loaded successfully" as "all SUSFS features are available"; rely on FinaSU's capability detection and the actual effect.
- SUSFS does not guarantee passing all integrity detection; system properties, file contents and behavioral differences still need to be considered.
- Prepare a recoverable boot image or a way to disable modules before making changes.
