---
title: FinaSU 简介
description: FinaSU 的定位、核心特性与项目入口
createTime: 2026/10/24 00:00:00
permalink: /guide/intro/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

**欢迎使用 FinaSU！**

==FinaSU== 是一款基于 [KernelSU](https://github.com/tiann/KernelSU) 的内核级 ROOT 管理器，保留内核授权、App Profile 和模块体系，并把常用引擎、主题与日常管理集中到同一套界面。

FinaSU 不要求所有用户接受同一种界面。无论你喜欢简约布局、动态背景，还是需要 Zygisk、Sui、模块挂载和系统级管理，都可以在同一套管理器中完成配置。

## 核心特性

- **高度自定义主题**：5 套首页布局、15 组主题色、图片与视频背景、自定义字体、导航图标、背景音乐、触摸音效和振动反馈。
- **内置能力**：Folk Mount、FolkZygisk 和 NoMount 随 FinaSU 发布，由 `ksud` 管理，不需要再安装同类上游模块。内置能力与 Built-In 安装方式不是一回事。
- **内置 Sui**：为兼容 Shizuku API 的应用提供 Root 或 Shell 后端。Sui 依赖 Zygisk，内置 FolkZygisk 和外部兼容提供者都可使用；外部提供者下 FinaSU 无法验证注入结果。
- **内核级 Root**：`su`、白名单和 App Profile 由内核驱动裁决，可为单个应用设置 UID/GID、能力、SELinux 域和模块卸载策略。
- **模块与插件**：支持 systemless 模块、WebUI、Metamodule 和 Lua 插件，也可从内置、社区、KernelSU 或自定义仓库获取模块。
- **内核功能**：支持 UTS View 与按 UID 的网络隔离；SUSFS 实现不在 FinaSU 仓库内，需要内核源码集成或加载匹配的 SUSFS-LKM。
- **三种安装方式**：LKM 修改 ramdisk，Built-In 将驱动编入内核，越狱模式不修改启动分区但重启后不保留 Root。
- **备份与恢复**：可备份超级用户列表、刷写前模块包和指定启动分区，并保存到本地目录或 WebDAV。

如果能给我一个 star，那将是对我莫大的鼓励。

<RepoCard repo="LyraVoid/FinaSU" />

::: tip
如果您想交流 FinaSU，请加入：

- Telegram: [FinaSU](https://t.me/FinaSU)

:::
