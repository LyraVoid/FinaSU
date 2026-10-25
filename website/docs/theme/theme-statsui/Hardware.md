---
title: 硬件监控
description: StatsUI 的 5 秒采样、30 点波形、CPU 集群、电池与存储指标
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-statsui/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 硬件监控

<Badge text="5 秒采样" type="tip" /> <Badge text="30 点历史" type="info" /> <Badge text="zram/swap 常不可用" type="warning" />

Stats 的监控卡把设备事实与短期趋势放在同一张卡中。它不依赖 Root shell，只读取 Android 公共接口与可读 sysfs 节点。

## CPU 温度波形

- 当前值来自首选 CPU/SoC 热区；
- 单位 °C；
- 最近 30 个有效样本绘制波形；
- 无法读取时显示 `—`，不补 0。

## RAM 波形

- 当前使用率来自 `/proc/meminfo`；
- 显示百分比；
- 保留 30 个样本；
- 总量为 0 时显示不可用，不绘制伪造曲线。

## CPU 集群

采集每个核心的 `cpufreq`：

- 优先 `scaling_cur_freq`；
- 回退 `cpuinfo_cur_freq`；
- 上限优先 `cpuinfo_max_freq`，再回退 `scaling_max_freq`；
- 按最大频率分组；
- 每组显示核心范围、平均当前频率与平均/上限比例。

这个是“当前频率占上限的比例”，不是 CPU 负载百分比。代码没有读取 `/proc/stat`，因为普通 Android 应用通常无权读取。

## 电池

电量来自 sticky battery broadcast：

- 0–20%：错误色；
- 21–50%：第三色；
- 51% 以上：主色；
- 不可读：占位并使用主色。

这些颜色使用 Material 语义角色，会随主题变化。

## zram 与 swap

界面在 `zramTotalBytes > 0` 或 `swapTotalBytes > 0` 时显示对应进度条。但当前 `SystemMetricsCollector` 没有填充这两个字段，注释说明平台通常不向应用暴露 `/proc/swaps`。

因此：

- 数据模型支持这两行；
- 当前常规设备上大概率看不到；
- 看不到不等于主题资源缺失；
- 不应把这两行写成当前版本的稳定卖点。

## 采样边界

- 每 5 秒一次；
- 只在 Stats/Focus 首页处于当前页时运行；
- 最后一个订阅者停止约 5 秒后结束；
- 切走再回来不会连续显示离开期间的“历史”；
- 没有数据时使用占位符。

## 相关页面

- [Focus 硬件信息](/theme/theme-focus/hardware/)
- [系统指标排错](/troubleshoot/runtime/)
- [全局主题](/theme/global/theme-style/)
