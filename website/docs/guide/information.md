---
title: 基础概念
createTime: 2026/10/24 00:00:00
permalink: /guide/information/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 基础概念

## KMI

KMI（Kernel Module Interface）描述内核外部模块可依赖的稳定接口版本。FinaSU 发布的 LKM 与 NoMount 模块按 KMI 构建，文件必须与设备内核匹配，例如 `android13-5.10`。

## GKI

GKI（Generic Kernel Image）把通用内核与厂商模块拆分。基于现有 GKI 内核加载 LKM 可以避免每次修改内核后重新编译完整设备内核。非 GKI 设备可能需要自行集成本项目。

## 元模块

元模块是 `module.prop` 中声明 `metamodule=1` 的特殊模块。它接管普通模块的挂载或安装钩子，同一时间只能有一个活动元模块。FinaSU 默认不是必须安装元模块。

## 模块目录

- `/data/adb/modules/`：已安装模块。
- `/data/adb/modules_update/`：等待合并的模块更新。
- `/data/adb/ksu/`：FinaSU 配置、日志、LKM 和运行时状态。
- `/data/adb/ksud`：设备上的 `ksud` 可执行文件。

`disable`、`remove` 与 `skip_mount` 标记分别表示禁用、待移除和跳过挂载。

## 运行模式

管理器会区分驱动是修补镜像后加载、内核内置，还是当前启动会话的越狱模式。不同模式影响更新、重启与刷写行为。
