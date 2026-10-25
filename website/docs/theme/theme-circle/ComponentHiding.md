---
title: 组件隐藏
description: CircleUI 系统信息行、支持卡片和导航外在信息的显示边界
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-circle/component-hiding/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 组件隐藏

<Badge text="只影响显示" type="tip" /> <Badge text="基础事实始终保留" type="info" /> <Badge text="支持卡片可完全隐藏" type="warning" />

CircleUI 的隐藏设置不会改变检测结果，也不会卸载模块或禁用能力。它只减少首页上的视觉信息。

## 状态标签

“设置 → 外观 → 布局 → 隐藏状态标签”作用于 Circle 状态卡的 `LKM` / `Built-In` 标签。该开关在 Stats 顶部为 `list` 时也出现，但不作用于 Grid 卡片。

安全模式、越狱模式与自定义 LKM 是更关键的状态提示，不受这个开关控制。

## 系统信息行

::::: table 可隐藏行与实际门控

| 行 | 是否可隐藏 | 额外条件 |
| --- | --- | --- |
| 指纹 | 是 | 设备信息可用 |
| Zygisk 实现 | 是 | 当前存在 Zygisk 提供者 |
| SUSFS | 是 | 当前检测到 SUSFS |
| 模块挂载实现 | 是 | Folk Mount 状态可用且未读取失败 |

:::::

以下基础事实始终显示：

- 管理器版本；
- 内核版本；
- 内核完整版本（存在时）；
- 设备型号；
- SELinux；
- Seccomp。

隐藏 Zygisk 或 SUSFS 不会关闭 FolkZygisk、外部提供者或 SUSFS 内核功能。排查检测问题时，应重新显示对应行或去功能页查看本次启动状态。

## 底部支持卡片

底部卡片有四种模式：

:::: table 支持卡片

| 模式 | 实际显示 |
| --- | --- |
| 了解 FinaSU | 一张打开 FinaSU 网站的说明卡 |
| 支持开发与 KernelSU | 捐赠入口与 KernelSU 文档入口 |
| 自定义 | 用户填写的标题、副标题和 HTTP(S) 链接 |
| 隐藏 | 不绘制，也不保留空占位 |

::::

自定义模式三项必须全部非空。缺任何一项时，首页直接不绘制卡片；无效链接不会保存。切到“隐藏”不会删除已经保存的自定义内容。

## 导航与图标

导航入口、角标和自定义图标不属于 Circle 页面内的隐藏项。它们由导航设置与主题 `navIcons` 控制，详见[主题包结构](/developer/theme-package/#导航图标)。

## 相关页面

- [首页与布局](/setting/home/)
- [工作卡片](/theme/theme-circle/working-card/)
- [主题预览与使用](/theme/theme-use/)
