---
title: NoMount
description: FinaSU VFS path injection backend, driver state, visibility and hot-loading rules
createTime: 2026/10/25 00:00:00
permalink: /en/builtin/nomount/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# NoMount

<Badge text="VFS Backend" type="info" /> <Badge text="Minimum API 23" type="warning" /> <Badge text="Inverted Default Visibility" type="danger" />

==NoMount injects module files by redirecting kernel-side VFS lookup and directory traversal==, without creating traditional mounts. A process's mount table can therefore stay as it is, while the filesystem view is still rewritten by the kernel according to rules.

::: important No Visible Mounts Does Not Mean Completely Untraceable

NoMount hides one class of traditional mount traces. File contents, open file descriptors, SELinux, audit logs, system properties or other behavioral differences may still be detected. It does not guarantee passing every integrity check.

:::

## Enabling Through Folk Mount

NoMount is not a separately toggled switch. Open **Settings → Features → Folk Mount**, change the mode to **Built-in (VFS)**, then reboot.

:::: steps

1. **Configure exception apps first**

   NoMount's default visibility is the opposite of the built-in Magic Mount. Before switching, choose on the NoMount page which apps should see the stock filesystem.

2. **Select Built-in (VFS)**

   The Manager warns about the visibility change. After confirming, it writes the next-boot configuration.

3. **Reboot the device**

   Driver loading and rule injection happen during boot. The Folk Mount provider of the current boot is not hot-switched.

4. **Check the driver and rules**

   Confirm the driver is **Ready** and check the "effective / expected" rule counts. A rule count of 0 does not mean the driver is broken; it may simply mean no module contributed injectable files.

5. **Verify with an exception app**

   Ordinary apps should see the injected files, and exception apps should see the stock view. Apps sharing a UID are affected together.

::::

## Driver

NoMount can come from built-in kernel support or from a `.ko` matching the current KMI. Both sources use the same rule protocol once the kernel API is ready.

::: table Driver State

| State | Meaning | Common Action |
| --- | --- | --- |
| **Ready** | The driver API responds and the version meets the minimum requirement | Continue checking the rules and the provider |
| **Absent** | No built-in support and no loaded module | Reboot, run one injection, or check whether the installation package ships the current KMI asset |
| **Outdated** | The driver exists but the API is below 22 | Upgrade the kernel-side implementation; an old built-in driver cannot be replaced directly from userspace |

:::

The page also distinguishes:

- **Kernel built-in**: the driver ships with the kernel image and needs no extra loading.
- **Kernel module**: the driver is loaded as a `.ko` for the current KMI.
- **Taken over by another module**: another installed module drives the same NoMount kernel interface, and the built-in backend stands down.

::: warning A Successful Load Does Not Mean the Driver Is Usable

The kernel module loader handles vermagic and symbol relocation, but a "load call returned" can still yield a driver that does not respond. NoMount therefore probes the API again after loading, and only a responsive driver with a valid version counts as ready.

:::

## Driver Failure and Boot Protection

NoMount prepares the driver first, then publishes rules. The following cases make this boot fall back to the built-in Magic Mount:

- The current installation package has no module matching the KMI, and the kernel does not provide the driver.
- Driver loading fails, or the API version is still unacceptable after loading.
- The previous load or injection stage did not complete, so the boot flow conservatively does not repeat the same path.
- Rule publishing fails.

The fallback writes a warning stating that the built-in engine actually completed mounting this time. ==The configuration mode is still Built-in (VFS)==; do not keep switching modes just because you saw a fallback.

::: danger Selecting NoMount Again Is Also a Retry

If the previous driver load left an unfinished protection record, selecting NoMount again clears that record and allows the next boot to retry. When NoMount has already stalled several times, keep the logs and prepare a bootloop rescue method first instead of retrying indefinitely.

:::

## Injection Rules

NoMount generates redirection rules from the `system/` trees of enabled modules, including ordinary file redirection, directory replacement and whiteouts. There are usually three counts:

- **Expected rules**: how many rules the current modules plan to publish.
- **Effective rules**: non-virtual directory rules currently held by the kernel and contributed by modules.
- **Total kernel rules**: also includes virtual directories the driver creates automatically to reach deep paths.

:::: field-group

::: field Ordinary redirection
@type `File or directory`
A virtual path is served by another real path, but processes still see the specified virtual path.
:::

::: field Directory replacement
@type `Opaque`
The directory uses the module tree directly and no longer merges the stock directory contents with module contents.
:::

::: field Whiteout
@type `Hidden`
Makes a path appear not to exist instead of redirecting it to another file.
:::

::: field Skipped module
@type `Diagnostics`
When a module directory is unreadable, a path is invalid, a symlink escapes or rules cannot be generated safely, the module may be skipped and the reason recorded.
:::

::::

## Hot Injection and Withdrawal

NoMount supports operating on a single module within the current boot:

- Tap a module to perform **hot injection**, adding that module's current rules to the kernel.
- Perform **withdraw** on a single module to remove its rules until they are regenerated on the next reboot.
- **Clear rules** removes all redirections but keeps exception apps; they are restored after a reboot.
- **Clear all** clears both the rules and the exception apps; the boot plan is restored after a reboot.

Hot operations suit locating a single module. They do not reinstall a module and do not change whether a module is enabled.

## Visibility Model

This is the part most likely to catch you out when switching to NoMount.

::: table Default Views of the Two Backends

| Backend | Default Behavior | Role of the Exception List |
| --- | --- | --- |
| **Built-in Magic Mount** | Hides from non-root apps according to the App Profile "unmount modules" policy | Decided by App Profile |
| **NoMount** | All apps can see injected files by default | Exception UIDs see the stock filesystem |

:::

NoMount exception apps are not "apps allowed to use Root", nor "apps allowed to use the network". They only mean those UIDs do not get module redirection applied.

### Isolated Processes

After enabling **Hide from isolated processes**, isolated processes such as WebView and SDK sandboxes also see the stock filesystem. This option and ordinary exception UIDs are two separate controls; do not treat them as the same kind of list.

### Follow App Profile

"Follow App Profile" performs a one-time copy only: it adds the apps within the list's scope that have "unmount modules" enabled in their Profile to the NoMount exceptions. Later App Profile changes are not synchronized automatically.

::: warning The First Copy May Select Many Apps

The default non-root Profile enables "unmount modules" for most apps. Tapping "Follow App Profile" on the NoMount page may select a large number of apps at once. Check the selected count before saving.

:::

### Shared UIDs

Android apps can share a UID. NoMount matches by UID rather than only by package name, so setting an exception for one app also makes other apps with the same UID see the stock view.

## State and Diagnostics

:::: collapse accordion

### The driver is ready but the rule count is 0

This may be normal. Check whether any module contains an injectable `system/` path, whether modules are disabled or marked `skip_mount`, and whether the rules were removed by a "clear rules" operation.

### It shows "taken over by another module"

One of the installed modules is driving the same NoMount kernel interface. The built-in backend exits on its own to avoid two programs writing rules at the same time. Check that module's management entry; do not force both to inject at once.

### Exception apps still see module files

Confirm the change was saved to the kernel and not only edited in the list; confirm the app UID has not changed because of a reinstall or a work profile change; other apps sharing the UID may re-expose the same view. Fully restart the target app if necessary.

### After switching back to the built-in Magic Mount the modules reappear

This means the NoMount exception list did take effect, while the built-in engine displays according to the App Profile policy. Recheck the Profile's "unmount modules" setting before switching back.

### With no mount records, why can detection still find differences

NoMount only changes the VFS view and does not hide file contents, process mappings, system properties or SELinux auditing. Integrity detection does not only check `/proc/mounts`.

::::

## Commands and Data Locations

```file-tree title="NoMount locations"
/data/adb/ksu
├── mount_mode                  # the VFS backend is only used after selecting nomount
├── nomount_runtime             # latest driver and rule injection result
├── nomount_uids                # exception UIDs and isolated process option
├── nomount_last_action         # latest hot operation
├── mount_log                   # rules and skip reasons
└── workdir/                    # rule preparation and boot working directory
```

::: code-tabs

@tab Manager

```text
Settings → Features → Folk Mount → NoMount
Check the driver, rules, module contributions, exception apps and recent operations
```

@tab Status

```sh
/data/adb/ksud nomount status --json
```

@tab Dry Run

```sh
/data/adb/ksud nomount inject --dry-run --verbose
```

:::

A dry run only generates and reports rules and does not touch the kernel. It suits confirming why a module was skipped.

Do not edit `nomount_uids` directly or call the driver protocol by hand. Rules and the UID list must stay synchronized with the current kernel state; the Manager and `ksud` handle storage, validation and error reporting.

## Recommended Usage

1. **Decide visibility first, then switch the backend.** Do not wait until after a reboot to discover that every app sees module files.
2. **Verify at the smallest scope with the exception list first.** Do not migrate the whole app policy to NoMount at once.
3. **Do a dry run first when rules look wrong.** Check module contributions and skip reasons, then decide whether to hot-inject or roll back.
4. **Do not promise "complete invisibility" with NoMount.** It solves one class of mount traces, not full-stack anti-detection.
