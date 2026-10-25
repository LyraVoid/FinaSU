---
title: 动画与交互
description: GridUI 工作卡点击、长按、计数导航与动画边界
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-grid/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 动画与交互

<Badge text="点击进安装" type="tip" /> <Badge text="长按调卡面" type="info" /> <Badge text="计数占位不跳动" type="warning" />

GridUI 保持两列空间稳定：计数不可用时只换成占位文本，不移除卡片，也不会把系统信息卡挤进侧栏。

## 工作卡

- 点击：进入安装/更新流程；延迟加载模式禁用；
- 长按：打开工作卡 surface 选项；
- 图片加载前：使用语义色，避免透明卡片上出现不可读文字；
- 图片加载后：按 surface 的不透明度与暗度绘制。

## 计数卡

计数可用时点击进入：

- 超级用户页面；
- 模块页面。

计数不可用时显示占位且不执行导航。这里的片点来自当前管理器数据，不是可点击的“安装模块”按钮。

## 系统信息与支持卡片

SUSFS 行可打开管理页；支持卡片根据模式打开 FinaSU 网站、Patreon、KernelSU 文档或自定义 HTTP(S) 链接。

页面切换、返回和按压反馈由 Material 3 Expressive 与系统决定。主题没有独立动画脚本或时长字段。

## 相关页面

- [工作卡片](/theme/theme-grid/working-card/)
- [导航](/setting/navigation/)
- [交互与兼容](/setting/interaction/)
