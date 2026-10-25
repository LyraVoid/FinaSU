---
title: 动画与交互
description: FocusUI 四卡点击、长按背景、采样启停与响应式切换
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-focus/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 动画与交互

<Badge text="四卡可长按" type="tip" /> <Badge text="状态卡执行安装" type="info" /> <Badge text="没有独立动画引擎" type="warning" />

Focus 的交互重点是四张卡的独立图片入口与状态卡操作。卡片重排由窗口尺寸变化触发，不是用户手动切换的 Focus 内部模式。

## 点击

:::: table 卡片交互

| 区域 | 点击 | 长按 |
| --- | --- | --- |
| 状态卡 | 卡内操作按钮执行安装/更新或越狱；卡体本身无点击动作 | 打开状态卡图片选项 |
| 管理器卡 | SUSFS 行可进入管理页 | 打开管理器卡图片选项 |
| 设备卡 | 无 | 打开设备卡图片选项 |
| 存储卡 | 无 | 打开存储卡图片选项 |

::::

长按只编辑对应子卡图片；透明度与暗度由共享父级 `layout.focus` 控制。

## 响应式切换

窗口从竖屏变横屏且满足断点时，四卡会在单列与 2×2 之间重排。没有横向页面切换动画，也不会复制卡片内容。

回到不满足条件的尺寸后立即恢复单列。这个过程不会重置四张卡的图片或共享数值。

## 指标采样

设备卡与存储卡只在实际显示 Focus 首页时更新。切到其他主页面后停止持续采样；回来时读取新值。没有数据时显示占位，不保留旧值伪装为实时。

## 导航与动画

导航形态、预测性返回、页面过渡和系统动态效果由全局设置与 Android 版本决定。主题不能提供独立 Focus 动画脚本或速度字段。

## 相关页面

- [Focus 平板适配](/theme/theme-focus/tablet/)
- [导航](/setting/navigation/)
- [交互与兼容](/setting/interaction/)
