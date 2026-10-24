---
title: App Profile 模板
createTime: 2026/10/24 00:00:00
permalink: /setting/profile-template/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# App Profile 模板

模板把一组 Root Profile 参数保存为可复用配置。入口位于“设置 → 通用 → App Profile 模板”。

::: tip 先用预设，不必自己配置

页面会加载本地和在线预设。一般用户直接选择用途合适的预设即可，不需要了解 UID、GID、用户组、capabilities 或 SELinux 域，也不需要创建自定义模板。

:::

## 字段

- **ID**：本地唯一标识；选用模板时会记录该 ID。
- **名称**：显示名称。
- **描述**：模板用途和适用范围。
- **配置**：UID、GID、用户组、capabilities、SELinux 域与 `no_new_privs` 等 Root Profile 值。

选择模板时，管理器会把当时的模板字段复制到应用 UID；模板不是实时继承。修改模板不会自动改写已经保存的应用策略，需要回到对应 App Profile 重新选择并应用。模板编辑器也不会列出所有曾使用该 ID 的应用，修改前应自行记录影响范围。

## 导入导出

模板可以导出到剪贴板，也可以从剪贴板导入。只导入来自可信来源的模板；SELinux 域和 capabilities 的错误组合可能扩大或破坏权限。

应用级策略与模板的对应关系见 [App Profile](/manager/app-profile/)。
