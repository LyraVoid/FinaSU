---
title: 越狱模式安装
description: 不修改启动分区、在当前系统会话内加载 FinaSU 驱动的完整说明
createTime: 2026/10/24 00:00:00
permalink: /guide/install/jailbreak/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 越狱模式安装

<Badge text="会话级" type="warning" /> <Badge text="不修改启动分区" type="tip" />

越狱模式（late load）在当前系统启动后加载 FinaSU 驱动，不修改 `boot` 或 `init_boot`。它适合无法或不想修改启动分区的设备，但 Root 只对当前启动会话有效，普通重启后会消失。

::: tip 已经有 Root？

如果设备当前已经由 Magisk、KernelSU 或其分支、APatch/KernelPatch 提供 Root，先看对应的切换文章，不要直接叠加固定越狱工具：

- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)

:::

::: info 本页的范围

本页说明越狱模式的安装分类、入口、生命周期和排错。更完整的越狱专题会另外编写；固定工具和 CVE 的详细适配也可以参考[从 KernelSU 切换](/guide/switch/kernelsu/)中的路径四。

:::

## 越狱是怎么工作的

```mermaid
flowchart LR
    A["已有提权入口"] --> B["取得 uid 0 / 绕过 seccomp"]
    B --> C["执行 FinaSU ksud late-load"]
    C --> D["加载 kernelsu.ko"]
    D --> E["建立 SELinux 域与授权模型"]
    E --> F["管理器识别 Jailbreak mode"]
```

越狱不是另一套驱动。它加载的仍然是标准的 `<KMI>_kernelsu.ko`，安装的也是同一份 `ksud`；区别在于驱动在系统启动后才加载，因此没有持久启动镜像补丁。

## 前提

:::: field-group

::: field 提权入口
@type `必需`
@default `CVE 或 Magica`
设备需要有可用的 CVE 提权工具，或满足 FinaSU 首页 Jailbreak 的 Magica/Permissive SELinux 条件。
:::

::: field FinaSU 载荷
@type `必需`
@default `ksud + kernelsu.ko`
确认 APK 内包含对应 ABI 的 `ksud` 和对应 KMI 的 `kernelsu.ko`。
:::

::: field 内核匹配
@type `必需`
@default `uname -r`
CVE 工具通常按 `uname -r` 精确匹配内核；不匹配时不要反复尝试。
:::

::: field 自启动
@type `自动越狱时必需`
@default `允许`
使用 FinaSU 首页的自动越狱时，需要允许管理器自启动。
:::

::: field 救砖方案
@type `建议`
@default `原厂镜像`
越狱本身不写启动分区，但切换 LKM/Built-In 或修复失败时仍需要原厂镜像。
:::

::::

::: danger 越狱不是通用提权漏洞

它依赖设备已经具备的 CVE 或 Magica 提权环境。不要为了越狱长期关闭 SELinux 保护，也不要在不匹配的内核上反复尝试。

:::

## 入口一：FinaSU 首页 Jailbreak

FinaSU 管理器内置了一条兼容越狱路径。它由 `AppZygotePreload` 触发，执行随包的 `ksud late-load --magica 5555 --package-name <当前管理器包名>`，成功后强制停止并重新启动管理器。

::: steps

1. **安装官方管理器**

   确认 APK 内包含对应架构的 `ksud` 和对应 KMI 的 `kernelsu.ko`。备份旧管理器和模块状态。

2. **确认条件**

   设备需要可用的 Magica 提权环境和相应的 SELinux 状态。使用自动越狱时还要允许管理器自启动。

3. **点击首页 Jailbreak**

   流程会临时启用本机 ADB TCP 通道，通过 `adb shell` 执行 late-load，完成后恢复 ADB 属性。

4. **等待管理器重启**

   成功后管理器会被强制停止并重新启动。若约 30 秒内没有恢复，按日志检查失败原因，不要连续点击。

5. **验证**

   首页应显示 **Jailbreak mode** 且 Root 可用。没有进入该状态时，检查 Magica 环境、SELinux 和 ADB 通道。

:::

::: warning 首页按钮是兼容方案

它不要求匹配 CVE 内核 profile，但依赖 Permissive SELinux 或 Magica 环境。条件不满足时改用 CVE 工具，不要为了按钮可用而长期降低系统安全性。

:::

## 入口二：CVE 提权工具

当前更常见的做法是先通过 CVE 漏洞从一个普通未授权应用提权到 uid 0，再执行 FinaSU 的 `ksud late-load`。例如 GhostLock 利用 CVE-2026-43499 在匹配的内核上取得 uid 0 并绕过 seccomp，然后调用：

```sh
ksud late-load --kmi <KMI> --allow-shell
```

公开实现见 [YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app)。它按设备 `uname -r` 精确匹配内核 profile；内核不匹配或漏洞已修复时不能使用。

::: warning GhostLock 默认不认识 FinaSU

GhostLock 查找 `ksud` 的顺序是：

1. 它工作目录中的 `ksud`
2. 已安装的 KernelSU 或分支管理器包内的 `libksud.so`
3. `/data/local/tmp/ksud`
4. `/data/adb/ksu/bin/ksud`

FinaSU 不在 GhostLock 的默认包名列表中。如果设备上还装着旧管理器，GhostLock 可能优先使用旧 `ksud`。要让它加载 FinaSU，可以卸载旧管理器，或直接替换 GhostLock 工作目录中的 `ksud`，或者在确认前两项不存在后把 FinaSU 的 `ksud` 放到 `/data/local/tmp/ksud`。

:::

::: steps

1. **准备 FinaSU 的 ksud**

   安装官方 FinaSU 管理器，或从 APK 中取出对应 ABI 的 `libksud.so`。它本质上就是可执行的 `ksud`，需要时改名为 `ksud`。

2. **推送到设备**

   把 `ksud` 放到 GhostLock 能找到的位置，并确保可执行。

3. **让 GhostLock 使用它**

   移除旧的 KernelSU 或分支管理器，或替换 GhostLock 工作目录中的 `ksud`。只把文件放进 `/data/local/tmp` 时，如果工作目录已经有旧 `ksud`，GhostLock 仍会优先使用旧的。

4. **执行 CVE 流程**

   在 GhostLock 中运行对应内核的 profile。日志应出现 `ksud=...`、`late-load kmi=...` 和 `late-load exit=0`，随后能检测到 `kernelsu` 模块。

5. **验证 FinaSU**

   打开 FinaSU，首页应显示 **Jailbreak mode** 且 Root 可用。没有进入该状态时，先检查 GhostLock 实际使用的 `ksud` 路径和 KMI。

:::

::: code-tabs

@tab 从 APK 取 ksud

```sh
# 把 arm64-v8a 换成设备实际 ABI
unzip -p FinaSU.apk lib/arm64-v8a/libksud.so > ksud
```

@tab 推送到临时目录

```sh
adb push ksud /data/local/tmp/ksud
adb shell chmod 755 /data/local/tmp/ksud
```

:::

::: danger CVE 工具不是通用解

GhostLock 需要与设备内核精确匹配的 profile，不能只凭 Android 版本判断支持。内核已经修复漏洞、profile 不匹配或设备不在支持列表时，不要反复尝试。错误的内核参数可能导致设备重启或无法开机。

:::

## 入口三：动态提权工具

如果提权工具支持选择已安装的管理器，应优先使用这种方式。工具会从所选管理器的原生库目录读取 `libksud.so`（本质上就是 `ksud`），而不是只认写死的 KernelSU 或分支包名。

使用时选择 FinaSU 管理器即可。官方构建的包名通常是 `me.yuki.fina`，PR 构建是 `me.yuki.fina.pr`，自定义构建可能不同；以设备上实际安装的包名为准。工具应当读取这个包内的 `libksud.so`，而不是旧管理器留下的副本。

::: tip 为什么优先用动态工具

这样不需要手工替换 `ksud`，也不会因为工具的固定包名列表而误用旧管理器。只要工具每次运行都重新解析所选管理器，FinaSU 更新 APK 后也会使用新的 `ksud`；如果工具缓存了旧路径，需要重新选择管理器或清除它的缓存。

:::

::: warning 动态选择管理器不等于自动提权

工具仍需对应的 CVE 或内核 profile 才能取得 uid 0。选择 FinaSU 只解决“用哪一份 `ksud`”的问题，不会绕过设备内核的漏洞匹配条件。

:::

## 入口四：固定工具替换载荷

如果设备只能通过某个固定工具触发越狱，可以尝试让它执行 FinaSU 的载荷。这类工具最终通常只是执行数据目录中的某个 `ksud`，再由这个 `ksud` 加载同目录或内嵌的 `kernelsu.ko`；因此==替换工具实际执行的 `ksud`，通常就能让它加载 FinaSU 驱动==。部分工具还会直接加载自己的 `kernelsu.ko`，这时需要同时替换对应的模块和配置。

::: steps

1. **找到工具真正执行的文件**

   不要只凭文件名猜。查看工具的数据目录、日志和启动脚本，确认它执行的是 `/data/adb/ksud`、自带目录中的 `ksud`，还是从 APK 解压出的二进制。先把原文件完整备份。

2. **准备匹配的 FinaSU 载荷**

   从官方 FinaSU APK 的 `lib/<ABI>/libksud.so`（本质上是可执行的 `ksud`）或发布包中的独立 `ksud` 取出对应版本。工具只执行 `ksud` 时，它会从内嵌资源加载匹配 KMI 的模块；工具直接加载模块时，还要准备与该 KMI 匹配的 `<KMI>_kernelsu.ko`。ABI、KMI 或架构不匹配时不要替换。

3. **按原路径替换并保留权限**

   用 FinaSU 的 `ksud` 覆盖工具实际执行的那个文件，保持原文件名、可执行权限和 SELinux 上下文。只替换载荷，不要用工具的状态文件覆盖 `/data/adb/ksu` 中的授权或 Profile。

4. **处理固定包名**

   如果工具还写死了旧管理器的包名或 Activity，优先把启动目标改成 FinaSU；改不了时，FinaSU 驱动仍可能已经加载，但工具会重启旧管理器。此时手动打开 FinaSU，检查首页是否进入 **Jailbreak mode**。

5. **运行并验证**

   重新执行工具的越狱流程，查看日志中实际加载的模块和 `ksud` 版本。只有 FinaSU 首页显示 **Jailbreak mode**，并且 Root 可用，才算切换成功。

6. **保留回退文件**

   保留原 `ksud`、模块和配置的备份。工具更新或重新解压载荷后可能覆盖替换结果，升级后需要重新检查。

:::

::: danger 替换载荷不是官方支持路径

这是针对固定工具的手工适配，不同工具的目录结构、校验和启动参数都可能不同。工具如果校验载荷哈希或签名，替换可能直接失败；不要为了绕过校验而关闭设备上其他安全保护。错误的内核模块可能导致无法开机。

:::

## 生命周期

::: timeline 一次越狱会话

- 系统正常启动，此时没有 FinaSU Root
  time=启动 type=tip

- CVE 或 Magica 取得 uid 0
  time=提权

- `ksud late-load` 加载 FinaSU 驱动
  time=加载

- 管理器重新启动并识别 Jailbreak mode
  time=生效

- 普通重启后 Root 消失，需要重新越狱
  time=结束

:::

::: warning 同一会话不会热替换驱动

越狱流程启动时会先检查系统中是否已经加载 KernelSU。已经加载时，它会跳过模块加载，只重新执行后续安装和启动脚本。因此重复执行越狱不会替换内存中的旧驱动；更新驱动需要完整重启后重新越狱。

:::

::: tip 软重启的用途

软重启会保留已经加载的内核模块，只重新启动 Android 用户态，适合让模块改动生效，但不适合替换驱动。需要更新越狱模式驱动时，必须完整重启后重新越狱。

:::

## 安装后验证

::: steps

1. **确认运行方式**

   首页应显示 **Jailbreak mode**，而不是 LKM 或 Built-In。

2. **确认内核构建**

   检查首页的内核构建版本和 KMI，确认加载的是当前 APK 内嵌的驱动。

3. **测试 Root**

   使用测试应用请求 Root，确认授权弹窗和 `su` 正常。

4. **验证模块**

   先启用一个模块并重启，确认 Folk Mount 和模块脚本正常。不要一次恢复全部旧模块。

5. **验证内置引擎**

   使用 Sui、FolkZygisk 或 NoMount 时，逐个检查状态；注入类功能通常需要一次完整重启。

:::

## 转为持久 Root

::: danger Bootloader 未解锁时不要尝试持久化

部分设备使用越狱模式，正是因为 Bootloader 无法解锁或用户不能解锁。LKM 和 Built-In 都需要修改 `boot` 或 `init_boot`；在锁定 Bootloader 上刷写会破坏 AVB，可能导致设备无法开机、数据丢失或失去官方恢复条件。

转为持久 Root 前，必须确认设备已经解锁 Bootloader、启动分区可以恢复，并且手上有与当前系统完全匹配的原厂镜像。任何一项无法确认，就继续使用越狱模式，不要尝试刷写启动分区。设备异常时按[救砖](/guide/rescue-bootloop/)处理。

:::

::: table 越狱、LKM 与 Built-In

| 项目 | 越狱模式 | LKM | Built-In |
| --- | --- | --- | --- |
| 修改启动分区 | 否 | 是 | 是 |
| 重启后保留 Root | 否 | 是 | 是 |
| 依赖提权漏洞或 Magica | 是 | 否 | 否 |
| 主要用途 | 临时验证、无法刷写的设备 | 大多数用户的永久 Root | 自行维护内核 |

:::

只有确认设备能够修改启动分区后，才按 [LKM 安装](/guide/install/lkm/) 或 [Built-In 安装](/guide/install/built-in/) 转为持久 Root。切换前先确认 Bootloader 状态、分区和原厂镜像恢复方案。

## 数据与文件

```file-tree title="越狱相关位置"
/data/adb
├── ksud                      # FinaSU 用户态守护进程
├── ksu/
│   ├── .allowlist            # 授权状态
│   └── profile/              # App Profile
└── modules/                  # 已安装模块

/data/local/tmp
└── ksud                      # 部分提权工具会从这里读取载荷
```

不要用旧管理器的状态文件覆盖 `/data/adb/ksu` 中的授权或 Profile。只替换工具实际执行的载荷，并在操作前备份原文件。

## 排错

::: collapse accordion

- **首页点击 Jailbreak 后约 30 秒仍未恢复**

  检查 Magica 环境、SELinux 状态、ADB 通道和管理器自启动权限。先看日志，不要连续触发。

- **CVE 工具提示 ksud 缺失**

  工具没有找到 FinaSU 的 `ksud`。把 APK 内的 `libksud.so` 放到它能读取的位置，或改用动态提权工具选择 FinaSU。

- **工具使用了旧管理器的 ksud**

  检查工具工作目录和固定包名列表。移除旧管理器，或替换工具工作目录中的 `ksud`。

- **CVE 流程无法取得 uid 0**

  内核 profile 不匹配或漏洞已被修复。不要在不支持的内核上反复尝试。

- **显示越狱成功但 FinaSU 没有 Root**

  可能仍在操作旧管理器或旧载荷。检查日志中的 `ksud` 路径、`late-load` 退出状态和 KMI。

- **重复越狱后驱动仍是旧版本**

  同一启动会话中，已经加载驱动时越狱流程会跳过模块加载。完整重启后重新越狱。

- **模块没有挂载**

  确认模块支持 FinaSU 的挂载方式，并检查 Folk Mount 提供方。越狱模式不会改变模块兼容性。

- **设备无法开机**

  越狱本身不写启动分区；若之前同时刷写过镜像，按[救砖](/guide/rescue-bootloop/)恢复。不要继续尝试未知载荷。

:::

## 常见问题

::: chat title="越狱模式安装问答"
{用户}
越狱会修改 boot 或 init_boot 吗？

{.}
不会。它在系统启动后加载驱动，不写启动分区；所以重启后 Root 会消失。

{用户}
首页 Jailbreak 和 CVE 工具是什么关系？

{.}
首页按钮是 FinaSU 自带的兼容路径，依赖 Permissive SELinux 或 Magica。CVE 工具则是先利用漏洞取得 uid 0，再执行 FinaSU 的 `ksud late-load`。

{用户}
为什么固定工具会认错管理器？

{.}
部分工具写死了旧管理器的包名、载荷路径或签名。可以替换它实际执行的 `ksud`，或改用能选择管理器的动态提权工具。

{用户}
替换 ksud 后还需要替换 kernelsu.ko 吗？

{.}
如果工具只执行 `ksud`，FinaSU 的 `ksud` 会从内嵌资源加载模块；如果工具直接加载固定的 `kernelsu.ko`，就需要同时替换模块。

{用户}
越狱后可以长期使用吗？

{.}
只在当前会话有效。需要持久 Root 时，按[LKM 安装](/guide/install/lkm/)或[Built-In 安装](/guide/install/built-in/)重新安装。

{用户}
重启后如何恢复 Root？

{.}
重新执行越狱流程。若需要每次开机自动恢复，应改用 LKM 或 Built-In，而不是依赖越狱。
:::

## 相关页面

- [安装与刷写概览](/guide/install/)
- [LKM 安装](/guide/install/lkm/)
- [Built-In 安装](/guide/install/built-in/)
- [从 Magisk 切换](/guide/switch/magisk/)
- [从 KernelSU 切换](/guide/switch/kernelsu/)
- [从 APatch 切换](/guide/switch/apatch/)
- [救砖](/guide/rescue-bootloop/)

*[LKM]: Loadable Kernel Module，可加载内核模块
*[KMI]: Kernel Module Interface，Android 内核模块接口版本
*[GKI]: Generic Kernel Image，Android 通用内核镜像
*[CVE]: 通用漏洞披露编号
*[AVB]: Android Verified Boot，Android 启动验证
