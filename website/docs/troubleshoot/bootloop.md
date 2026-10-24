---
title: 无法开机
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/bootloop/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 无法开机

先区分启动循环发生在内核加载之前还是模块挂载之后。

## 尝试安全模式

首次出现开机画面后快速按三次音量下键，每次按下后松开。进入系统后检查最近安装或更新的模块。

## 恢复原厂镜像

如果安全模式无法进入，在 `fastboot` 或 Recovery 中刷回同版本原厂 `boot`、`init_boot` 或安装时修改的分区。

没有备份时，从对应固件包提取镜像。不要混用其他安全补丁级别、设备型号或槽位的镜像。

## 常见原因

- LKM 与当前 KMI 不匹配。
- AnyKernel3 包写入了不兼容分区。
- 模块在 `post-fs-data.sh` 或 `service.sh` 中阻塞启动。
- 元模块与内置 Folk Mount 冲突。
- A/B 设备安装了错误槽位。

恢复后先读取 [日志](/troubleshoot/logs/)，再重新安装和逐个启用模块。
