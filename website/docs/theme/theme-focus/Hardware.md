---
title: 硬件信息显示
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-focus/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 硬件信息显示

FocusUI 从设备当前可读的指标生成卡片：

- **CPU 温度**：以摄氏度显示，未知时使用占位值。
- **内存**：RAM 使用量与总量。
- **电池**：当前电量百分比。
- **CPU 频率**：按集群显示可用频率和负载。
- **内部存储**：Data 分区使用量与总量。
- **zram 与 swap**：仅在设备存在对应容量时显示。

无法读取的传感器显示占位符，不会沿用上一次缓存伪装为实时值。布局只在 Focus 与 StatsUI 中持续读取设备指标。
