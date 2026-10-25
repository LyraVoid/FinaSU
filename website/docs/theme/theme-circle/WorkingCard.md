---
title: 工作卡片
description: CircleUI 状态卡的运行状态、模式标签、点击行为和背景限制
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-circle/working-card/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 工作卡片

<Badge text="状态卡" type="tip" /> <Badge text="点击安装/更新" type="info" /> <Badge text="无长按卡面编辑" type="warning" />

CircleUI 的“工作卡片”是首页顶部的整宽状态卡。它报告驱动是否可用、版本、UAPI 与当前运行模式，并为安装、更新或越狱提供入口。

## 状态与标签

:::: table 实际显示

| 条件 | 标题 | 辅助信息 |
| --- | --- | --- |
| 驱动已连接 | 运行中 | `版本-UAPI` |
| 未连接且内核为 GKI | 未安装 | 点击安装提示 |
| 未连接且内核不满足 | 不支持 | 不支持原因 |

::::

驱动已连接时，状态卡可显示：

- `LKM` 运行模式标签；
- `Built-In` 运行模式标签；
- 安全模式标签；
- 越狱模式标签；
- 自定义 LKM 标签。

“隐藏状态标签”设置只影响 Circle 和 Stats 列表顶部的运行模式标签，不隐藏安全模式、越狱模式或自定义 LKM 信息。

未安装、内核可安装且 SELinux 为 Permissive 时，右侧可能出现越狱按钮。它是当前会话的临时路线，不能替代启动镜像安装。

## 点击行为

- 驱动可用：点击进入安装/更新流程；
- 未安装：点击进入安装；
- 延迟加载模式：点击被禁用，避免从不适合刷写的会话进入安装；
- 越狱按钮：走独立延迟加载流程；
- 长按：没有 Circle 专属 surface 编辑面板。

## 背景限制

Circle 状态卡没有以下字段：

- 独立图片；
- 独立不透明度；
- 独立暗度；
- 隐藏状态图标；
- 隐藏标题。

这些控制在 Grid 工作卡页面中才对应实际 surface。Circle 的状态卡只使用全局壁纸与主题表面材质。

## 颜色

未启用壁纸时，状态卡使用语义容器色：正常状态与错误/未安装状态使用不同角色。启用壁纸后，改用共享壁纸表面 token，以保证半透明背景上的文字对比度。

## 相关页面

- [组件隐藏](/theme/theme-circle/component-hiding/)
- [背景设置](/theme/theme-circle/background/)
- [首页与布局](/setting/home/)
