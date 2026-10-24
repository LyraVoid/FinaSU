---
title: App Profile
createTime: 2026/10/24 00:00:00
permalink: /manager/app-profile/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# App Profile

App Profile 把 Root 权限和文件可见性限定在单个应用范围内。每个应用可以分别配置 Root Profile 与非 Root Profile。

## Root Profile

Root Profile 在应用获得 Root 后生效，可设置：

- UID、GID 与附加用户组。
- Effective、Permitted 和 Inheritable capabilities。
- SELinux 域与额外规则。
- 挂载命名空间：继承、全局或独立。
- `no_new_privs`：阻止从该上下文继续提升权限。

降低 capabilities 或限制 SELinux 域可能使应用无法完成正常功能。修改后应重新启动目标应用验证。

## 非 Root Profile

非 Root Profile 主要控制“卸载模块”。启用后，该应用看到的是模块注入前的文件视图，适合只检查挂载或文件完整性的应用。

## 模板

在“设置 → App Profile 模板”中可以创建、编辑、删除和导入导出模板。模板通过 ID 复用；修改模板会影响所有引用它的应用。

## 排查

如果应用已被授权却仍显示没有 Root，先检查 Root Profile 是否限制了 capabilities、SELinux 域或命名空间，再检查非 Root Profile 的模块卸载策略。
