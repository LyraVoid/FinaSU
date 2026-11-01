---
title: Basic Styling
description: GridUI two-column ratio, status, count placeholders and system info
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-grid/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Basic Styling

<Badge text="Equal left and right weights" type="tip" /> <Badge text="Counts keep their placeholder" type="info" /> <Badge text="Does not become a single column with width" type="warning" />

The top of GridUI consists of one working card and two count cards. The working card is on the left and the counts on the right; each side takes half of the available width, with fixed spacing.

## Working Card

The working card shows:

- The status icon;
- Running, not installed or not supported;
- The `LKM` / `Built-In` tags when available;
- The jailbreak button when not installed and SELinux is Permissive.

The status icon sits in the top right, while the title and tags stay near the bottom, making the card feel like a visual entry rather than an information list.

## Count Cards

:::: table The Two Cards on the Right

| Card | When available | When unavailable |
| --- | --- | --- |
| Superuser | Shows the number of grants, opens the Superuser page | Shows an unavailable placeholder, does not navigate |
| Modules | Shows the number of enabled modules, opens the Modules page | Shows an unavailable placeholder, does not navigate |

::::

Counts are available when a full-featured environment and available root both hold. Installing the Manager alone does not make the two cards show numbers permanently.

## System Info

After the two-column grid, Grid shows a unified system info card:

- Manager version;
- Kernel version and full version;
- Hook type (`Tracepoint` / `SUSFS Inline`; older kernels show "Legacy / unknown");
- Device model;
- Fingerprint;
- SELinux;
- Seccomp;
- The actual Zygisk provider;
- The actual module mount provider;
- Detected SUSFS.

Whether a row appears is also affected by capability detection and the [Component Hiding](/en/theme/theme-grid/component-hiding/) settings.

## Visual Material

Grid has extra surfaces:

- The page background comes from the global or per-page wallpaper;
- The working card can use `grid_working_card_background.*` on its own;
- The count cards and the system info card have no separate image surface.

So do not apply Dashboard Hero or Focus card keys to Grid by mistake.

## Related Pages

- [Working Card](/en/theme/theme-grid/working-card/)
- [Background](/en/theme/theme-grid/background/)
- [Home & Layout](/en/setting/home/)
