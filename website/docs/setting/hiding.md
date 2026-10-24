---
title: 隐藏与内核安全
createTime: 2026/10/24 00:00:00
permalink: /setting/hiding/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 隐藏与内核安全

## 隐藏 SELinux 修改

阻止应用检测由 FinaSU 加载的 SELinux 策略变化。内核不支持或策略强制时，开关会显示不可修改状态。通常需要重启生效。

## 隐藏挂载记录

从普通应用与隔离进程的 `/proc` 挂载视图中移除 Root 和模块相关记录。该能力受内核构建与运行状态限制；提示不可用时不要反复切换。

## AVC 日志伪装

处理 SELinux 审计日志中的目标上下文，避免日志直接暴露 FinaSU 相关对象。它不会隐藏所有系统差异，也不替代按应用卸载模块。

## SELinux 模式

可以查看并切换 Enforcing、Permissive 或 Disabled 状态，实际可选范围由系统与内核决定。Permissive 会降低系统安全边界，除越狱流程外不应长期保持。

::: warning
不要同时启用互相冲突的隐藏方案。改动后重启，分别验证授权、模块加载和应用文件视图。
:::
