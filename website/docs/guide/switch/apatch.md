---
title: 从 APatch 切换
createTime: 2026/10/24 00:00:00
permalink: /guide/switch/apatch/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 从 APatch 切换

APatch 使用内核补丁与自己的模块机制。FinaSU 不能接管正在运行的 APatch 驱动，切换前必须回到原厂启动状态。

## 步骤

1. 导出 APatch 中仍需保留的模块或配置。
2. 使用 APatch 的卸载能力恢复原厂镜像，或手动刷回备份的原厂启动分区。
3. 重启并确认 Root 已移除、启动分区与系统版本匹配。
4. 按 [安装方式](/guide/install/) 安装 FinaSU。
5. 在新的模块环境中逐个恢复兼容内容。

若设备无法启动，立即按 [启动循环救援](/guide/rescue-bootloop/) 恢复原厂镜像。
