---
title: 交互与兼容
createTime: 2026/10/24 00:00:00
permalink: /setting/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 交互与兼容

## 传统 SU 命令支持

控制 `/system/bin/su` 兼容入口：

- **启用（默认）**：允许传统调用。
- **禁用直到下次重启**：当前启动禁用。
- **始终禁用**：后续启动保持禁用。

内核不支持或策略被强制时，选项会显示对应状态。关闭后，只通过绝对路径调用 `su` 的应用可能无法提权。

## 软重启

安装、更新或启停模块时，用软重启代替完整重启。软重启能更快重新加载用户态服务，但无法替代启动镜像或内核驱动更新。越狱模式会强制使用软重启以保留当前 Root。

## WebView 调试

为模块 WebUI 开启 WebView 调试。该选项扩大调试接口暴露面，只在开发或排查页面问题时启用。

## 自动越狱

仅在越狱模式可配置。开机检测到 Permissive SELinux 时，管理器通过 Magica 自动尝试提权。需要授予管理器自启动权限，并且不能保证每次环境变化后都成功。
