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

- **ID**：本地唯一标识；创建后用于引用模板。
- **名称**：显示名称。
- **描述**：模板用途和适用范围。
- **配置**：UID、GID、用户组、capabilities、SELinux 域与 `no_new_privs` 等 Root Profile 值。

修改模板会影响引用它的应用。管理器不会替你判断这些应用能否接受新的权限组合；自定义前应自行确认使用范围，并先在测试应用上验证。

## 导入导出

模板可以导出到剪贴板，也可以从剪贴板导入。只导入来自可信来源的模板；SELinux 域和 capabilities 的错误组合可能扩大或破坏权限。

应用级策略与模板的对应关系见 [App Profile](/manager/app-profile/)。
