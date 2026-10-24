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

## 内核级卸载模块

启用后，内核可以按 App Profile 的策略为目标应用隐藏模块修改。状态会区分为可管理、被策略强制或内核不支持。

该能力与具体模块挂载后端共同决定应用看到的文件。NoMount 使用自己的例外应用列表，不自动跟随 App Profile。

## 默认卸载模块

设置非 Root Profile 中“卸载模块”的全局默认值。启用后，未单独配置的应用会看到未注入模块的系统文件视图。

如果某个应用因为看不到模块文件而失效，应关闭单独应用的“卸载模块”选项，而不是直接关闭整个模块系统。

## 相关设置

- [隐藏与内核安全](/setting/hiding/)
- [内置能力总览](/builtin/)
- [App Profile](/manager/app-profile/)
