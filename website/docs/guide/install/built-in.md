---
title: Built-In 安装
description: 将 FinaSU 驱动编入内核、刷入完整内核镜像并完成验证
createTime: 2026/10/24 00:00:00
permalink: /guide/install/built-in/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Built-In 安装

<Badge text="进阶" type="warning" /> <Badge text="替换内核" type="danger" />

Built-In 把 FinaSU 驱动直接编入 Linux 内核，而不是放进 ramdisk。它适合需要内置驱动、SUSFS，或本来就在维护自定义内核的用户。安装结果是完整内核镜像，管理器无法通过“直接安装”替你完成。

内置内核可以由内核作者以完整 `boot` 镜像分发，也可以打包成 AnyKernel3 ZIP 在设备端刷入。

::: tip 已经有 Root？

如果设备当前已经由 Magisk、KernelSU 或其分支、APatch/KernelPatch 提供 Root，不要在内核里继续叠加 FinaSU。先看对应的切换文章：

- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)

:::

## Built-In 是怎么工作的

```mermaid
flowchart LR
    A["设备内核源码"] --> B["接入 FinaSU"]
    B --> C["启用 CONFIG_KSU=y"]
    C --> D["编入管理器证书"]
    D --> E["构建完整内核镜像"]
    E --> F["刷入 boot"]
    F --> G["开机直接初始化驱动"]
    G --> H["管理器连接并授权"]
```

Built-In 驱动和内核一起启动，不经过 ramdisk 中的 `kernelsu.ko`。因此它不能像 LKM 那样由管理器单独替换，更新和 OTA 都需要重新处理内核。

## 先确认分区

::: table Built-In 与 LKM 的区别

| 项目 | Built-In | LKM |
| --- | --- | --- |
| 驱动位置 | Linux 内核镜像内部 | ramdisk 中的 `kernelsu.ko` |
| 主要修改分区 | `boot` | 通常是 `init_boot`，没有时是 `boot` |
| 是否替换 Linux 内核 | 是 | 否 |
| 是否能由管理器直接安装 | 否 | 可以，取决于设备条件 |
| 系统 OTA 后 | 重新编译并刷入内核 | 重新修补启动镜像 |

:::

`init_boot` 不是内置驱动的目标；除非你的内核安装包明确要求，否则不要操作它。

::: warning 内核在 boot 里

Built-In 修改的是 Linux 内核，通常位于 `boot`。只刷 `init_boot` 不会替换内置驱动，也不会把 LKM 变成 Built-In。

:::

## 前提

:::: field-group

::: field 内核源码
@type `必需`
@default `与设备匹配`
准备与设备型号、Android 版本和内核版本匹配的源码或内核树。不能用相邻版本的内核源码代替。
:::

::: field 构建环境
@type `必需`
@default `匹配工具链`
使用设备内核维护者的编译器、DDK 或 CI 环境。不同编译器、配置和内核树可能产生不兼容的产物。
:::

::: field 管理器证书
@type `必需`
@default `匹配 APK`
内核必须编入与目标 FinaSU 管理器匹配的证书大小和 SHA-256。签名不一致时，管理器无法获得管理器身份。
:::

::: field 救砖镜像
@type `必需`
@default `原厂 boot`
准备与当前系统完全匹配的原厂 `boot`，并阅读[救砖](/guide/rescue-bootloop/)。
:::

::::

::: danger 不要在内核上叠加旧 Root

开始前移除 Magisk、KernelSU 或其分支、APatch/KernelPatch 等旧 Root 的内核修改。两套 hook、supercall 或内核补丁同时存在时，可能直接导致无法开机。

:::

## 清理旧集成

在目标内核源码中先移除旧的 KernelSU 系集成：

- 关闭旧的 `CONFIG_KSU` 或对应驱动选项。
- 移除旧的 hook、supercall、内核命令行参数和补丁。
- 确认没有旧驱动残留的符号或初始化入口。

不要在同一份内核里同时启用两套 KernelSU 驱动。

::: tip SUSFS

SUSFS 的内核实现不在 FinaSU 仓库中。只应用与目标内核版本匹配的 SUSFS 补丁，并开启 `CONFIG_KSU_SUSFS`。当前 SUSFS 集成通常要求 `CONFIG_KSU=y`，并可能需要 `CONFIG_THREAD_INFO_IN_TASK=y`。

:::

## 接入并构建

在目标内核源码根目录执行接入脚本，再按设备维护者的方式构建。完整选项和证书要求见[内核集成](/developer/builtin-integration/)。

::: code-tabs

@tab 接入 FinaSU

```sh
./kernel/setup.sh --dir /path/to/FinaSU
scripts/config --enable KSU
scripts/config --enable KPROBES
scripts/config --enable EXT4_FS
scripts/config --enable HEADERS_INSTALL
```

@tab 构建内核

```sh
make ... KSU_REQUIRE_PREINIT=1
```

:::

::: steps

1. **确认源码版本**

   使用与设备系统和内核版本匹配的源码。跨版本复用编译产物可能导致无法启动。

2. **执行接入脚本**

   运行 `kernel/setup.sh`，确认 `CONFIG_KSU=y`、KPROBES、EXT4_FS 和 HEADERS_INSTALL 等选项已启用。

3. **构建并检查日志**

   显式传入 `KSU_REQUIRE_PREINIT=1`。检查日志中的版本、KMI 和证书信息。

4. **准备原厂镜像**

   保留与当前系统匹配的原厂 `boot`，确认 A/B 槽位和当前启动分区。

5. **刷入完整内核**

   把构建出的完整内核镜像刷入 `boot`。不要把它当作 LKM 放进 ramdisk。

:::

::: timeline Built-In 构建与安装

- 准备匹配的内核源码和工具链
  time=准备 type=tip

- 清理旧 Root 集成，接入 FinaSU
  time=接入

- 编入证书并构建完整内核
  time=构建

- 刷入 boot 并重启
  time=生效

- 验证 Built-In、Root 和模块
  time=收尾 type=success

:::

## 刷入完整内核

::: code-tabs

@tab 非 A/B 设备

```sh
fastboot flash boot boot.img
```

@tab A/B 设备

```sh
fastboot getvar current-slot

# 当前槽位是 a 时；如果是 b，把 _a 换成 _b
fastboot flash boot_a boot.img
```

:::

::: danger 不要刷错分区

把 LKM 修补流程产生的 `init_boot` 当成本文的内核镜像，或把完整内核刷进 `init_boot`，都可能导致无法开机。Built-In 的目标通常是 `boot`。

:::

## AnyKernel3 是什么

AnyKernel3 是一套通用的设备端启动镜像安装格式，不是内核，也不是 Root 方案本身。它通常用于分发内置内核：内核作者把编译好的内核、补丁或相关文件放进 ZIP，再由包内的 `anykernel.sh` 和 `update-binary` 完成解包、替换、重打包和刷写。

::: steps

1. **安装器读取当前启动镜像**

   AnyKernel3 在设备上读取当前槽位的启动镜像，而不是自带一份完整的分区备份。

2. **替换 ZIP 中的内容**

   包内脚本根据作者提供的文件替换内核、ramdisk 或其他启动内容。对于 Built-In，ZIP 里应当已经包含编入 FinaSU 驱动的内核。

3. **重新打包并写入分区**

   安装器重新生成启动镜像，并由 `anykernel.sh` 决定写入 `boot` 或作者指定的分区和槽位。

4. **重启验证**

   刷入完成后重启，首页应显示 **Built-In**。如果仍然显示 LKM 或未安装，检查 ZIP 是否真的包含 FinaSU 内核，而不是普通 LKM 包。

:::

::: warning FinaSU 只检查 ZIP，不保证内核兼容

FinaSU 管理器会以 Root 权限执行 AnyKernel3 的安装脚本，但只做 ZIP 格式和结构检查。它不会覆盖包内的槽位选择，也不会在刷入后自动修正分区或更新 `ksud`。脚本成功退出只能说明安装器没有报错，不能证明设备一定能启动。

:::

::: danger

AnyKernel3 包里的脚本以 Root 权限运行，且可能在失败前已经写入分区。只使用明确支持当前设备、系统构建和内核版本的包。刷入前准备完整原厂 `boot` 并阅读[救砖](/guide/rescue-bootloop/)；如果安装后无法启动，不要继续刷入第二个未知 ZIP。

:::

## 安装匹配的管理器

内置驱动会校验管理器签名。安装与内核构建时使用的证书匹配的 FinaSU APK；签名不一致时，管理器可能无法获得管理器身份或 Root 权限。

重启后确认：

- 首页显示 **Built-In**。
- 管理器能读取版本和授权应用。
- 测试应用可以正常获得 Root。
- 模块和内置引擎按预期工作。

::: warning 签名必须匹配

自定义签名构建必须在编译内核时同步更新管理器证书的大小与 SHA-256。不要在未确认签名关系的情况下混用不同来源的 APK 和内核。

:::

## 系统 OTA

系统 OTA 会替换内核。OTA 后必须重新构建并刷入匹配新系统的 FinaSU 内核，不能继续使用旧的内核镜像。A/B 设备在 OTA 尚未重启时，确认新系统对应的内核构建已经准备好，再决定刷写方式。

::: danger 内核版本不匹配可能导致无法开机

不要为了省事把旧内核刷到新系统。内核、vendor 模块和系统版本不匹配时，可能在启动阶段直接失败。

:::

## 数据与模块

```file-tree title="Built-In 相关位置"
/data/adb
├── ksud                      # 用户态命令行工具
├── ksu/
│   ├── .allowlist            # 授权状态
│   ├── .dynamic_manager      # 管理器登记
│   └── profile/              # App Profile
└── modules/                  # 已安装模块
```

系统 OTA 通常不会删除 `/data/adb`，但旧模块仍可能因为系统或内核变化而失效。切换后先停用旧模块，再逐个启用兼容内容。

## 排错

::: collapse accordion

- **刷入后无法开机**

  停止反复重启，按[救砖](/guide/rescue-bootloop/)恢复原厂 `boot`。优先检查内核版本、分区、槽位和构建配置。

- **首页仍显示 LKM 或未安装**

  确认刷入的是完整内核，而不是 LKM 修补后的 `init_boot`；检查内核是否真的包含 FinaSU 驱动。

- **管理器能打开但无法授权**

  检查内核编入的管理器证书大小与 SHA-256 是否与当前 APK 匹配。

- **提示需要更新管理器或内核**

  这是 UAPI 不匹配。安装匹配版本的管理器，或重新构建对应内核；见[更新 FinaSU](/guide/update/)。

- **缺少 preinit 或 UAPI 头文件**

  确认构建时显式传入 `KSU_REQUIRE_PREINIT=1`，并检查 `HEADERS_INSTALL` 等选项。

- **模块无法挂载**

  检查模块是否支持 FinaSU 的挂载方式，以及内核配置是否满足模块要求。

- **AnyKernel3 刷入后无法启动**

  该 ZIP 可能不支持当前设备或内核版本，或其中并不是 FinaSU 内置内核。恢复原厂 `boot`，不要继续叠加其他未知安装包。

- **OTA 后无法开机**

  不要继续使用旧内核。恢复原厂 `boot`，重新构建匹配新系统的 FinaSU 内核。

- **SUSFS 状态异常**

  确认内核已应用匹配版本的 SUSFS 补丁并启用 `CONFIG_KSU_SUSFS`；FinaSU 只配置已有的内核实现。

:::

## 常见问题

::: chat title="Built-In 安装问答"
{用户}
Built-In 和 LKM 哪个更好？

{.}
大多数用户选 LKM。Built-In 适合需要内置驱动、SUSFS 或自行维护内核的用户，但构建、升级和 OTA 都更复杂。

{用户}
可以直接用管理器安装 Built-In 吗？

{.}
不可以。管理器只能刷写启动镜像或修补 LKM；Built-In 必须先把驱动编入内核并刷入完整内核。

{用户}
为什么首页显示 Built-In？

{.}
内核不是按可加载模块构建的，因此不会设置 LKM 标志位。Built-In 表示驱动已经编入内核，不是另一套独立功能。

{用户}
内核和管理器签名必须一致吗？

{.}
必须匹配。内核会校验管理器证书；不一致时，管理器可能无法获得管理器身份或 Root。

{用户}
OTA 后还要重新编译吗？

{.}
通常要。OTA 会替换内核，需要重新构建并刷入匹配新系统的 FinaSU 内核。

{用户}
AnyKernel3 是 LKM 吗？

{.}
不是。AnyKernel3 只是设备端刷入启动镜像的打包格式，通常用于分发内置内核；LKM 日常安装使用文件修补或直接安装。

{用户}
已经有 Magisk/KernelSU/APatch 怎么办？

{.}
先看对应的切换文章，移除旧内核补丁后再构建 FinaSU；不要直接叠加。
:::

## 相关页面

- [安装与刷写概览](/guide/install/)
- [LKM 安装](/guide/install/lkm/)
- [越狱模式安装](/guide/install/jailbreak/)
- [内核集成](/developer/builtin-integration/)
- [更新 FinaSU](/guide/update/)
- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)
- [救砖](/guide/rescue-bootloop/)

*[LKM]: Loadable Kernel Module，可加载内核模块
*[KMI]: Kernel Module Interface，Android 内核模块接口版本
*[GKI]: Generic Kernel Image，Android 通用内核镜像
*[OTA]: Over-The-Air，系统在线更新
*[UAPI]: 内核与用户态之间的稳定接口
*[AVB]: Android Verified Boot，Android 启动验证
