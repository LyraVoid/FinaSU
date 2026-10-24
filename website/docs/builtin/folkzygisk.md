---
title: FolkZygisk
createTime: 2026/10/24 00:00:00
permalink: /builtin/folkzygisk/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# FolkZygisk

FolkZygisk 是 FinaSU 内置的 Zygisk 引擎，随管理器与 `ksud` 发布，不需要另外安装 Zygisk 提供者。

## 启用

在管理器设置中启用 FolkZygisk，然后重启。引擎状态、模块目录和最近一次启动诊断会显示在设置页。

## 外部提供者

`ksud` 会在打开内置引擎前检查已安装模块。若存在活动的 Zygisk 提供者，FolkZygisk 会让位，避免两个引擎同时注入 Zygote。移除外部提供者后，内置引擎会在下一次启动恢复。

## 启动保护

引擎会记录启动阶段是否完成、注入是否得到确认以及失败次数。连续失败达到限制后，启动保护会阻止继续尝试，保留状态供排查。

## 模块

Zygisk 模块仍由模块系统安装。一个模块是否需要 Zygisk、依赖哪个提供者，应以模块自身说明为准。Xposed 衍生框架需要安装在可用的 Zygisk 提供者上。

## 排错

先确认 FolkZygisk 已开启、重启完成、没有外部提供者同时接管，再检查启动诊断与内核日志。某些模块只支持特定 Android 版本或 Zygisk API。
