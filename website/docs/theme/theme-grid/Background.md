---
title: 背景设置
description: GridUI 页面背景、分页壁纸与工作卡独立图片
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-grid/background/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 背景设置

<Badge text="双背景层" type="tip" /> <Badge text="页面壁纸 + 工作卡" type="info" /> <Badge text="计数卡无独立图" type="warning" />

GridUI 可以同时使用两层背景：

1. 页面背景：全局壁纸或首页分页壁纸；
2. 工作卡背景：左侧状态卡自己的图片。

两张计数卡和系统信息卡没有独立图片 surface。

## 页面背景

页面背景由“设置 → 外观 → 壁纸”控制：

- 单图模式使用 `background.*`；
- 多背景模式首页使用 `background_home.*`；
- 视频模式使用 `video_background.*`；
- 透明度、暗度、模糊与取色来自全局壁纸配置。

Grid 本身不会改变页面壁纸的裁切或覆盖范围。

## 工作卡背景

工作卡独立条目：

```text
grid_working_card_background.jpg
grid_working_card_background.png
grid_working_card_background.gif
grid_working_card_background.webp
grid_working_card_background.jpeg
```

按上述扩展名优先级选择。图片存在后，还要开启对应 surface；只有资源但没有启用时，卡片仍显示语义色。

::::: field-group

:::: field 启用
@type `surface.layout.grid.workCard.enabled`
关闭时保留图片文件，但卡片不绘制它。
::::

:::: field 不透明度
@type `0..1`
使用单一 `opacity`，或开启 `dual_opacity` 后分别使用日间/夜间值。
::::

:::: field 暗度
@type `0..1`
Grid 工作卡只有一个 `dim`，当前注册表没有日夜暗度分离。
::::

:::: field 裁切
@type `实际卡片边界`
图片按工作卡实际测量尺寸裁切填充，布局变化后以实际宽高为准。
::::

:::::

## Stats 网格顶部

当首页为 StatsUI 且 `statsTopLayout=grid` 时，顶部会复用 Grid 工作卡和计数卡。此时壁纸页也会显示 Grid 工作卡设置。

切回 Stats 的 `list` 顶部后，该 surface 不再由 Stats 使用，但 GridUI 仍可使用同一份配置。

## 操作入口

有两种方式进入工作卡设置：

- “设置 → 外观 → 壁纸”，选择当前布局下的工作卡；
- 在首页长按工作卡，直接打开 surface 选项。

## 相关页面

- [工作卡片](/theme/theme-grid/working-card/)
- [全局背景](/theme/global/transparency/)
- [壁纸](/setting/wallpaper/)
