---
title: Basic Styling
description: Titles, fields, actions and the shared background model of the four FocusUI cards
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-focus/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Basic Styling

<Badge text="Semantic sections" type="tip" /> <Badge text="Shared transparency" type="info" /> <Badge text="Child cards store only images" type="warning" />

Each FocusUI card has an icon, a title, a divider and content rows. Cards use a uniform tonal card shape; when a background image is present, the text and dividers switch to a contrast color suited to the image.

## Status Card

The title is "Status" and it shows:

- Running/Not installed/Not supported;
- Version and UAPI;
- The current run mode;
- Safe mode;
- Jailbreak mode;
- Custom LKM state.

The card header may show:

:::: table Status Actions

| Condition | Action |
| --- | --- |
| Manager is too old | Update Manager |
| Kernel or built-in payload needs an update | Update |
| Not installed and temporary jailbreak is possible | Jailbreak |
| Not installed and a normal install is possible | Install |

::::

## Manager Card

The title is "Manager" and the content is the current system facts:

- Manager version;
- Kernel version;
- Full kernel version;
- Hook type (hideable; `Tracepoint` / `SUSFS Inline`);
- Device model;
- Fingerprint (hideable);
- SELinux;
- Seccomp;
- Zygisk provider (present and not hidden);
- Module mount implementation (available and not hidden);
- SUSFS state (detected and not hidden).

This card is broader than a single "Manager version" item; it carries the identifiable facts of the whole system.

## Device Card

Three ring metrics:

1. Battery temperature;
2. CPU temperature;
3. Battery level.

The units are °C, °C and % respectively. When a value cannot be read, `—` is shown; unknown values are never drawn as 0.

## Storage Card

Shown in order:

- Internal Data used/total;
- RAM used/total;
- zram (when the total is greater than 0);
- swap (when the total is greater than 0).

The current Android public API collector does not actively fill zram/swap, so these two rows do not appear on many devices; this is a data source limitation, not theme hiding.

## Background Model

::::: field-group

:::: field Parent
@type `layout.focus`
The master switch, transparency, day/night transparency, dim, day/night dim.
::::

:::: field Status card image
@type `focus_card_kernel_bg`
Only controls whether that card draws its own image.
::::

:::: field Manager card image
@type `focus_card_app_bg`
Only stores the presence of the image; the numeric values still come from the parent.
::::

:::: field Device and storage card images
@type `focus_card_device_bg` / `focus_card_storage_bg`
The two cards select their images separately and share the parent transparency and dim.
::::

:::::

## Related Pages

- [Hardware Info](/en/theme/theme-focus/hardware/)
- [Global Background](/en/theme/global/transparency/)
- [Home & Layout](/en/setting/home/)
