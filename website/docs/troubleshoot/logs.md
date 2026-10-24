---
title: 日志与反馈
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/logs/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 日志与反馈

## 管理器日志

使用“设置 → 发送日志”导出管理器日志。SU 事件在“超级用户 → SU 日志”中查看和筛选。

## 设备日志

有 ADB 时，可以采集：

```sh
adb shell dmesg > dmesg.txt
adb logcat -d > logcat.txt
adb shell /data/adb/ksud module list
```

Root Shell 中还可以查看 `/data/adb/ksu/log/`。模块安装脚本与挂载日志的路径由当前实现决定，不要假定每个模块都使用同一文件名。

## 提交前处理

- 去除账号、包名、路径和设备序列号中的敏感信息。
- 保留时间线、退出码和错误文本。
- 同时说明管理器版本、内核完整版本、KMI、Android 版本和安装方式。
- 记录最近安装或修改的模块、主题、内置引擎和安全设置。

日志不完整时，先增加一个可重复操作并重新采集，而不是发送多份没有时间线的截图。
