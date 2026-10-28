---
title: Custom Unmount Mountpoints
description: Hide exact mountpoints from selected apps and distinguish real unmounting from /proc filtering
createTime: 2026/10/29 00:00:00
permalink: /en/setting/umount-paths/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Custom Unmount Mountpoints

<Badge text="Applied per UID" type="warning" /> <Badge text="New app processes only" type="tip" />

This feature makes a **selected app** stop seeing one **exact mountpoint** the next time that app starts. It changes the app's own mount namespace and the real files the app can read.

::: warning This is not "Hide mount records"

[Hide mount records](/en/setting/hiding/) only filters the text that apps see when reading `/proc`. This feature removes the mountpoint from the target app's real mount view. Test the two features separately.

:::

## When to use it

Use it when a module mounts content at a fixed path, but only one target app should see the stock content instead.

Examples:

- A module injects files into the system certificate directory, while one detection app should read the stock directory.
- An app needs to avoid one specific mountpoint.
- You need to check whether an app's behavior is caused by a particular module mount.

This is not a replacement for module management. It only handles the exact mountpoints you enter; it does not process regular files, child directories, wildcards or regular expressions.

## Requirements

All four conditions must be met:

::: table Readiness checks

| Check | Where to verify it |
| --- | --- |
| “Module unmounting (master switch)” is on | [Settings → Features](/en/setting/function/) |
| The target UID's App Profile allows module unmounting | [App Profile](/en/manager/app-profile/); when no override exists, “Umount modules by default” decides |
| The path is a mountpoint root in the target namespace | Enter the mountpoint itself, not a file or child directory |
| The target app starts after the rule takes effect | An already-running process does not change immediately |

:::

::: important A Root UID does not necessarily run the non-Root unmount policy

The kernel first decides whether a UID should unmount modules. A UID that already has Root access, or whose App Profile does not allow module unmounting, will not run the unmount just because a path exists on this page.

:::

## Add a rule

:::: steps

1. Open **Manager → Settings → Features → Custom Unmount Mountpoints**.

2. Tap **Add** in the top-right corner, or use the empty-state row.

3. Enter the complete mountpoint path under “Complete mountpoint path”, for example:

   ```text
   /system/etc/security/cacerts
   ```

4. Tap **Save rule**. The Manager first saves the persistent configuration, then tries to add the path to the running kernel list.

5. Force-stop the target app and open it again. A device reboot is not required, but an already-running process will not change immediately.

::::

After the operation, the page reloads both the ksud configuration and the kernel list. It does not fake a successful result with local-only state.

## Entering the path

A valid path must:

- start with `/`;
- name the mountpoint itself;
- be no longer than 255 bytes;
- not end with `/`;
- not contain empty segments, `.`, `..` or control characters;
- not be a protected root-level mountpoint such as `/`, `/data`, `/system` or `/apex`.

::: table Correct and incorrect examples

| Input | Result |
| --- | --- |
| `/system/etc/security/cacerts` | Correct, exact mountpoint |
| `system/etc/security/cacerts` | Invalid, not absolute |
| `/system/etc/security/cacerts/` | Invalid, trailing slash |
| `/system/etc/security/cacerts/example.0` | Not a mountpoint root; the parent is not unmounted recursively |
| `/system/*` or a regular expression | Not supported |

:::

Removal is fixed to **Safe delayed removal (MNT_DETACH)**. There is no flags field because this is not a configurable parameter panel; the fixed method can remove the mountpoint safely while another process is using it.

## My rules versus module-added entries

The page separates entries by source:

::: table Entry sources

| Group | Source | Editable here |
| --- | --- | --- |
| **My rules** | Configuration saved on this page | Yes |
| **Added automatically by modules (read-only)** | Entries written by modules or other external components | No |
| **Unknown source (read-only)** | Entries whose source cannot be determined reliably | No |

:::

The Manager does not optimistically delete entries just to make the list look tidy. If the source is unknown, the entry remains read-only.

## Why a deleted rule may still apply

Deleting a rule removes only your persistent configuration. It does not force-remove an entry that a module or another source may have registered in the current boot.

- The existing kernel entry may still apply until the device restarts.
- After a reboot, the path stops if no module registers it again.
- Removing all custom rules does not remove entries added automatically by modules.

The confirmation dialog therefore states: ==it may still apply in this boot and stops after a reboot==.

## Verify the result

:::: steps

1. Record the target app's UID and confirm that its App Profile allows module unmounting.

2. Close the app. Moving it to the background is not a full restart.

3. Open the app again, then run the real business flow or use a detection app.

4. If the app still sees the mount, work through [Troubleshooting](#troubleshooting).

::::

Users comfortable with a terminal can inspect the target process's own mount view:

```bash
adb shell pidof <package>
adb shell su -c "grep ' /your/mountpoint ' /proc/<PID>/mountinfo"
```

No matching line means the mountpoint is absent from that process's namespace. Changing only `/proc` display would not affect this check.

## Troubleshooting

::: collapse accordion

- **Why did nothing change immediately?**

  The target app was already running. Force-stop it and open it again; the device usually does not need a reboot.

- **The master switch is on, but the app still sees the mount.**

  Check whether the target UID's App Profile allows module unmounting, and confirm that Root authorization is not causing the UID to skip the non-Root unmount policy.

- **The path exists, so why is there no effect?**

  The path must be exactly the mountpoint root in the target namespace. A regular file, child directory or parent directory is not a substitute.

- **The page says this kernel cannot list mountpoints.**

  The kernel does not provide the new read-only enumeration command. Saved rules may still be handled by the older add command, but the Manager cannot confirm kernel acceptance and must not report them as active.

- **The page says the custom rules cannot be read.**

  The configuration may be damaged or unreadable. The Manager does not rebuild or overwrite the file and disables editing. Back it up and inspect it instead of deleting it immediately.

- **Can it replace "Hide mount records"?**

  No. This feature changes real mounts and file content; "Hide mount records" changes only the `/proc` text view.

- **Can it unmount everything under a directory?**

  No. It accepts exact mountpoints only. Directory recursion, wildcards, regular expressions and automatic recommendations are not supported.

:::

## Safety

- Test on an unimportant app whose data you can clear.
- Do not unmount core system mountpoints or use root-level directories as test paths.
- Do not rely only on a detector reporting clean; confirm that the app's normal features still work.
- Record the package name, UID, original path and current app behavior before changing anything.

## Related pages

- [Feature Settings](/en/setting/function/)
- [Root & Mount Policy](/en/setting/root-policy/)
- [App Profile](/en/manager/app-profile/)
- [Hiding & Kernel Security](/en/setting/hiding/)
- [Detection Troubleshooting](/en/troubleshoot/detection/)
