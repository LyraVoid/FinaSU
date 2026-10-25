---
title: 统计入口
description: DashboardUI 的超级用户与模块计数、导航和统计能力边界
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-dashboard/statistics/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 统计入口

<Badge text="两个计数" type="tip" /> <Badge text="点击导航" type="info" /> <Badge text="没有环形统计" type="warning" />

Dashboard 的“统计”只是两张可点击计数卡，不做图表。

:::: table 两个入口

| 卡片 | 数字来源 | 点击 |
| --- | --- | --- |
| 超级用户 | 当前已授权用户数量 | 超级用户页面 |
| 模块 | 当前已启用模块数量 | 模块页面 |

::::

完整功能环境才会绘制计数区；环境不完整时不显示这些入口。完整环境中的数字来自当前管理器状态，0 也表示实际统计结果。

## 不包含什么

Dashboard 没有：

- 插件启用数量环形图；
- CPU/RAM 波形；
- CPU 集群；
- 电池阈值图；
- 独立 KPM/APM 统计；
- 图表点击详情。

需要三类计数环形图时使用 [StatsUI 统计信息](/theme/theme-statsui/statistics/)。

## 计数解释

模块计数表示已启用模块，不是模块目录条目总数。超级用户计数来自当前授权状态，不在主题包中保存。

## 相关页面

- [StatsUI 统计信息](/theme/theme-statsui/statistics/)
- [超级用户](/manager/superuser/)
- [模块](/manager/module/)
