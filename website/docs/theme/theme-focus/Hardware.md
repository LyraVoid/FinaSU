---
title: 硬件信息显示
description: FocusUI 的设备与存储指标来源、采样周期、占位和历史限制
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-focus/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 硬件信息显示

<Badge text="5 秒采样" type="tip" /> <Badge text="仅当前首页" type="info" /> <Badge text="zram/swap 数据源受限" type="warning" />

Focus 与 Stats 是仅有的两套持续读取设备指标的首页布局。Circle、Grid 和 Dashboard 只使用状态快照，不保持指标轮询。

## 采样生命周期

- 首页为当前页面且布局为 Focus/Stats 时开始采集；
- 每 5 秒读取一次；
- 保留最近 30 个样本供波形使用；
- 离开首页或切换到不采样布局后停止；
- 最后一个消费者消失后约 5 秒停止共享流。

## 设备卡来源

:::: table 指标与实际来源

| 指标 | 来源 | 失败结果 |
| --- | --- | --- |
| 电池电量 | `ACTION_BATTERY_CHANGED` sticky broadcast | `—` |
| 电池温度 | 同一广播的 `EXTRA_TEMPERATURE` | `—` |
| CPU 温度 | `/sys/class/thermal` 中首个 `cpu*` 热区，回退 SoC/AOSS/TSENS | `—` |

::::

温度只用于显示，不触发温控。显示的 CPU 温度是设备暴露的某个热区，并不保证等于所有核心或 SoC 内部最高温度。

## 存储卡来源

:::: table 存储与内存

| 指标 | 来源 |
| --- | --- |
| Data 使用/总量 | `StatFs` 读取数据分区 |
| RAM 使用/总量 | `/proc/meminfo` 的 `MemTotal` 与 `MemAvailable` |
| zram / swap | 数据模型支持，但当前采集器不填充 |

::::

代码注释明确指出 Android 应用通常无法读取 `/proc/swaps`，所以当前 `SystemMetricsCollector` 不设置 `zramTotalBytes` 与 `swapTotalBytes`。即使界面有对应行，也不应承诺每台设备必然显示。

## 未知值

未知指标统一显示 `—`。这比显示 0 更安全：

- 0°C 看起来像有效读数；
- 0% 看起来像真实电量；
- 0 B 总量可能被误认为设备没有存储。

## Focus 不显示的指标

Focus 设备卡没有 CPU 频率集群、RAM 波形或电池阈值颜色条。CPU 集群与短期波形属于 StatsUI 的监控卡。

## 相关页面

- [StatsUI 硬件监控](/theme/theme-statsui/hardware/)
- [Focus 平板适配](/theme/theme-focus/tablet/)
- [基础样式](/theme/theme-focus/basic/)
