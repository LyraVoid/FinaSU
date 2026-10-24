---
title: 内置能力
createTime: 2026/10/24 00:00:00
permalink: /setting/builtin/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 内置能力

功能页集中显示由 `ksud` 驱动的内置引擎。每项能力都有独立状态和可用条件。

## FolkZygisk

查看引擎开关、外部提供者、模块与最近启动诊断。启用和引擎更新通常需要重启。详见 [FolkZygisk](/builtin/folkzygisk/)。

## Sui

管理内置 Shizuku API 后端、载荷、权限与诊断。Sui 需要 Zygisk 提供者。详见 [Sui](/builtin/sui/)。

## Folk Mount

选择自动、内置、仅元模块、NoMount 或关闭挂载。状态栏会显示本次启动实际使用的提供者，修改只影响下一次启动。详见 [Folk Mount](/builtin/folk-mount/)。

## NoMount

查看驱动、注入规则、模块贡献和例外应用。驱动 API 过旧或缺少当前 KMI 的模块时，应改用其他挂载后端。详见 [NoMount](/builtin/nomount/)。

## 内核信息伪装

允许选中的应用看到不同的内核身份信息，也可以对全部应用生效。配置时应保持字段自洽；错误的版本、编译时间或架构组合可能引发新的兼容性问题。

## 网络隔离

阻止所选应用建立新的互联网连接。已建立的连接不保证立即中断，系统组件与自身进程也不应被误加入列表。
