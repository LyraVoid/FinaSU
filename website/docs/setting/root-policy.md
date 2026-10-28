---
title: Root 与挂载策略
createTime: 2026/10/24 00:00:00
permalink: /setting/root-policy/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Root 与挂载策略

## 卸载模块（总开关）

这是 App Profile 模块卸载策略的总开关。启用后，内核可以按 UID 策略为目标应用隐藏模块修改。状态会区分为可管理、被策略强制或内核不支持。

该能力与具体模块挂载后端共同决定应用看到的文件。NoMount 使用自己的例外应用列表，不自动跟随 App Profile。

## 默认卸载模块

设置非 Root Profile 中“卸载模块”的全局默认值。启用后，未单独配置的应用会看到未注入模块的系统文件视图。

如果某个应用因为看不到模块文件而失效，应关闭单独应用的“卸载模块”选项，而不是直接关闭整个模块系统。

## 自定义卸载挂载点

在总策略之外，还可以为精确挂载点添加自定义规则。它只影响 App Profile 允许卸载模块的目标 UID，不递归处理子目录，也不能编辑模块自动注册的只读条目。

这个功能会改变目标 App 的真实 mount namespace；“隐藏挂载记录”只过滤 `/proc` 文本视图，两者不是同一机制。

完整的新手步骤见 [自定义卸载挂载点](/setting/umount-paths/)。

## 相关设置

- [自定义卸载挂载点](/setting/umount-paths/)
- [隐藏与内核安全](/setting/hiding/)
- [内置能力总览](/builtin/)
- [App Profile](/manager/app-profile/)
