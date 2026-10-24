---
title: 从 Magisk 切换
createTime: 2026/10/24 00:00:00
permalink: /guide/switch/magisk/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 从 Magisk 切换

Magisk 与 FinaSU 都可能接管启动链和模块注入，不应同时依赖。切换前备份原厂启动镜像、可用的 Magisk 配置以及需要保留的模块。

## 步骤

1. 记录当前系统版本、KMI 和启动分区。
2. 在 Magisk 中执行卸载，或刷回与当前系统完全一致的原厂启动镜像。
3. 确认设备不再由 Magisk 提供 Root。若 Magisk 仍参与启动，不要继续叠加 FinaSU。
4. 按 [安装方式](/guide/install/) 修补并刷入 FinaSU。
5. 重启后逐个安装兼容模块，不要一次恢复全部旧模块。

## 模块

Magisk 模块的 ZIP 结构通常兼容，但依赖 Magisk 专有接口的脚本可能失败。先确认模块说明支持 KernelSU 系方案，并准备安全模式救援流程。

Zygisk 模块需要可用的 Zygisk 提供者。FinaSU 内置 [FolkZygisk](/builtin/folkzygisk/)；已有其他提供者时，应只保留一个活动实现。
