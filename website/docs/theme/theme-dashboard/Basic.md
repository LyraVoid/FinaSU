---
title: 基础样式
description: DashboardUI Hero 的布局、内容、图片 surface 与响应式边界
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-dashboard/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 基础样式

<Badge text="Hero 优先" type="tip" /> <Badge text="纵向卡片流" type="info" /> <Badge text="Hero 有独立表面" type="warning" />

Dashboard 的视觉重点是一张约占整宽的 Hero，内部使用较大内边距和三层信息：状态、状态附加项、三项事实。

## Hero 内容

:::: table Hero 区域

| 区域 | 内容 |
| --- | --- |
| 左上 | 状态图标与运行中/未安装/不支持 |
| 标题下 | 版本摘要、点击安装提示或不支持原因 |
| 状态行 | 运行模式、安全模式、越狱模式、自定义 LKM |
| 底部三项 | 模块版本、内核、SELinux |

::::

三项事实使用等宽列。值过长时省略，不会把整张卡撑出屏幕。

## Hero 背景

主题路径：

```text
dashboard_card_bg.jpg
dashboard_card_bg.png
dashboard_card_bg.gif
dashboard_card_bg.webp
dashboard_card_bg.jpeg
```

surface：

```text
layout.dashboard.hero
```

支持：

- 图片与启用开关；
- 单一或日夜不透明度；
- 单一或日夜暗度。

图片加载完成前，Hero 使用不透明语义表面；加载后切换为透明卡片并给文字使用适配图片的对比色，避免白字先落在空白背景上。

## 计数卡

完整环境下，超级用户与模块计数横向排列。计数不可用时，它们仍可保留占位，具体是否绘制由完整环境判断。

## 系统信息

Dashboard 自己包含系统信息卡，不会在页面外再重复绘制一张全局 InfoCard。内容和其他布局一致，受相同隐藏项控制。

## 响应式行为

当前代码没有 Dashboard 专用宽屏分支。不要用 Focus 的 2×2 条件推断 Dashboard 也会分栏。

## 相关页面

- [状态信息](/theme/theme-dashboard/hardware/)
- [全局背景](/theme/global/transparency/)
- [首页与布局](/setting/home/)
