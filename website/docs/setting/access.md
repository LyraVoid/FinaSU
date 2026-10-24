---
title: 访问控制
createTime: 2026/10/24 00:00:00
permalink: /setting/access/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 访问控制

## 允许任意动态管理器

默认情况下，只有预设管理器签名可以显示“设为动态管理器”。该实验选项允许对任意应用显示入口。启用后会显著扩大可被内核信任的管理器范围。

动态管理器的具体流程见 [动态管理器](/manager/dynamic-manager/)。

## ADB Root

以 Root 权限运行 `adbd`。该能力可能要求重启 ADB 服务，并受系统构建与 SELinux 限制。使用后应按需关闭。

## Shell 仅可通过 ADB 获取 Root

对已授权的 Shell 增加来源检查：只有由 `adbd` 或 KernelSU 系 Root 进程启动的 Shell 才能提权。它用于阻止普通应用先提权到 Shell，再借助 Shell 获得 Root。

## 建议

- 不使用动态管理器时保持实验入口关闭。
- ADB Root 只在调试期间开启。
- 保留官方管理器签名，不要用相同包名重新签名分发。
