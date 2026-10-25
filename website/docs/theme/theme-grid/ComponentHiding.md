---
title: 组件隐藏
description: Grid 工作卡与系统信息卡可隐藏内容及不改变的行为
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-grid/component-hiding/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 组件隐藏

<Badge text="工作卡三项" type="tip" /> <Badge text="系统信息四项" type="info" /> <Badge text="隐藏不改变状态" type="warning" />

GridUI 的隐藏控制分成两组：工作卡自己的三个 flag，以及系统信息卡的可选行。

## 工作卡

:::: table 三个独立开关

| 开关 | 隐藏对象 | 保留的行为 |
| --- | --- | --- |
| 隐藏状态图标 | 运行/警告/阻止图标 | 点击安装、模式标签和标题 |
| 隐藏标题 | 运行中/未安装/不支持文字 | 图标、模式和按钮 |
| 隐藏模式标签 | `LKM` / `Built-In` | 驱动状态与安装入口 |

::::

三个 flag 都保存在 `surface.layout.grid.workCard.flag.*`，并有历史兼容键。它们与 Circle/Stats 列表的“隐藏状态标签”不是同一个设置。

## 系统信息

可隐藏的行：

- 指纹；
- Zygisk 实现（存在时）；
- SUSFS（检测到时）；
- 模块挂载实现（状态可用时）。

基础行始终保持：管理器版本、内核、设备型号、SELinux、Seccomp。

## 底部支持卡片

Grid 与其他布局共用底部卡片模式。选择“隐藏”只会跳过卡片，不删除自定义文字与链接。

## 风险

:::: warning 不要用隐藏代替修复

如果 SUSFS 未检测、Zygisk 没有提供者或挂载失败，隐藏对应行只会让问题不可见。需要排错时应先恢复显示，再查看本次启动状态。

::::

## 相关页面

- [工作卡片](/theme/theme-grid/working-card/)
- [首页与布局](/setting/home/)
- [驱动运行异常](/troubleshoot/runtime/)
