---
title: 设备兼容性
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/compatibility/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 设备兼容性

## 内核

设备必须运行与随包资产匹配的 KMI。`android13-5.10`、`android14-6.1` 等名称不能混用。GKI 5.10 或更高通常使用运行时 LKM；旧内核可能需要自行编译和移植。

## 架构

主要资产面向 `arm64-v8a`。x86_64 有单独构建；riscv64 不支持全部内置引擎与内核模块资产。管理器会报告缺失能力。

## 系统

- 系统版本与启动分区布局必须一致。
- Android 13 及以后拆分 ramdisk 的设备通常修补 `init_boot`，以管理器检测为准。
- 锁定 Bootloader、启用 AVB 或厂商完整性校验会阻止或破坏刷入。
- 安全补丁级别与内核构建不匹配可能导致 `vermagic` 或符号错误。

## 其他 Root

安装前移除 Magisk、KernelSU、APatch 的启动修改。多个驱动、模块挂载器或 Zygisk 提供者同时运行，会造成无法预测的冲突。

确认完整版本信息的方法见 [诊断与反馈](/setting/diagnostics/)。
