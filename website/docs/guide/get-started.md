---
title: 快速开始
createTime: 2026/10/24 00:00:00
permalink: /guide/get-started/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 快速开始

## 准备

1. 确认设备为 `arm64-v8a`，并已根据自己的风险承受能力处理 Bootloader。
2. 安装官方 FinaSU 管理器。内核会校验管理器签名，其他签名的构建无法直接管理驱动。
3. 在管理器中确认当前内核版本与 KMI，例如 `android13-5.10`。
4. 备份当前 `boot`、`init_boot` 或管理器提示的分区。
5. 卸载其他内核级 Root 方案，避免多个驱动同时接管启动链。

## 安装

有 Root 时可直接安装；首次安装通常选择“选择文件并修补”，再用 `fastboot` 刷回对应分区。A/B 设备在 OTA 后可以改用“安装到未使用的槽位”。

详细步骤见 [安装方式](/guide/install/)。

## 验证

打开管理器首页，确认内核状态可用，然后访问“超级用户”页面。终端应用首次请求 Root 时应出现授权界面。模块、Zygisk 与 Sui 的改动通常要重启后才生效。

::: warning
不要在未备份启动镜像时尝试不熟悉的模块或内核文件。无法开机时参见 [启动循环救援](/guide/rescue-bootloop/)。
:::
