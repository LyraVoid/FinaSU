---
title: 备份与 WebDAV
createTime: 2026/10/24 00:00:00
permalink: /manager/backup/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 备份与 WebDAV

备份页面可以把选定内容写到本地目录或 WebDAV 服务器，并从备份列表校验和恢复。

## 备份目标

- **本地目录**：`Download/FinaSU`。
- **WebDAV**：填写服务器地址、用户名、远程路径和密码。

密码保存在可逆的秘密存储中，以便发起 WebDAV 请求。不要把服务器凭据写入公开日志或文档。

## 备份内容

- 模块：可在刷写前归档本次涉及的模块。
- 超级用户列表：授权应用与策略。
- 启动分区：管理器检测到的 `boot`、`init_boot` 或 `vendor_boot`。

分区镜像体积较大。备份前确认目标空间充足，并确保 WebDAV 支持所需的 HTTP 方法。

## 恢复

从本地或 WebDAV 列表选择条目，管理器会在恢复前校验可用元数据。分区恢复会写入真实分区，操作前确认设备与系统版本。

## 刷写前备份

安装页面可以启用“刷写前备份”，先向全部已配置目标写入快照。部分目标失败时会报告写入、跳过和失败数量；不要忽略部分失败。
