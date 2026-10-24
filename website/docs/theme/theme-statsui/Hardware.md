---
title: 硬件监控
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-statsui/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 硬件监控

StatsUI 的监控卡包含：

- **CPU 温度波形**：显示当前摄氏度与短期历史。
- **内存波形**：显示 RAM 使用比例与变化趋势。
- **CPU 集群频率**：按集群显示平均频率与使用比例。
- **电池**：当前电量，并用颜色区分低电量与中间区间。
- **zram / swap**：仅在设备提供对应容量时显示。

采样不可用时显示 `--`。只有 FocusUI 与 StatsUI 保持指标采集，其他首页布局不持续轮询。
