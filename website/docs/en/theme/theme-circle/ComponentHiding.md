---
title: Component Hiding
description: Display boundaries for CircleUI system info rows, the support card and navigation extras
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-circle/component-hiding/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Component Hiding

<Badge text="Display only" type="tip" /> <Badge text="Basic facts always remain" type="info" /> <Badge text="The support card can be fully hidden" type="warning" />

CircleUI hiding settings do not change detection results, and they do not uninstall modules or disable capabilities. They only reduce the visual information on the home screen.

## Status Tags

"Settings → Appearance → Layout → Hide status tags" affects the `LKM` / `Built-In` tags of the Circle status card. The same switch also appears when the Stats top layout is `list`, but it does not affect Grid cards.

Safe mode, jailbreak mode and custom LKM are more important state hints and are not controlled by this switch.

## System Info Rows

::::: table Hideable Rows and Actual Gating

| Row | Hideable | Extra condition |
| --- | --- | --- |
| Fingerprint | Yes | Device information is available |
| Hook type | Yes | A driver is connected |
| Zygisk implementation | Yes | A Zygisk provider currently exists |
| SUSFS | Yes | SUSFS is currently detected |
| Module mount implementation | Yes | Folk Mount status is available and reading did not fail |

:::::

The following basic facts are always shown:

- Manager version;
- Kernel version;
- Full kernel version (when present);
- Device model;
- SELinux;
- Seccomp.

Hiding Zygisk or SUSFS does not turn off FolkZygisk, an external provider or SUSFS kernel features; hiding the hook type does not change the kernel backend either. When troubleshooting detection problems, either show the corresponding row again or check the current boot state on the feature page.

## Bottom Support Card

The bottom card has four modes:

:::: table Support Card

| Mode | What is actually shown |
| --- | --- |
| Learn about FinaSU | One info card that opens the FinaSU website |
| Support development and KernelSU | A donation entry and a KernelSU documentation entry |
| Custom | A title, subtitle and HTTP(S) link filled in by the user |
| Hidden | Not drawn, and no empty placeholder is kept |

::::

In custom mode all three fields must be non-empty. If any of them is missing, the card is simply not drawn on the home screen; invalid links are not saved. Switching to "Hidden" does not delete custom content that has already been saved.

## Navigation and Icons

Navigation entries, badges and custom icons are not hiding items on the Circle page. They are controlled by the navigation settings and the theme `navIcons`; see [Theme Package Structure](/en/developer/theme-package/#navigation-icons).

## Related Pages

- [Home & Layout](/en/setting/home/)
- [Working Card](/en/theme/theme-circle/working-card/)
- [Theme Preview & Usage](/en/theme/theme-use/)
