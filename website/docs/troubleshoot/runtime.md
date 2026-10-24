---
title: 驱动运行异常
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/runtime/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 驱动运行异常

## 管理器能打开但没有 Root

检查首页是否显示驱动可用。签名不匹配、LKM 未加载或内核 UAPI 不兼容都会让管理器只能展示离线状态。

## `ksud` 未运行

确认 `/data/adb/ksud` 存在且可执行。内置引擎读取失败时，重新安装与当前构建匹配的官方包，再重启。不要在用户态手动复制不匹配版本的 `ksud`。

## 模块不生效

检查 Folk Mount 是否处于“关闭挂载”，以及状态中记录的本次启动提供者。模块启用后通常要重启；NoMount 还需检查驱动 API 与例外应用。

## Zygisk 或 Sui 不工作

确认 FolkZygisk 已开启且外部提供者没有同时接管。Sui 还要求 Zygisk 提供者可用。查看两者诊断和 [SU 日志](/manager/su-log/)。

## 内核模块导入失败

核对架构、vermagic、模块名和依赖符号。查看该条目的“上次结果”，不要通过修改 `/sys/module` 或重复 `insmod` 猜测状态。
