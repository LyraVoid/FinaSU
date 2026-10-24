---
title: NoMount
createTime: 2026/10/24 00:00:00
permalink: /builtin/nomount/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# NoMount

NoMount 通过内核侧的 VFS 路径解析与目录遍历重定向注入模块文件，不建立传统挂载。因此进程看到的挂载表可以保持原样。

## 驱动

内置 NoMount 后端需要匹配当前 KMI 的内核模块。页面会区分内核内置驱动与可加载模块，并只接受 API 22 或更新的协议。

驱动不可用时：

- 当前构建没有此 KMI 的模块，改用自动或内置 Folk Mount。
- 驱动版本过旧，需要更新内核侧实现。
- 驱动尚未加载，可重启或先执行一次注入。

## Folk Mount 选择

在 Folk Mount 中选择“内置（VFS）”后，下次启动由 NoMount 注入。若驱动加载失败，内置引擎会回退，并在状态中报告原因。

## 可见性

NoMount 起初不会按 App Profile 排除任何应用。页面可以维护“被排除的应用”和“隐藏于隔离进程”。启用“跟随应用 Profile”只会复制当前策略，之后不会自动同步。

## 规则

模块页面会显示每个模块贡献的重定向规则数量。可以立即注入模块，也可以在本次启动内卸载规则；重启会重建规则表。

## 边界

NoMount 隐藏的是传统挂载痕迹，不保证绕过所有完整性检查。SELinux、审计日志和文件内容差异仍可能需要其他策略配合。
