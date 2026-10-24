---
title: LKM 安装
description: 使用可加载内核模块安装 FinaSU 的完整流程、分区选择与排错
createTime: 2026/10/24 00:00:00
permalink: /guide/install/lkm/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# LKM 安装

<Badge text="推荐" type="tip" /> <Badge text="修改 ramdisk" type="warning" />

LKM 不替换 Linux 内核。修补流程会解包启动镜像，把 `ksuinit` 和与设备 KMI 匹配的 `kernelsu.ko` 放入 ramdisk，再由 `ksuinit` 在启动早期加载驱动。它适合大多数用户，也是从其他 Root 方案迁移时最简单的永久方案。

::: tip 已经有 Root？

如果设备当前已经由 Magisk、KernelSU 或其分支、APatch/KernelPatch 提供 Root，不要直接在旧启动镜像上叠加 FinaSU。先看对应的切换文章：

- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)

:::

## LKM 是怎么工作的

```mermaid
flowchart LR
    A["原厂启动镜像"] --> B["修补流程解包 ramdisk"]
    B --> C["写入 ksuinit 与 kernelsu.ko"]
    C --> D["刷入修补后的 boot 或 init_boot"]
    D --> E["开机时 ksuinit 加载驱动"]
    E --> F["内核启动 ksud"]
    F --> G["管理器连接并授权"]
```

这条链路里，Linux 内核没有变化。FinaSU 只修改 ramdisk，让设备在启动早期多加载一个模块。因此 LKM 的风险主要集中在启动镜像的分区、KMI 和槽位是否匹配。

## 先确认分区

::: table LKM 的目标分区

| 设备情况 | 默认修改 | 回原厂时刷回 |
| --- | --- | --- |
| 有 `init_boot`，且 KMI 不是 `android12-*` | `init_boot` | 原厂 `init_boot` |
| 没有 `init_boot`，或 KMI 是 `android12-*` | `boot` | 原厂 `boot` |
| 手动选择 `vendor_boot` | `vendor_boot` | 原厂 `vendor_boot` |

:::

一句话记法：==LKM 改 ramdisk，通常刷 init_boot，没有 init_boot 时才刷 boot==。`android12-*` 内核是例外，即使存在 `init_boot`，默认仍选择 `boot`。

::: important 最终以管理器显示为准

不同设备的启动布局并不相同。安装页会显示它识别到的默认分区；不要只凭 Android 版本或网上教程猜。分区选错会把镜像写进错误位置，直接导致无法开机。

:::

::: warning 不要交叉刷写

`boot.img` 不能刷进 `init_boot`，`init_boot.img` 也不能刷进 `boot`。A/B 设备还要确认当前槽位；只处理当前槽位，保留另一个能启动的槽位。

:::

## 安装前准备

:::: field-group

::: field 原厂镜像
@type `必需`
@default `boot / init_boot`
准备与当前 Android 版本、内核版本和槽位完全匹配的原厂镜像。没有原厂镜像时不要开始。
:::

::: field Bootloader
@type `必需`
@default `已解锁`
确认电脑能执行 `fastboot devices`。不要使用来源不明的刷机工具。
:::

::: field KMI
@type `必需`
@default `自动识别`
管理器会根据设备识别 KMI。手动指定时，必须与当前内核完全匹配，不能拿相邻版本代替。
:::

::: field 旧 Root
@type `必须移除`
@default `无`
Magisk、KernelSU 或其分支、APatch/KernelPatch 不能与 FinaSU 同时接管启动链。
:::

::: field 救砖方案
@type `必需`
@default `已准备`
阅读[救砖](/guide/rescue-bootloop/)，确认知道如何恢复安装时实际修改的分区。
:::

::::

## 选择安装方式

::: table LKM 的安装方式

| 方式 | 适合场景 | 是否需要已有 Root |
| --- | --- | --- |
| [选择文件并修补](#选择文件并修补) | 首次安装；手动处理下载好的启动镜像 | 否 |
| [下载文件并修补](#下载文件并修补) | 有完整 OTA 包或 factory 镜像的下载地址 | 否 |
| [直接安装](#直接安装) | 当前设备已能提供 Root，直接修补并刷写当前槽位 | 是 |
| [安装到未使用的槽位](#安装到未使用的槽位) | A/B 设备刚完成 OTA，但还没有重启 | 是 |

:::

普通用户优先使用“选择文件并修补”或“下载文件并修补”。已有 Root 且设备为 GKI 时，可以用“直接安装”升级。

## 选择文件并修补

用于首次安装，或手动处理从完整固件包中提取的启动镜像。

::: steps

1. **提取原厂镜像**

   从与当前系统完全匹配的固件中提取 `boot.img`、`init_boot.img` 或管理器支持的启动镜像。不要使用其他机型或其他系统版本的文件。

2. **选择修补入口**

   在 FinaSU 中进入 **安装与刷写 → 选择文件并修补**，选择这个镜像。

3. **确认分区**

   保持管理器默认选中的分区。只有在明确知道本机分区布局时才手动切换；LKM 的目标通常是 `init_boot`，没有 `init_boot` 时是 `boot`。

4. **选择驱动**

   优先使用管理器内置的 LKM。只有必须使用自定义驱动时才选择本地 `.ko`，并确保架构、KMI 和内核配置完全匹配。

5. **修补并导出**

   修补完成后，把输出镜像复制到电脑。用 `fastboot` 或设备对应的刷写方式写入**同一个分区和当前槽位**。

:::

::: code-tabs

@tab:active boot

```sh
fastboot flash boot boot_patched.img
```

@tab init_boot

```sh
fastboot flash init_boot init_boot_patched.img
```

:::

上面的文件名只是示例。A/B 设备先确认当前槽位：

```sh
fastboot getvar current-slot
```

如果当前槽位是 `a`，通常应使用 `boot_a` 或 `init_boot_a`；如果 Fastboot 已经替你处理槽位，就沿用管理器提示的分区名。不要使用 `--slot all` 同时写两个槽位。

::: danger

刷写后无法开机时，立即停止反复重启，按[救砖](/guide/rescue-bootloop/)恢复安装前实际修改的分区。继续刷入猜测的镜像会扩大故障范围。

:::

## 下载文件并修补

输入完整 OTA 包或 factory 镜像的下载地址。管理器会检查包内有哪些可用的启动分区，并尝试从内核部分识别 KMI，然后执行与“选择文件并修补”相同的流程。

下载流程不会让镜像自动适配你的设备。仍要确认设备型号、系统版本、分区和 A/B 槽位一致；否则修补成功也可能无法启动。

::: warning 下载源必须可信

只使用官方固件或可信的完整 OTA 来源。镜像下载完成后先核对版本和校验信息，再交给管理器修补。

:::

## 直接安装

设备已经通过其他方式拥有 Root 时，可以直接修补并刷写当前设备的当前槽位。这是升级 FinaSU 最方便的方式，但仍会实际覆盖启动分区。

直接安装前建议开启“刷写前备份”，同时保留外部原厂镜像。管理器备份是便利措施，不是完整救砖方案。

::: warning 直接从其他管理器安装前先确认分区

如果旧管理器的 LKM 在 `boot`，FinaSU 的分区也必须选 `boot`；旧 LKM 在 `init_boot`，就选 `init_boot`。不确定时先刷回原厂镜像，再用文件修补，不要盲目叠加。

:::

::: danger Magisk 修补过的镜像不能直接安装

FinaSU 的启动镜像修补会拒绝 Magisk 已修补的 ramdisk。使用 Magisk 的设备先按[从 Magisk 切换](/guide/switch/magisk/)还原原厂镜像。

:::

## 安装到未使用的槽位

该方式只适用于 A/B 设备已经完成 OTA、但尚未重启的时刻。

管理器会把修补结果写入另一个槽位，并在下次重启时尝试切换过去，让新系统与 FinaSU 一起生效。不要在普通重启、系统已经切换完成或无法确认两个槽位版本时使用。

如果新槽位启动失败，可以按[救砖](/guide/rescue-bootloop/)检查当前槽位并切回仍可启动的旧槽位。旧槽位已经被改写、OTA 已合并，或者无法确认两个槽位的系统版本时，不要盲目切换。

::: tip AnyKernel3 通常属于内置模式

LKM 日常安装使用“选择文件并修补”或“直接安装”，不需要 AnyKernel3。如果你拿到的是内核作者提供的 AnyKernel3 ZIP，通常是在安装内置内核；它的作用和风险见 [Built-In 的 AnyKernel3 说明](/guide/install/built-in/#anykernel3-是什么)。

:::

## 使用本地 LKM 文件

修补流程可以使用自己的 `.ko`，但文件名相同不代表 ABI 兼容。驱动必须同时匹配设备架构、KMI 和内核配置；来源不明的内核模块拥有内核权限，不应加载。

没有明确理由时，使用管理器随包提供的 LKM。更换本地 LKM 前先阅读[快速开始](/guide/get-started/)中的支持范围，并准备好对应的启动镜像恢复方案。

::: table 自定义 LKM 必须匹配

| 项目 | 要求 |
| --- | --- |
| 架构 | 与设备 ABI 一致，例如 arm64 |
| KMI | 与当前内核的 KMI 完全一致 |
| 内核配置 | 与目标内核的符号、版本和配置兼容 |
| 签名信任 | 管理器与内核证书关系匹配 |

:::

## 刷写选项

::: table 刷写选项

| 选项 | 作用 | 建议 |
| --- | --- | --- |
| 刷写前备份 | 尽力保存管理器支持的启动分区或状态 | 建议开启；但不能替代外部原厂镜像 |
| 备份为原厂镜像 | 把当前文件标记为原厂备份 | 只在文件确实来自官方固件时开启 |
| 允许 Shell | 允许 Shell 用户获得 Root | 普通用户通常不需要 |
| 启用 ADB Root | 强制开启 ADB Root 并关闭认证 | 风险高，除非调试否则关闭 |

:::

## 安装后验证

::: steps

1. **确认运行方式**

   首页应显示 **LKM**，而不是 Built-In 或 Jailbreak mode。

2. **确认管理器身份**

   FinaSU 应能读取版本、授权应用和管理模块。显示未安装时，检查分区、KMI 和签名。

3. **测试 Root**

   使用测试应用请求 Root，确认授权弹窗和 `su` 正常。

4. **验证模块**

   先启用一个模块并重启，确认 Folk Mount 和模块脚本正常。

5. **验证内置引擎**

   使用 Sui、FolkZygisk 或 NoMount 时，逐个检查状态；Zygisk 和 Sui 通常需要重启完成注入。

:::

## 更新与 OTA

LKM 的驱动版本随 APK 内嵌载荷变化。更新管理器后，如果首页提示随包 LKM 有新版本，需要重新修补并刷写启动镜像；只更新 APK 不会替换已加载的驱动。详细流程见[更新 FinaSU](/guide/update/)。

A/B 设备完成 OTA 后、尚未重启时，可选择“安装到未使用的槽位”。非 A/B 设备需要先恢复原厂镜像完成 OTA，再重新修补当前系统的启动镜像。

## 数据与恢复

```file-tree title="LKM 相关数据位置"
/data/adb
├── ksud                      # 用户态命令行工具
├── ksu/
│   ├── .allowlist            # 授权状态
│   ├── .dynamic_manager      # 管理器登记
│   └── profile/              # App Profile
└── modules/                  # 已安装模块
```

恢复时刷回安装时实际修改的分区：

::: code-tabs

@tab 非 A/B 设备

```sh
fastboot flash boot boot.img
fastboot flash init_boot init_boot.img
```

@tab A/B 设备

```sh
fastboot getvar current-slot
fastboot flash boot_a boot.img
fastboot flash init_boot_a init_boot.img
```

:::

只执行与当前部署对应的分区。刷错后按[救砖](/guide/rescue-bootloop/)处理。

## 排错

::: collapse accordion

- **提示 `Cannot work with Magisk patched image`**

  修补输入仍然是 Magisk 修补过的镜像。先还原原厂镜像，再按本文修补。

- **刷写后无法开机**

  停止反复重启，按[救砖](/guide/rescue-bootloop/)恢复安装前实际修改的分区。优先检查分区、槽位、KMI 和镜像版本。

- **设备没有 `init_boot`**

  这是正常的。没有独立 ramdisk 分区时，LKM 使用 `boot` 的 ramdisk。

- **管理器显示未安装**

  检查启动镜像是否刷入当前槽位、KMI 是否匹配，以及管理器签名是否被内核信任。

- **Root 可用但模块没有挂载**

  检查模块是否启用、Folk Mount 的提供方是否正常，以及模块是否依赖其他 Root 方案的专有接口。

- **直接安装不可用**

  该选项要求当前已有 Root 且设备为 GKI。条件不满足时使用“选择文件并修补”。

- **OTA 后 Root 消失**

  系统更新替换了启动镜像。按[更新 FinaSU](/guide/update/)重新修补；A/B 设备注意在 OTA 尚未重启时使用未使用槽位。

:::

## 常见问题

::: chat title="LKM 安装问答"
{用户}
LKM 会替换我的内核吗？

{.}
不会。LKM 只修改启动镜像中的 ramdisk，把 `ksuinit` 和 `kernelsu.ko` 放进去，Linux 内核本身不变。

{用户}
我应该选 boot 还是 init_boot？

{.}
管理器会自动判断。设备有 `init_boot` 且 KMI 不是 `android12-*` 时通常是 `init_boot`；否则是 `boot`。最终以安装页显示为准。

{用户}
可以直接在 Magisk 的镜像上安装吗？

{.}
不可以。FinaSU 会拒绝 Magisk 修补过的 ramdisk。先按[从 Magisk 切换](/guide/switch/magisk/)还原原厂镜像。

{用户}
直接安装需要什么条件？

{.}
需要当前已有 Root 且设备为 GKI。没有 Root 或不是 GKI 时，使用“选择文件并修补”。

{用户}
本地 `.ko` 可以随便换吗？

{.}
不可以。必须匹配架构、KMI、内核配置和签名信任关系。来源不明的模块不要加载。

{用户}
更新管理器后 Root 还是旧版本？

{.}
APK 更新不会自动替换启动镜像中的 LKM。需要按[更新 FinaSU](/guide/update/)重新修补并刷写。
:::

## 相关页面

- [安装与刷写概览](/guide/install/)
- [Built-In 安装](/guide/install/built-in/)
- [越狱模式安装](/guide/install/jailbreak/)
- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)
- [更新 FinaSU](/guide/update/)
- [救砖](/guide/rescue-bootloop/)

*[LKM]: Loadable Kernel Module，可加载内核模块
*[KMI]: Kernel Module Interface，Android 内核模块接口版本
*[GKI]: Generic Kernel Image，Android 通用内核镜像
*[OTA]: Over-The-Air，系统在线更新
*[AVB]: Android Verified Boot，Android 启动验证
