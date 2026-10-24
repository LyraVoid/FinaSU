---
title: Sui
createTime: 2026/10/24 00:00:00
permalink: /builtin/sui/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Sui

Sui 是 FinaSU 内置的 Shizuku API 后端。它把 API 服务提供给兼容应用，因此不需要再安装 Shizuku 本体。

## 依赖

Sui 以 Zygisk 模块形式运行，需要可用的 Zygisk 提供者。FolkZygisk 已内置；若改用其他提供者，也应保持单一活动实现。

## 生命周期

载荷内嵌在 `ksud` 中，安装在 `/data/adb/modules/finasu-sui`。管理器负责启用、停用、修复和卸载，不依赖 Sui 自己的安装脚本。

启用与载荷更新通常需要重启或等待服务重新启动。修复会从当前 `ksud` 重新发布载荷并核验文件哈希。

## 权限

应用请求 API 权限时，管理器会显示授权请求。设置页可以配置按应用授权、默认权限、对 Sui 隐藏的应用和诊断信息。

只有受信任的 FinaSU 管理器包可以管理 Sui。若检测到旧的冲突模块 `zygisk-sui`，应先将其移除，避免两个服务竞争。

## 排查

确认 FolkZygisk 或外部提供者已经工作，再查看 Sui 的载荷、守护进程与权限诊断。仅安装兼容应用并不会自动授予权限。
