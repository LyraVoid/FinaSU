---
title: Lua 插件
createTime: 2026/10/24 00:00:00
permalink: /manager/plugin/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Lua 插件

FinaSU 的 Lua 插件由内置 Lua 5.5 解释器运行，存放在 `/data/adb/plugins/<id>/`。插件可以在启动阶段执行脚本、提供操作入口或以常驻任务运行。

## 安装

ZIP 根目录必须直接包含 `plugin.json` 与入口 Lua 文件，默认入口为 `main.lua`。安装器会检查 ID、入口名称、依赖、文件数量和压缩比，再原子发布插件目录。

## 生命周期

- `post_fs_data`、`service`、`boot_completed`：按依赖顺序参与自动调度。
- `action`：运行插件操作按钮或 `plugin action`。
- `main`：在声明后作为常驻任务启动。
- 自定义回调：通过 `plugin run <id> <callback>` 调用。

普通回调最多运行 300 秒，自动启动回调共享 120 秒预算。每次普通调用都会重新加载 Lua 状态，不能依赖全局变量跨调用保存数据。

## 安全边界

“安全标准库”不等于文件或权限沙箱。插件继承 `ksud` 的运行身份，可以执行系统程序并读写文件。只安装可信插件。

完整接口见 [Lua 插件接口](/developer/lua-plugin/)。
