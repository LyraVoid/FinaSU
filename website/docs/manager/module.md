---
title: 模块管理
createTime: 2026/10/24 00:00:00
permalink: /manager/module/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 模块管理

模块页面管理 `/data/adb/modules` 中的 ZIP 模块。安装器支持 FinaSU、KernelSU 和常见的 Magisk 模块格式，但模块脚本自身的兼容性仍由作者决定。

## 安装

安装前页面会列出 ZIP 中的模块名称并请求确认。安装完成后通常需要重启才能让文件注入生效；支持热安装的模块可能在当前会话立即产生部分效果。

## 启停与移除

- **禁用**：写入 `disable` 标记，使模块不参与后续启动。
- **跳过挂载**：保留脚本能力，但不注入文件。
- **移除**：先标记，再在后续处理阶段清理。

已加载的模块脚本或内核对象不保证在移除操作后立刻从内存消失。

## 更新与操作

模块卡片可以显示更新、操作按钮和 WebUI。操作按钮运行模块提供的 `action.sh`；WebUI 由模块自带页面决定，调试 WebView 时可在设置中开启 WebView 调试。

## 元模块

元模块会接管模块安装或挂载钩子，同时只能有一个活动元模块。卸载元模块会影响依赖它的全部模块。
