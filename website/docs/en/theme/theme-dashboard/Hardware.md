---
title: Status Info
description: The states, versions and capabilities actually shown by the DashboardUI Hero and system info card
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-dashboard/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Status Info

<Badge text="Status snapshot" type="tip" /> <Badge text="No live monitoring" type="info" /> <Badge text="Capability rows appear conditionally" type="warning" />

The Dashboard "hardware" page describes system facts rather than continuous monitoring. It does not draw CPU temperature, RAM waveforms or storage charts.

## Hero Facts

:::: table What the Hero Shows

| Field | Content |
| --- | --- |
| Module version | Driver version and kernel UAPI |
| Kernel | The kernel version reported by the device |
| SELinux | Enforcing, Permissive or unknown state |

::::

When the driver is not connected, the module version uses a placeholder; the Hero title becomes not installed or not supported, and install or jailbreak actions are offered according to the kernel and SELinux conditions.

## Additional States

When the driver is connected, the status text is shown as a combination:

- Run mode: LKM / Built-In;
- Safe mode;
- Jailbreak mode;
- Custom LKM.

These states are not theme decoration. They come from the current boot snapshot and do not change when the theme changes.

## System Info Card

Basic rows:

- Manager version;
- Kernel version;
- Full kernel version (when present);
- Hook type while a driver is connected (`Tracepoint` / `SUSFS Inline`);
- Device model;
- SELinux;
- Seccomp.

Optional rows:

- Fingerprint;
- The actual Zygisk provider;
- The module mount provider;
- SUSFS.

## No Continuous Metrics

Dashboard does not subscribe to the Focus/Stats metrics flow, so it does not refresh CPU/memory every 5 seconds. Switch to Focus or Stats when you need live readings.

## Related Pages

- [StatsUI Hardware Monitoring](/en/theme/theme-statsui/hardware/)
- [Focus Hardware Info](/en/theme/theme-focus/hardware/)
- [Driver Runtime Errors](/en/troubleshoot/runtime/)
