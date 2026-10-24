---
title: Root 授权请求不出现
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/root-request/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Root 授权请求不出现

授权请求由内核通知管理器处理，不是应用直接弹出。

## 检查顺序

1. 打开“超级用户”页面，确认应用是否已有允许或拒绝记录。
2. 查看 [SU 日志](/manager/su-log/)，确认请求是否到达内核。
3. 确认管理器进程没有被省电策略、Force Stop 或厂商清理机制杀死。
4. 在息屏、后台限制或授权超时后重新启动目标应用。
5. 检查 App Profile 是否限制了应用的 capabilities 或 SELinux 域。

## 应用仍提示无 Root

部分应用只在启动时检测一次。授权后强制停止并重新打开。若日志显示授权成功但应用仍失败，问题可能来自文件可见性、SELinux 或应用自身检测。

## Shell

“限制 Shell 仅可通过 ADB 获取 Root”启用时，从普通终端应用启动的 Shell 可能被拒绝。检查该设置和调用来源。
