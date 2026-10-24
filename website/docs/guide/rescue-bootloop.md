---
title: 启动循环救援
createTime: 2026/10/24 00:00:00
permalink: /guide/rescue-bootloop/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 启动循环救援

## 先停用模块

在设备首次出现开机画面后，快速按三次音量下键，每次按下后松开。模块会在本次启动中被禁用。

进入系统后打开管理器，逐个排查最近安装或更新的模块。导致问题的模块通常需要继续停用，而不是只重启一次。

## 恢复启动镜像

如果无法进入安全模式：

1. 进入 `fastboot` 或设备可用的第三方 Recovery。
2. 刷回与当前系统完全一致的 `boot`、`init_boot` 或其他被修改分区的原厂备份。
3. 重启并确认设备恢复。

没有备份时，从同版本固件包提取启动镜像。不要使用不同系统版本或不同设备的镜像。

## 常见原因

- KMI 与 LKM 文件不匹配。
- 使用了不兼容的 AnyKernel3 安装包。
- 模块在 `post-fs-data.sh` 或 `service.sh` 中破坏了启动环境。
- OTA 后槽位与启动镜像不一致。

恢复后检查日志，再决定是否重新安装。参见 [日志与反馈](/troubleshoot/logs/)。
