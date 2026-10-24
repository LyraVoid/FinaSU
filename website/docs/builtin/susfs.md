---
title: SUSFS 与内核级隐藏
createTime: 2026/10/24 00:00:00
permalink: /builtin/susfs/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# SUSFS 与内核级隐藏

SUSFS 是一组与具体内核版本绑定的内核补丁，用于在内核层处理挂载、路径和 SELinux 痕迹。FinaSU 只提供对接入口，不包含完整的 SUSFS 内核实现。

## 能力门控

只有运行中的内核报告 SUSFS 支持时，管理器才会显示相关状态和控制项。看不到页面通常表示当前内核没有 `CONFIG_KSU_SUSFS`，或者驱动没有检测到兼容接口。

## 能做什么

在目标内核正确集成时，SUSFS 可以配合：

- 隐藏特定挂载或路径。
- 隐藏 SELinux 策略改动。
- 处理 AVC 审计日志，避免暴露目标上下文。

具体可用开关取决于内核侧补丁版本与构建选项，而不是只取决于 FinaSU 版本。

## 构建

内核需要打开 `CONFIG_KSU_SUSFS` 及相关子选项，并按目标内核版本应用匹配的 SUSFS 补丁。当前集成通常要求把 FinaSU 直接编入内核（`CONFIG_KSU=y`）。

补丁不应跨内核版本直接套用。构建与验证方法见 [内核集成](/developer/builtin-integration/)。

## 边界

SUSFS 不保证通过所有检测。应用还可能检查内核版本、系统属性、硬件认证、文件内容或行为差异。按应用组合 App Profile、NoMount 与 SUSFS，而不是期待一个全局开关。
