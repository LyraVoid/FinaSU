---
title: 模块仓库
createTime: 2026/10/24 00:00:00
permalink: /manager/module-store/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 模块仓库

模块仓库把不同来源的模块目录统一为名称、版本、作者、说明和可下载版本。安装仍会走本地模块安装流程。

## 可用来源

- **Folk**：内置模块索引。
- **GMR/MMRL 集群**：默认社区仓库与可切换的集群仓库。
- **KernelSU Registry**：KernelSU 模块注册表。
- **自定义仓库**：填写兼容的仓库根地址或 JSON 索引地址。

来源页面显示模块数量、描述与仓库地址。切换来源后刷新列表；下载 URL 必须为 HTTP 或 HTTPS。

## 安装与校验

下载完成后，管理器会检查可用的文件大小与 SHA-256 信息，再进入模块安装确认。没有完整性元数据的来源只能依赖 TLS 与来源本身的可信度。

## 安全

仓库只负责分发，不代表模块经过 FinaSU 审核。模块以 Root 身份执行脚本，安装前应查看模块说明、作者和更新记录。
