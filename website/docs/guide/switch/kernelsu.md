---
title: 从 KernelSU 切换
createTime: 2026/10/24 00:00:00
permalink: /guide/switch/kernelsu/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 从 KernelSU 切换

FinaSU 保留了 KernelSU 的核心模型，但管理器信任、内置引擎和部分存储协议已经不同。不要把两个驱动或两个管理器混用。

## 步骤

1. 备份当前启动镜像、白名单和需要保留的模块。
2. 使用原 Root 方案卸载，或刷回原厂启动镜像。
3. 重启确认原内核驱动已不再提供 Root。
4. 安装官方 FinaSU 管理器，按 [安装方式](/guide/install/) 完成修补。
5. 在新管理器中重新授权应用，检查 App Profile 与模块。

## 数据

`/data/adb/modules` 与部分配置可能继续存在，但这不代表所有结构都兼容。迁移后先停用旧模块，再按需启用；不要直接复制另一套驱动或管理器 APK。
