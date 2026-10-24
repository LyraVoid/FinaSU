---
title: SU 日志
createTime: 2026/10/24 00:00:00
permalink: /manager/su-log/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# SU 日志

SU 日志记录与 Root 相关的事件，便于确认应用是否真正发起授权以及 `ksud` 是否正常运行。

## 事件类型

- **Root execve**：Root 上下文执行命令。
- **经典 SU**：通过 `/system/bin/su` 兼容入口提权。
- **授予 Root**：管理通道下发授权。
- **守护进程重启**：`ksud` 生命周期事件。

可以按类型过滤、查看日志文件并清理记录。内核未提供日志能力时页面会显示不支持；日志开关被强制开启时不能从管理器关闭。

## 隐私

日志可能包含包名、UID、命令路径和进程名称。提交日志前检查是否含有敏感路径或个人信息。

## 使用建议

授权弹窗未出现时，先确认目标请求是否出现在日志中，再检查管理器是否被系统回收。已经授权却无法提权的应用还需要检查 App Profile。
