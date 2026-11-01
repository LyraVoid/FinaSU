---
title: Component Hiding
description: What can be hidden on the Grid working card and system info card, and what does not change
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-grid/component-hiding/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Component Hiding

<Badge text="Three items on the working card" type="tip" /> <Badge text="Five items in system info" type="info" /> <Badge text="Hiding does not change state" type="warning" />

GridUI hiding controls fall into two groups: the working card's own three flags, and the optional rows of the system info card.

## Working Card

:::: table Three Independent Switches

| Switch | What is hidden | Behavior kept |
| --- | --- | --- |
| Hide status icon | The running/warning/blocked icon | Tapping to install, mode tags and the title |
| Hide title | The Running/Not installed/Not supported text | The icon, the mode and the buttons |
| Hide mode tags | `LKM` / `Built-In` | Driver state and the install entry |

::::

All three flags are stored in `surface.layout.grid.workCard.flag.*` and have historic compatibility keys. They are not the same setting as "Hide status tags" for the Circle/Stats list.

## System Info

Rows that can be hidden:

- Fingerprint;
- Hook type while a driver is connected;
- Zygisk implementation (when present);
- SUSFS (when detected);
- Module mount implementation (when status is available).

Basic rows always remain: Manager version, kernel, device model, SELinux, Seccomp. Hook type appears only while a driver is connected, and hiding it does not change the backend.

## Bottom Support Card

Grid shares the bottom card modes with the other layouts. Selecting "Hidden" only skips the card; it does not delete custom text and links.

## Risks

:::: warning Do Not Use Hiding Instead of a Fix

If SUSFS is not detected, Zygisk has no provider or mounting fails, hiding the corresponding row only makes the problem invisible. When troubleshooting, show it again first and then check the current boot state.

::::

## Related Pages

- [Working Card](/en/theme/theme-grid/working-card/)
- [Home & Layout](/en/setting/home/)
- [Driver Runtime Errors](/en/troubleshoot/runtime/)
