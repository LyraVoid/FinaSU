---
title: 统计信息
description: StatsUI 模块、超级用户、插件环形图的数据来源和交互边界
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-statsui/statistics/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 统计信息

<Badge text="三类计数" type="tip" /> <Badge text="总数显示在中心" type="info" /> <Badge text="图表不可点击" type="warning" />

Stats 的环形图由三类当前状态组成。

:::: table 环形统计

| 类别 | 含义 | 颜色角色 |
| --- | --- | --- |
| 模块 | 当前已启用模块数量 | primary |
| 超级用户 | 当前授权数量 | tertiary |
| 插件 | 当前已启用插件数量 | secondary |

::::

环形中心显示三项数量之和。右侧图例始终列出三类名称与数量，即使某一类为 0。

## 数据来源

数量来自管理器当前列表状态，不来自主题包，也不从 Root 授权文件自行猜测。模块被停用、插件被关闭或用户被移除后，下一次状态更新会反映变化。

当前不使用 Ring 表示：

- KPM/APM；
- 内核模块数量；
- 存储占用；
- CPU 温度；
- 下载任务。

## 交互

环形图和图例是信息展示，没有点击跳转、筛选或隐藏切片功能。需要管理对应对象时，从导航进入超级用户、模块或插件页面。

## 视觉边界

三类颜色来自当前 Material 配色，不单独提供 Stats 图表主题字段。改变强调色、色彩风格、明暗模式或壁纸取色会影响图表颜色。

## 相关页面

- [超级用户](/manager/superuser/)
- [模块](/manager/module/)
- [插件](/manager/plugin/)
- [StatsUI 硬件监控](/theme/theme-statsui/hardware/)
