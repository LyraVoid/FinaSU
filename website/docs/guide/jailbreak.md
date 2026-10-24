---
title: 越狱与延迟加载
createTime: 2026/10/24 00:00:00
permalink: /guide/jailbreak/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 越狱与延迟加载

越狱模式（late load）在当前系统启动后加载 FinaSU 驱动。它适合无法直接修补启动镜像、但设备已经具备提权入口的场景。

::: tip 首页 Jailbreak 是兼容方案

FinaSU 首页的 **Jailbreak** 依赖 Permissive SELinux 或已有的 Magica 提权环境，属于较宽容的兼容路径。当前更常见的是先用 CVE 漏洞取得 uid 0，再执行 FinaSU 的 `ksud late-load`，例如 GhostLock（CVE-2026-43499）。优先使用可以选择管理器的动态提权工具：这类工具会从所选管理器的原生库目录读取 `libksud.so`，而不是只认固定包名；选中 FinaSU 后即可使用它自带的 `ksud`。CVE 工具需要与设备内核精确匹配的 profile，漏洞修复后也不能使用。固定工具的手工适配见[从 KernelSU 切换](/guide/switch/kernelsu/)。

:::

## 前提

- Bootloader 通常仍需解锁。在锁定设备上刷写分区会破坏 AVB，可能导致无法启动。
- 当前 SELinux 状态和提权入口必须满足越狱流程的要求。
- 管理器需要自启动权限，才能在开机后重新执行自动越狱。

## 操作

管理器检测到可用的越狱条件后会在首页提供入口。执行期间管理器可能被强制停止并重启；如果没有按时恢复，查看日志确认失败原因。

越狱成功后只对当前启动会话有效。普通重启会丢失 Root，需要重新执行；需要保留模块改动时使用软重启。

## 自动越狱

“自动越狱”只在越狱模式下可配置。开机检测到 Permissive SELinux 时，它会尝试通过 Magica 自动提权。该能力依赖系统环境和自启动权限，不能替代可持久化的启动镜像安装。

::: danger
不要为了越狱关闭 SELinux 保护而长期不恢复。只在你理解设备风险并具备恢复手段时使用。
:::
