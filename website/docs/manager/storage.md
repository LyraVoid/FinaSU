---
title: /data/adb 存储结构
createTime: 2026/10/24 00:00:00
permalink: /manager/storage/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# /data/adb 存储结构

FinaSU 与模块共享 `/data/adb`。以下路径用于排查，不应在运行中随意删除。

| 路径 | 内容 |
| --- | --- |
| `/data/adb/ksud` | 设备上的 `ksud` |
| `/data/adb/modules/` | 已安装 Android 模块 |
| `/data/adb/modules_update/` | 等待合并的模块更新 |
| `/data/adb/metamodule` | 活动元模块的符号链接 |
| `/data/adb/plugins/` | Lua 插件 |
| `/data/adb/ksu/bin/` | `ksud` 与内置引擎使用的外部二进制 |
| `/data/adb/ksu/log/` | 管理器、挂载与相关日志 |
| `/data/adb/ksu/lkm/` | 受管 Linux 内核模块 |
| `/data/adb/ksu/profile/` | App Profile 模板与 SELinux 规则 |
| `/data/adb/ksu/sui/` | Sui 状态、日志和权限数据 |
| `/data/adb/ksu/mount_mode` | Folk Mount 下次启动使用的提供者 |

`.allowlist`、`.dynamic_manager`、`zygisk.json` 等点文件由内核或 `ksud` 管理。修改格式错误可能使授权、管理器信任或引擎配置失效。

备份时优先使用管理器提供的导出功能。手动复制前停止相关写入，并保留文件权限与 SELinux 上下文。
