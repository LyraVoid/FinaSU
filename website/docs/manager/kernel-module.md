---
title: 内核模块
createTime: 2026/10/24 00:00:00
permalink: /manager/kernel-module/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 内核模块

“内核模块”页面管理用户主动导入的 Linux `.ko` 文件，与 Android ZIP 模块不同。条目存放在 `/data/adb/ksu/lkm/<id>/`，通过 `ksud kmod` 操作。

## 导入

导入时读取模块元数据，要求内部模块名明确，并拒绝与内置 `kernelsu`、`nomount` 标识冲突的文件。模块必须匹配设备内核架构、vermagic 和符号环境。

## 状态

- **开机加载**：下次启动的偏好。
- **禁用**：持久阻止加载，但不会卸载当前实例。
- **当前已加载**：从 `/sys/module/<name>` 读取，不根据上一次命令结果推断。

“立即加载”“阻止后续加载”“允许后续加载”是独立操作：启用不会立即加载，禁用不会立即卸载，移除也不会卸载已经进入内核的实例。

## 参数与日志

参数会作为一个 NUL 结尾字符串传给加载器，不会被 Shell 求值。每次加载的结果和启动 ID 会保存在条目中，便于定位符号或 vermagic 错误。

## 安全模式

安全模式会为所有受管内核模块写入禁用标记。导入与移除仍可用，但加载和启用会被拒绝。安全模式无法恢复内核崩溃。
