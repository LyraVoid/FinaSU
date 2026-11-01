---
title: Basic Styling
description: The CircleUI top bar, status, counts, system info and visual hierarchy
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-circle/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Basic Styling

<Badge text="Single-column layout" type="tip" /> <Badge text="Status first" type="info" /> <Badge text="System facts are never faked" type="warning" />

CircleUI uses uniform full-width tonal cards with vertical spacing. The page itself scrolls, while the top bar keeps app-level actions; card colors come from the current Material color scheme, and once a wallpaper is enabled the surface material takes over transparency and dim.

## Top Bar

The top bar title can be:

- The app name;
- `KernelSU`;
- Custom text;
- An advanced title image.

Actions on the right side of the top bar appear according to the current capabilities:

- A management entry when SUSFS is detected;
- Play/pause when background music is enabled and the file exists;
- The install/update button;
- The reboot menu.

In lazy-loading mode the install entry is disabled, so a session that cannot safely flash the boot image does not lead the user into that action.

## Status Card

The status title has only three basic outcomes:

:::: table Status Determination

| Title | Condition | Common action |
| --- | --- | --- |
| Running | Driver version connected | Tap to enter the install/update flow |
| Not installed | No driver, but the kernel is GKI | Tap to install |
| Not supported | No driver, and the kernel does not meet the install assumptions | Read the compatibility notes; no faked status is offered |

::::

While running, the driver version and kernel UAPI combination are also shown, possibly with:

- The `LKM` or `Built-In` run mode;
- A safe mode tag;
- A jailbreak mode tag;
- A custom LKM tag.

When not installed and SELinux is Permissive, a jailbreak button may appear. The jailbreak entry is an action for the current environment and is not the same as permanent root.

## Count Cards

In a full-featured environment, the superuser and module counts are shown as two horizontal cards. Unavailable or non-full-featured environments do not draw the count area.

Tapping a count only navigates; it does not modify authorization or install modules.

## System Info Card

The basic facts are always shown:

- Manager version;
- Kernel version;
- Full kernel version (when the device provides it);
- Device model;
- SELinux;
- Seccomp.

Optional facts appear according to capability:

- Fingerprint;
- Hook type while a driver is connected (`Tracepoint` / `SUSFS Inline`; older kernels show "Legacy / unknown");
- The actual Zygisk provider;
- The actual Folk Mount / NoMount provider;
- The detected SUSFS version, implementation and management method.

These rows come from the actual state snapshot; a capability is not faked just because a theme field is missing.

## Colors and Fonts

Circle has no separate color page. It uses only:

- The color scheme from "Settings → Appearance → Theme Settings";
- The font from "Settings → Appearance → Font";
- The background and surface settings from "Settings → Appearance → Wallpaper".

## Related Pages

- [Working Card](/en/theme/theme-circle/working-card/)
- [Background](/en/theme/theme-circle/background/)
- [Home & Layout](/en/setting/home/)
