---
title: 提交问题报告
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/report/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 提交问题报告

一份可处理的问题报告应包含足够信息，让维护者区分安装、驱动、模块和应用层问题。

## 必填信息

- 设备型号与产品名。
- Android 版本和安全补丁级别。
- 内核完整版本与 KMI。
- FinaSU 管理器版本、驱动版本与 UAPI 版本。
- 安装方式：运行时 LKM、修补启动镜像、AnyKernel3、越狱或内核内置。
- 最近安装的模块、主题、Zygisk/Sui/NoMount 状态。
- 可重复步骤、预期结果和实际结果。

## 日志

附上“发送日志”导出文件；如果问题发生在启动阶段，同时提供 `dmesg` 与 `logcat`。不要提交管理器密码、WebDAV 密码或私钥。

## 安全

公开 issue 前移除设备序列号、账户名和不必要的包名。若问题涉及签名绕过、提权边界或数据泄露，先通过私密渠道联系维护者，不要直接公开利用细节。
