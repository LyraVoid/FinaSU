---
title: 状态信息
description: DashboardUI Hero 与系统信息卡实际显示的状态、版本和能力
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-dashboard/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 状态信息

<Badge text="状态快照" type="tip" /> <Badge text="不做实时监控" type="info" /> <Badge text="能力行按条件出现" type="warning" />

Dashboard 的“硬件”页面描述系统事实，而不是持续监控。它不绘制 CPU 温度、RAM 波形或存储图。

## Hero 事实

:::: table Hero 显示

| 字段 | 内容 |
| --- | --- |
| 模块版本 | 驱动版本与内核 UAPI |
| 内核 | 设备报告的内核版本 |
| SELinux | Enforcing、Permissive 或未知状态 |

::::

驱动未连接时，模块版本使用占位；Hero 标题变成未安装或不支持，并根据内核与 SELinux 条件提供安装或越狱操作。

## 附加状态

驱动已连接时，状态文字会组合显示：

- 运行模式：LKM / Built-In；
- 安全模式；
- 越狱模式；
- 自定义 LKM。

这些状态不是主题装饰。它们来自当前启动快照，主题切换不会改变。

## 系统信息卡

基础行：

- 管理器版本；
- 内核版本；
- 完整内核版本（存在时）；
- 设备型号；
- SELinux；
- Seccomp。

可选行：

- 指纹；
- Zygisk 实际提供者；
- 模块挂载提供者；
- SUSFS。

## 没有持续指标

Dashboard 不订阅 Focus/Stats 的 metrics flow，因此不会每 5 秒刷新 CPU/内存。需要实时读数时切换到 Focus 或 Stats。

## 相关页面

- [StatsUI 硬件监控](/theme/theme-statsui/hardware/)
- [Focus 硬件信息](/theme/theme-focus/hardware/)
- [驱动运行异常](/troubleshoot/runtime/)
