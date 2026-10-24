---
title: 超级用户
createTime: 2026/10/24 00:00:00
permalink: /manager/superuser/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 超级用户

“超级用户”页面列出已授权、已拒绝和曾请求 Root 的应用。授权决定由内核保存，重启后仍然有效。

## 授权与拒绝

应用首次请求时可选择允许或拒绝。错过弹窗时，在列表中查找该应用并修改决定，不一定要等待下一次请求。

拒绝记录不会删除应用数据。再次授权后，部分应用需要强制停止并重新启动才会重新检测 Root。

## App Profile

点击应用可以进入 [App Profile](/manager/app-profile/)，为 Root Profile 和非 Root Profile 分别设置策略。

## SU 日志

日志入口记录 Root 执行、传统 `su`、授权和守护进程事件。详见 [SU 日志](/manager/su-log/)。

## 搜索与排序

可以按应用名称搜索，并查看多用户或工作资料中的应用。修改某个应用的策略时，确认它影响的 UID；同一应用包在不同用户下的 UID 可能不同。
