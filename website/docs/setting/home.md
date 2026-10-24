---
title: 首页与布局
createTime: 2026/10/24 00:00:00
permalink: /setting/home/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 首页与布局

## 布局

FinaSU 当前提供五种首页布局：CircleUI、GridUI、FocusUI、DashboardUI 和 StatsUI。GridUI 使用双列卡片，FocusUI 会随屏幕方向重排，StatsUI 以超级用户和模块计数为中心。

首页布局与主题区的布局参数分别保存。切换布局不会修改 Root 状态或模块。

## 布局变体

插件、超级用户和模块页面可以选择传统紧凑顶栏或 M3E 大标题与可折叠搜索栏。

## 顶栏标题

- **顶栏标题**：没有标题图片时显示的文字。
- **标题图片**：用图片替换文字标题。
- **标题偏移**：调整标题图片的位置。

## 支持卡片

首页底部卡片可以显示 FinaSU 介绍、传统支持内容、隐藏或自定义内容。自定义模式可以设置标题、副标题和 HTTP(S) 链接；不合法链接不会保存。

## 首页信息

可以隐藏系统指纹、Zygisk 实现、SUSFS 和模块挂载行。Zygisk 和 SUSFS 行只在对应能力实际存在时显示。隐藏只改变展示，不改变检测或功能状态。

## 其他

- **启动器图标**：选择管理器图标样式。
- **界面缩放**：调整全局显示比例。
- **壁纸图库**：从设置首页打开系统图片选择器和管理壁纸。
