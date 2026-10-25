---
title: 基础样式
description: StatsUI 顶部 list/grid 切换、宽屏分组和表面能力
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-statsui/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 基础样式

<Badge text="顶部两种形态" type="tip" /> <Badge text="宽屏两列" type="info" /> <Badge text="监控卡无独立图片" type="warning" />

StatsUI 的视觉结构由顶部形态决定，但监控、环形统计和系统信息始终存在。

## 顶部 list

选择“列表样式”时：

- 使用标准状态卡；
- 显示驱动状态、版本-UAPI、LKM/Built-In；
- 安全模式、越狱模式和自定义 LKM 可附加显示；
- “隐藏状态标签”只影响 LKM/Built-In 标签；
- 不使用 Grid 工作卡。

## 顶部 grid

选择“网格样式”时，Stats 直接复用 Grid 首页内容：

```text
左侧：Grid 工作卡
右侧：超级用户计数 + 模块计数
```

因此以下配置会出现在壁纸页：

- `layout.grid.workCard`；
- `grid_working_card_background.*`；
- Grid 工作卡的透明度、暗度和隐藏开关。

顶部 grid 不绘制额外标准状态卡，避免重复状态。

## 宽屏分组

窗口宽度达到 600dp 时：

```text
左列：顶部状态/Grid
      监控卡
右列：环形统计
      系统信息
```

窄屏按顶部、监控、环形统计、系统信息纵向排列。

## 背景

Stats 使用：

- 全局或分页页面壁纸；
- 顶部 grid 时的 Grid 工作卡图片。

监控卡、环形统计卡和系统信息卡没有独立图片 surface。全局壁纸透明度与暗度仍作用于页面材料。

## 相关页面

- [硬件监控](/theme/theme-statsui/hardware/)
- [Grid 工作卡片](/theme/theme-grid/working-card/)
- [首页与布局](/setting/home/)
