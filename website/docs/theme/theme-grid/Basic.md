---
title: 基础样式
description: GridUI 双列比例、状态、计数占位与系统信息
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-grid/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 基础样式

<Badge text="左右等权" type="tip" /> <Badge text="计数保留占位" type="info" /> <Badge text="不随宽度变单列" type="warning" />

GridUI 顶部由一张工作卡与两张计数卡组成。工作卡在左，计数在右；左右各占可用宽度的一半，间距固定。

## 工作卡

工作卡显示：

- 状态图标；
- 运行中、未安装或不支持；
- 可用时的 `LKM` / `Built-In` 标签；
- 未安装且 SELinux Permissive 时的越狱按钮。

状态图标位于右上，标题和标签靠近底部，使卡片更像视觉入口而不是信息列表。

## 计数卡

:::: table 右侧两张卡

| 卡片 | 可用时 | 不可用时 |
| --- | --- | --- |
| 超级用户 | 显示授权数量，可进入超级用户页 | 显示不可用占位，不触发导航 |
| 模块 | 显示已启用模块数量，可进入模块页 | 显示不可用占位，不触发导航 |

::::

计数可用条件是完整功能环境和 Root 可用同时成立。仅安装管理器并不会让两张卡永久显示数字。

## 系统信息

Grid 在双列网格之后显示统一系统信息卡：

- 管理器版本；
- 内核版本与完整版本；
- 设备型号；
- 指纹；
- SELinux；
- Seccomp；
- Zygisk 实际提供者；
- 模块挂载实际提供者；
- 检测到的 SUSFS。

行是否出现还受能力检测和[组件隐藏](/theme/theme-grid/component-hiding/)设置影响。

## 视觉材料

Grid 有额外的 surface：

- 页面背景来自全局/分页壁纸；
- 工作卡可单独使用 `grid_working_card_background.*`；
- 计数卡和系统信息卡没有独立图片 surface。

因此不要把 Dashboard Hero 或 Focus 卡键误用到 Grid。

## 相关页面

- [工作卡片](/theme/theme-grid/working-card/)
- [背景设置](/theme/theme-grid/background/)
- [首页与布局](/setting/home/)
