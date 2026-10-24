---
title: 常见问题
description: FinaSU 的安装、Root、模块、更新与排错常见问题
createTime: 2026/10/24 00:00:00
permalink: /guide/faq/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 常见问题

<Badge text="先查这里" type="tip" /> <Badge text="不要猜分区" type="danger" />

本页按使用阶段整理常见问题。涉及具体操作时，先看对应的安装或切换文章，不要只凭问答中的一句话直接刷机。

::: tip 已经有 Root？

如果设备当前已经由 Magisk、KernelSU 或其分支、APatch/KernelPatch 提供 Root，先看对应的切换文章：

- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)

:::

## 快速定位

::: table 按问题查找

| 阶段 | 常见问题 | 先看 |
| --- | --- | --- |
| 了解 FinaSU | 它是什么、和 KernelSU/Magisk 的关系 | [基础问题](#基础问题) |
| 安装前 | 设备支持、Bootloader、选哪种方案 | [安装与启动](#安装与启动) |
| 安装后 | 无法开机、Root 不生效、授权弹窗 | [Root 与权限](#root-与权限) |
| 模块 | 模块不生效、开机循环、Zygisk/Sui/SUSFS | [模块与引擎](#模块与引擎) |
| 维护 | OTA、更新驱动、卸载 | [更新与卸载](#更新与卸载) |
| 反馈 | 日志、版本、复现步骤 | [反馈问题](#反馈问题) |

:::

## 基础问题

### FinaSU 是什么？

FinaSU 是 KernelSU 系的下游分支，核心 Root 裁决发生在内核中。它提供内核级 `su` 与白名单、Folk Mount、App Profile、FolkZygisk、Sui、SUSFS 能力接口、NoMount、`.fpt` 主题和模块仓库。

::: important 它不只是管理器

管理器 APK 只负责界面、授权和载荷发布；真正提供 Root 的是内核驱动。只安装 APK 不会自动获得 Root。

:::

### FinaSU、KernelSU 和 Magisk 是什么关系？

FinaSU 与 KernelSU 系方案一样，由内核负责最终授权；Magisk 则通过修补启动镜像并在用户态运行守护进程来提供 Root。三者的管理器、模块挂载和启动链修改不能随意混用。

::: warning 不要同时保留两套 Root

FinaSU、Magisk、KernelSU 或其分支、APatch/KernelPatch 同时接管启动链时，可能出现授权异常、模块冲突或无法开机。切换前先移除旧方案。

:::

### FinaSU 支持哪些设备？

官方预构建 LKM 当前覆盖以下 KMI：

::: table 官方 LKM 的 KMI 范围

| Android 基线 | 内核版本 |
| --- | --- |
| Android 12 | 5.10 |
| Android 13 | 5.10、5.15 |
| Android 14 | 5.15、6.1 |
| Android 15 | 6.6 |
| Android 16 | 6.12 |
| Android 17 | 6.18 |

:::

还要同时满足架构和启动方式：

- 官方预构建驱动支持 `arm64-v8a` 和 `x86_64`。
- riscv64 目前没有 LKM 驱动资产，Sui 也没有 riscv64 载荷。
- 设备实际 KMI 必须与支持列表完全一致，不能只看 Android 版本或内核版本。
- 预构建列表之外的内核通常需要源码集成或移植，不能拿名称相近的 `.ko` 强行安装。

### 必须解锁 Bootloader 吗？

**LKM 和 Built-In 需要解锁 Bootloader**，因为它们要修改 `boot` 或 `init_boot`。

越狱模式不修改启动分区，因此可以在锁定 Bootloader 的设备上使用，但前提是设备具备可用的 CVE 或 Magica 提权入口。锁定设备不要为了“永久化”去刷启动分区，否则可能破坏 AVB、导致无法开机。

## 安装与启动

### 应该选 LKM、Built-In 还是越狱？

::: table 三种安装方案

| 方案 | 是否修改启动分区 | 重启后保留 Root | 适合谁 |
| --- | --- | --- | --- |
| [LKM](/guide/install/lkm/) | 是，通常是 `init_boot` 或 `boot` | 是 | 大多数用户 |
| [Built-In](/guide/install/built-in/) | 是，通常是 `boot` | 是 | 自行编译内核的用户 |
| [越狱模式](/guide/install/jailbreak/) | 否 | 否 | 无法或不想修改启动分区的设备 |

:::

不确定时先看[安装与刷写](/guide/install/)的分类菜单。不要同时安装两种方案。

### 安装会修改哪个分区？

- **LKM**：改 ramdisk。设备有 `init_boot` 且 KMI 不是 `android12-*` 时通常是 `init_boot`；否则是 `boot`。
- **Built-In**：替换 Linux 内核，通常是 `boot`；`init_boot` 不是内置驱动的目标。
- **越狱**：不写启动分区。

最终以管理器显示的分区为准。`boot` 和 `init_boot` 不能交叉刷写，A/B 设备还要确认当前槽位。

### 安装后无法开机怎么办？

立即停止反复重启，按[救砖](/guide/rescue-bootloop/)恢复安装时实际修改的分区。优先检查：

- 分区是否正确（`boot` / `init_boot` / `vendor_boot`）。
- KMI 是否匹配。
- A/B 槽位是否写错。
- 是否同时存在两套 Root。
- 镜像是否来自当前设备和系统版本。

::: danger 不要继续猜

连续刷入不同的启动镜像会扩大故障范围。没有匹配的原厂镜像时，先找回完整固件包，不要使用来源不明的“通用救砖镜像”。

:::

### 可以同时保留旧 Root 再安装 FinaSU 吗？

不建议。LKM、Built-In 和旧 Root 可能同时修改启动链或内核入口。已有 Root 的设备应先按切换文章还原旧驱动，再按目标方案安装。

唯一的过渡例外是：旧 LKM 仍能提供可用的 Root shell，且目标分区与旧 LKM 一致时，可以用 FinaSU 的“直接安装”替换；完成后尽快重启并移除旧管理器。具体条件见[从 KernelSU 切换](/guide/switch/kernelsu/)。

## Root 与权限

### 已经授予 Root，应用仍提示没有 Root

先确认问题出在驱动还是应用检测：

1. 用终端应用执行 `su -c id`，确认是否返回 `uid=0`。
2. 检查该应用的 App Profile，尤其是卸载模块策略和 SELinux 域。
3. 强制停止并重新打开应用，确认它重新发起检测。
4. 检查管理器与内核的 UAPI 是否匹配。

如果 `su -c id` 正常，但应用仍报错，通常是应用自己的检测或 Profile 配置问题。

### 为什么没有出现 Root 授权弹窗？

- 应用可能已经在白名单中，被默认允许。
- 应用可能已经被拒绝，记录在“超级用户”页面。
- 默认策略可能已设置为允许或拒绝。
- 应用可能缓存了上一次的检测结果。

先打开管理器的超级用户页面查看记录，再调整该应用的授权。不要通过反复清除数据来碰运气。

### 重新安装管理器后为什么认证失败？

内核按 APK 签名识别管理器。debug、自行编译或使用其他密钥签名的构建不会自动被信任。优先安装官方构建；如果确实需要其他签名，按[动态管理器](/manager/dynamic-manager/)的流程登记，并理解这会扩大内核信任范围。

::: danger 不要随便开启“允许任意应用作为动态管理器”

该实验选项会显著扩大可被信任的管理器范围。只对自己构建并校验过的 APK 使用。

:::

### 怎么隐藏 Root？

没有对所有检测都有效的单一开关。可以按应用组合使用：

- App Profile 的模块卸载策略。
- NoMount 的路径重定向。
- 内核提供的 SELinux 隐藏与 AVC 日志处理。
- 只对必要应用配置规则，避免全局关闭保护。

先确认目标应用实际检测什么，再逐项调整；不要同时开启所有隐藏功能。

## 模块与引擎

### 安装了模块却没有生效

检查：

- 模块是否已启用。
- Folk Mount 是否被设为“关闭挂载”。
- 模块是否需要重启后才生效。
- 模块依赖的 Zygisk、Sui 或配置应用是否已经就绪。
- 模块是否支持 FinaSU 的挂载方式。

### 安装模块后无法正常开机

设备开始启动、屏幕刚亮起时，快速执行 3 次“按下音量减键后松开”，尝试进入 FinaSU 安全模式。

**仍然失败时**，恢复原厂启动分区，并把启动阶段日志交给模块作者。若故障来自错误的 LKM 或 KMI，音量键安全模式可能根本没有机会运行。

### 什么是元模块，我需要安装吗？

元模块接管普通模块的挂载或安装流程，同时只能启用一个。FinaSU 内置的 Folk Mount 已能处理普通模块；只有需要特定挂载实现时才安装元模块。

### Folk Mount 和 NoMount 有什么区别？

Folk Mount 是选择挂载提供者并注入模块的引擎；NoMount 通过内核 VFS 路径重定向注入文件，不建立传统挂载。NoMount 随 `ksud` 发布并按 KMI 匹配。

### FolkZygisk 是什么？还需要安装其他 Zygisk 提供者吗？

FolkZygisk 是内置 Zygisk 引擎。启用并重启即可运行 Zygisk 模块；检测到外部提供者时，内置引擎会让位。FinaSU 只保留一个活动的 Zygisk 实现。

### FinaSU 支持 Xposed 吗？

支持。在可用的 Zygisk 提供者上安装 LSPosed 等现代 Xposed 衍生框架即可。内置 FolkZygisk 与外部提供者只能保留一个。

### 什么是 Sui？还需要 Shizuku 吗？

Sui 是内置的 Shizuku API 后端，兼容应用不需要另外安装 Shizuku。应用权限、默认策略、隐藏规则和诊断由 FinaSU 管理器管理；Sui 依赖 Zygisk 后端。

### 为什么找不到 SUSFS 设置？

SUSFS 是内核侧能力。FinaSU 可以对接源码集成版或已加载的 SUSFS-LKM；当前内核未报告支持时，相关开关不可用。FinaSU 自身不提供 SUSFS 实现，也不会加载 LKM。

## 更新与卸载

### 系统更新之后 Root 消失了

OTA 会替换启动镜像。A/B 设备应在 OTA 尚未重启时使用“安装到未使用的槽位”；非 A/B 设备需要先恢复原厂镜像完成 OTA，再重新修补当前系统的启动镜像。`/data` 中的模块和配置通常仍在。

### 更新管理器后驱动还是旧版本

APK 更新不会自动替换已经刷入的 LKM 或 Built-In 驱动。按[更新 FinaSU](/guide/update/)对应方案重新修补或刷入；越狱模式需要完整重启后重新越狱。

### 如何卸载 FinaSU？

LKM 模式下，设置首页的“卸载”提供永久卸载和恢复原厂镜像。前者进入卸载刷写流程，后者刷回匹配的原厂启动分区，两者不是同一个操作。

::: steps

1. 备份启动镜像和需要保留的模块。
2. 在设置首页选择“卸载”。
3. 选择永久卸载，或选择恢复原厂镜像并准备匹配的镜像。
4. 重启后确认 Root 已移除，再清理不再需要的目录。

:::

不要删除整个 `/data/adb`；其中还有模块、授权和 FinaSU 状态。

## 反馈问题

### 反馈问题时应提供什么？

::: table 反馈信息

| 信息 | 示例 |
| --- | --- |
| 设备与系统 | 型号、Android 版本、系统构建 |
| 内核 | 内核版本、完整 KMI |
| FinaSU | 管理器版本、内核构建版本、运行方式 |
| 安装 | LKM / Built-In / 越狱，实际修改的分区 |
| 模块 | 相关模块名称和版本 |
| 复现 | 操作步骤、预期结果、实际结果 |
| 日志 | 管理器导出的日志和启动阶段日志 |

:::

只发一张截图通常不足以定位问题。涉及启动失败时，请说明是否还能进入 Fastboot、Recovery 或安全模式。

## 一句话问答

::: chat title="FinaSU 快速问答"
{用户}
只安装管理器 APK 就能 Root 吗？

{.}
不能。还需要对应的内核驱动、启动镜像或越狱载荷。

{用户}
LKM 和 Built-In 哪个适合我？

{.}
大多数用户选 LKM；需要内置驱动、完整源码集成 SUSFS 或自行维护内核时选 Built-In。仅需要 SUSFS 时也可以评估功能较少的 SUSFS-LKM。

{用户}
越狱重启后还在吗？

{.}
不在。越狱只在当前启动会话有效，重启后需要重新执行。

{用户}
安装模块后开不了机怎么办？

{.}
启动早期连续按三次音量减键尝试安全模式；无效时恢复原厂启动分区，不要继续刷未知镜像。

{用户}
可以从 Magisk/KernelSU/APatch 直接切过来吗？

{.}
先看对应的切换文章。LKM 有时可以借旧 Root 直接安装，内置驱动必须先清理旧内核补丁。

{用户}
更新 APK 后为什么 Root 版本没变？

{.}
APK 只更新用户态；驱动需要按 LKM、Built-In 或越狱模式分别更新。
:::

## 相关页面

- [安装与刷写](/guide/install/)
- [LKM 安装](/guide/install/lkm/)
- [Built-In 安装](/guide/install/built-in/)
- [越狱模式安装](/guide/install/jailbreak/)
- [更新 FinaSU](/guide/update/)
- [救砖](/guide/rescue-bootloop/)
- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)

*[LKM]: Loadable Kernel Module，可加载内核模块
*[KMI]: Kernel Module Interface，Android 内核模块接口版本
*[GKI]: Generic Kernel Image，Android 通用内核镜像
*[OTA]: Over-The-Air，系统在线更新
*[UAPI]: 内核与用户态之间的稳定接口
*[AVB]: Android Verified Boot，Android 启动验证
*[SUSFS]: 建立在内核侧的隐藏与路径伪装能力
