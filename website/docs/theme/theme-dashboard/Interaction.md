---
title: 动画与交互
description: DashboardUI Hero 操作、长按表面、计数导航与系统动画
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-dashboard/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 动画与交互

<Badge text="点击进安装" type="tip" /> <Badge text="长按调 Hero 图" type="info" /> <Badge text="没有独立动画开关" type="warning" />

Dashboard 的 Hero 是首页主要交互区域，计数卡承担导航，系统信息中的 SUSFS 行提供能力入口。

## Hero

- 点击：进入安装/更新流程；延迟加载模式禁用；
- 长按：打开 Hero surface 选项；
- 越狱按钮：仅在未安装且 SELinux Permissive 时出现；
- 图片加载：先保持不透明表面，解码成功后切换。

## 计数

超级用户与模块卡各打开对应主页面。不可用时只占位，不执行导航。

## 系统信息

SUSFS 行检测到时可打开管理页。其他行只显示事实，点击背景不会授予 Root 或改变模块。

## 动画边界

Dashboard 使用 Material 3 Expressive 的按压、形状与页面过渡，以及系统预测性返回。FinaSU 没有 Dashboard 专属动画引擎、时长或缓动字段。

## 相关页面

- [Dashboard 基础样式](/theme/theme-dashboard/basic/)
- [导航](/setting/navigation/)
- [交互与兼容](/setting/interaction/)
