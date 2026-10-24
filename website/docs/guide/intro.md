---
title: FinaSU 简介
createTime: 2026/10/24 00:00:00
permalink: /guide/intro/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# FinaSU 简介

FinaSU 是面向 Android 的内核级 Root 方案。`su` 请求与白名单由内核中的驱动裁决，管理器负责展示状态、修改策略和调用 `ksud`。

## 组成

- **内核驱动**：提供 `su`、白名单、App Profile、SUSFS 接口和隐藏能力。
- **ksud**：用户态命令行工具，由启动事件或管理器调用，处理模块、插件、Zygisk、Sui 与 NoMount。
- **管理器**：安装与更新、超级用户管理、模块仓库、主题和设置界面。
- **内置引擎**：Folk Mount、FolkZygisk、Sui 与 NoMount，由 `ksud` 按配置启动。

## 运行方式

FinaSU 可以修补启动镜像，也可以在系统启动后加载内核模块。越狱模式只在当前启动会话提供 Root，重启后需要重新进入。

## 边界

FinaSU 不会替用户判断第三方模块是否可信。内核模块、Lua 插件和 Root 应用都拥有很高权限，只应安装来源明确的内容。

继续阅读 [快速开始](/guide/get-started/) 或 [安装方式](/guide/install/)。
