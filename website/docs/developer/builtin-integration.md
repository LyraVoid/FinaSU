---
title: Built-In 内核集成
description: 把 FinaSU 驱动、preinit 与信任证书组合进目标内核镜像
createTime: 2026/10/25 00:00:00
permalink: /developer/builtin-integration/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Built-In 内核集成

<Badge text="CONFIG_KSU=y" type="tip" /> <Badge text="必须匹配签名" type="warning" /> <Badge text="先备份启动镜像" type="danger" />

Built-In 把 FinaSU 驱动直接编入目标内核镜像，而不是作为 `kernelsu.ko` 在启动后加载。它和 LKM 使用同一份驱动源码和管理器接口；==Built-In 还可以在编译期选择 Tracepoint 或 SUSFS Inline syscall 后端==，区别还包括启动镜像位置和故障恢复路径。

:::: table Built-In 与 LKM

| 项目 | Built-In | LKM |
| --- | --- | --- |
| 内核配置 | `CONFIG_KSU=y` | `CONFIG_KSU=m` |
| 驱动位置 | `vmlinux` / 内核镜像 | 独立 `kernelsu.ko` |
| 安装介质 | 更换内核镜像 | 修补 ramdisk，通常涉及 `init_boot` 或 `boot` |
| 内核更新 | 每次内核变化都重新集成 | 仍需匹配新内核/KMI 的 LKM |
| 失败影响 | 可能无法进入系统 | 通常还能通过启动镜像恢复 |

::::

已有 Root 的切换流程不在本文重复。先按[切换到 FinaSU](/guide/switch/kernelsu/)确认旧驱动在哪里，再选择 LKM 或 Built-In。

## 前置条件

Built-In 必须从目标设备实际运行的内核源码构建。仅仅版本号相同还不够；厂商内核配置、符号导出、in-tree 修改和源码分支都可能不同。

需要准备：

- 与设备固件匹配的内核源码和构建配置。
- Linux 内核构建环境、目标交叉编译器与正确的 `ARCH`。
- 可恢复的启动镜像，以及明确的刷入与回退流程。
- 与要安装的管理器 APK 对应的签名证书信息。
- 目标内核的 `defconfig` 或厂商完整构建参数。

Kconfig 的硬依赖是：

:::: table 必需选项

| 选项 | 原因 |
| --- | --- |
| `CONFIG_KSU=y` | 把驱动编入内核 |
| `CONFIG_KPROBES=y` | FinaSU 的硬依赖；Tracepoint 后端和保留的 kprobe 功能需要 |
| `CONFIG_EXT4_FS=y` | 驱动依赖 `ext4_unregister_sysfs` |
| `CONFIG_HEADERS_INSTALL=y` | 安装目标 UAPI 头，用于编译 preinit runner |

::::

`CONFIG_KSU_SUSFS` 还要求 `CONFIG_KSU=y` 与 `CONFIG_THREAD_INFO_IN_TASK=y`。只有计划接入已有 SUSFS 内核实现时才开启；它是 SUSFS 功能开关，不是 syscall 后端开关。

## 选择 Hook 类型

内核在编译期选择 syscall 入口，管理器只能读取结果，不能在运行时切换，也不会自动回退到另一种后端。

:::: table Syscall 后端

| Hook 类型 | 配置 | 依赖 | 入口 |
| --- | --- | --- | --- |
| Tracepoint | `CONFIG_KSU_HOOK_TRACEPOINT=y` | `HAVE_SYSCALL_TRACEPOINTS`；支持 LKM 与 Built-In | `sys_enter` tracepoint、共享 dispatcher 与其余 kprobe |
| SUSFS Inline | `CONFIG_KSU_HOOK_SUSFS_INLINE=y` | `ARM64=y`、`CONFIG_KSU=y`、`CONFIG_KSU_SUSFS=y` | 匹配 SUSFS 内核补丁中的直接 `ksu_handle_*` 调用 |

::::

Tracepoint 是默认后端。SUSFS Inline 只支持 ARM64 Built-In：它不注册通用 syscall tracepoint、共享 dispatcher，以及已被 SUSFS 直接入口替代的 kprobe。仍需要 `sys_call_table` 的独立钩子可以保留表解析，但不会复用通用 dispatcher 安装。

`CONFIG_KSU_SUSFS` 与后端正交：

- `CONFIG_KSU_SUSFS=n`：只能使用 Tracepoint。
- `CONFIG_KSU_SUSFS=y` 且未启用 Inline：Tracepoint + SUSFS 功能。
- `CONFIG_KSU_SUSFS=y` 且启用 `CONFIG_KSU_HOOK_SUSFS_INLINE=y`：SUSFS Inline。

不要同时启用两个 choice 选项。最终 `.config` 必须只包含一种后端。

## 把源码接进内核树

从内核源码根目录运行仓库中的 `kernel/setup.sh`。脚本会识别 `common/drivers` 或 `drivers`，建立 `kernelsu` 符号链接，并在 `Makefile`、`Kconfig` 中加入驱动入口。

```sh
cd /path/to/kernel-source

# 使用当前 FinaSU checkout
/path/to/FinaSU/kernel/setup.sh --dir /path/to/FinaSU

# 或让脚本自己 clone/更新
/path/to/FinaSU/kernel/setup.sh
```

清理脚本对内核树做的接入修改：

```sh
/path/to/FinaSU/kernel/setup.sh --cleanup
```

::: warning 清理不是恢复任意厂商修改

`--cleanup` 只移除脚本建立的符号链接与它自己加入的 Makefile/Kconfig 行。厂商原有修改、其他补丁和源码 clone 是否保留，仍要自己核对。

:::

## 配置与构建

先基于设备原有配置修改，不要拿一份通用 `defconfig` 覆盖厂商配置：

```sh
scripts/config --enable KSU
scripts/config --enable KPROBES
scripts/config --enable KALLSYMS_ALL
scripts/config --enable EXT4_FS
scripts/config --enable HEADERS_INSTALL
```

然后使用该内核原本的构建命令，并附加 FinaSU 参数：

```sh
make ... \
  KSU_REQUIRE_PREINIT=1 \
  KSU_EXPECTED_SIZE=0x<证书长度> \
  KSU_EXPECTED_HASH=<证书 SHA-256>
```

`...` 代表原有架构、设备树、编译器、并行度等参数，必须替换为本机真实构建命令。

:::: steps

1. **确认当前配置**

   在构建日志中检查 `CONFIG_KSU`、`CONFIG_KPROBES`、`CONFIG_EXT4_FS` 和 `CONFIG_HEADERS_INSTALL` 的最终值，而不是只看 `.config`。

2. **确认 UAPI 头**

   保留 `KSU_REQUIRE_PREINIT=1`。缺少目标 UAPI 头时构建会立即失败，而不是静默生成没有 preinit 的驱动。

3. **确认证书槽位**

   查看构建日志打印的 manager signature size/hash。它们必须与随后安装的管理器 APK 签名一致。

4. **生成可刷入镜像**

   使用设备原本的镜像打包方式。FinaSU 不规定特殊刷写器，`AnyKernel3`、`magiskboot` 或厂商工具都只是封装内核镜像。

5. **先在可恢复设备上验证**

   至少验证开机、管理器识别、Root 授权、模块挂载、SELinux 状态与目标 ABI。编译成功不等于可以启动。

:::::

## 管理器签名

内核在运行时检查管理器 APK 签名，不只看包名。构建变量 `KSU_EXPECTED_SIZE` 与 `KSU_EXPECTED_HASH` 对应签名证书 DER 的长度和 SHA-256。

```sh
make ... \
  KSU_EXPECTED_SIZE=0x02cc \
  KSU_EXPECTED_HASH=<sha256>
```

第二信任槽位：

```sh
make ... \
  KSU_EXPECTED_SIZE=0x<主证书长度> \
  KSU_EXPECTED_HASH=<主证书 SHA-256> \
  KSU_EXPECTED_SIZE2=0x<第二证书长度> \
  KSU_EXPECTED_HASH2=<第二证书 SHA-256> \
  KSU_SECOND_SIGNATURE_RELEASE=1
```

如果设置了 `KSU_EXPECTED_SIZE2` 却没有 `KSU_EXPECTED_HASH2`，构建直接失败。正式使用第二槽位时应显式加 `KSU_SECOND_SIGNATURE_RELEASE=1`，否则构建会把第二个槽位标记为测试/PR 信任槽。

::: danger 签名不匹配不是“换个管理器就行”

内核可以正常启动，却不会把当前管理器识别为受信身份。应重新构建匹配的管理器与驱动，或重新签名 APK；不要通过修改包名、伪装版本号或绕过身份检查解决。

:::

## Preinit

Preinit 让部分模块配置和 `initrc` 在系统更早阶段进入启动流程。它依赖构建时安装到目标树的 UAPI 头：

```text
$(objtree)/usr/include
```

`CONFIG_HEADERS_INSTALL=y` 负责生成这些头；`KSU_REQUIRE_PREINIT=1` 把“头缺失”从警告升级为构建失败。

没有 preinit 时，普通模块仍可挂载和运行，但 `initrc/*.rc`、`preinit/` 与部分早期同步不能按预期工作。不要用触摸文件伪造 preinit 状态。

## UAPI 同步边界

Built-In 内核使用的 UAPI 必须同时提供给：

- 内核中的 `kernel/include/uapi/`。
- Manager JNI 使用的仓库根 `uapi/` 镜像头。
- `userspace/ksud/src/ksu_uapi.rs` 与 `ksucalls.rs`。
- Manager JNI 与 Kotlin 调用层。

当前 `KERNEL_SU_UAPI_VERSION` 为 `6`。只改内核结构体而不更新用户态，可能导致请求长度、字段偏移或命令号不一致。`GET_INFO` 通过 `KSU_GET_INFO_FLAG_HOOK_TRACEPOINT`、`KSU_GET_INFO_FLAG_HOOK_SUSFS_INLINE` 和 `KSU_GET_INFO_FLAG_HOOK_READY` 报告后端及注册状态。详细约束见 [SuperCall 与 UAPI](/developer/supercall/)。

## 版本字符串

内核构建会尝试从 FinaSU checkout 的 Git 信息生成完整版本字符串，并通过 UAPI 提供给管理器。若构建树没有 `kernel/` 所在的 Git 元数据，版本会退化为未知值。

版本字符串只标识驱动来源，不表示 Built-In/LKM、preinit、late-load、SUSFS 状态或 Hook 类型；这些由 `GET_INFO` 的能力标志单独报告。

## SUSFS 集成

FinaSU 仓库不包含 SUSFS 的内核实现。要启用内核集成版 SUSFS，必须给目标内核应用与其版本严格匹配的 SUSFS 补丁，并在 FinaSU 构建中开启：

```text
CONFIG_KSU=y
CONFIG_KSU_SUSFS=y
CONFIG_THREAD_INFO_IN_TASK=y
```

这段配置仍使用默认 Tracepoint。若还要求编译期启用 Inline：

```text
CONFIG_KSU_HOOK_SUSFS_INLINE=y
```

此时匹配的内核补丁必须直接调用 FinaSU 的入口。构建会运行 `kernel/tools/check_susfs_inline.sh` 检查实际参与构建的内核源码；缺少 `ksu_handle_*`、出现旧钩子名或 `CONFIG_KSU_MANUAL_HOOK` 冲突时构建直接失败。

| 路径 | 直接入口 |
| --- | --- |
| input / 安全模式 | `ksu_handle_input_handle_event` |
| exec 与 post-exec | `ksu_handle_execveat*`、`ksu_handle_post_execveat_sucompat` |
| preinit | `ksu_setup_preinit_file`、`ksu_preinit_handle_post_execveat` |
| `faccessat` | `ksu_handle_faccessat` |
| `vfs_statx` / `vfs_fstat` | `ksu_handle_stat`、`ksu_handle_vfs_fstat` |
| read / init.rc | `ksu_handle_sys_read` |
| reboot / supercall | `ksu_handle_sys_reboot` |
| setresuid | `ksu_handle_setresuid` |

本仓库提供 SUSFS 接入点、SELinux 辅助符号和配置层；`fs/susfs.c`、`include/linux/susfs.h` 与各内核版本的路径钩子来自外部 SUSFS 项目。不能把一个内核版本的补丁直接用于另一个版本，也不能把 FinaSU 的驱动侧接口当作完整 SUSFS 实现。

外部还有一种 `susfs-lkm` 方案：它把 SUSFS 侧实现做成可加载内核模块，可由内核模块加载器管理，或封装为 Magisk/FinaSU 模块交付。它和目标内核源码集成版在可见功能、钩子覆盖范围与兼容性上可能有差距；能力仍取决于具体 `susfs-lkm` 版本和内核。不要把它描述成与内核补丁版完全等价。

::: warning SUSFS 不是普通内置能力

“驱动 Built-In”不等于“SUSFS 已开启”。先有匹配的内核实现，再有 `CONFIG_KSU_SUSFS` 和对应配置，管理器中的设置才有实际对象。

:::

::: warning Inline 失败不会静默回退

Inline 初始化失败时，内核仍报告选定的 Hook 类型，但不会设置 `KSU_GET_INFO_FLAG_HOOK_READY`。管理器会显示异常；不会自动改用 Tracepoint。

:::

## 常见故障

::::: collapse accordion

:::: collapse 构建时提示 UAPI headers missing
确认 `CONFIG_HEADERS_INSTALL=y`，清理并重新准备目标内核头；保留 `KSU_REQUIRE_PREINIT=1`，不要靠删除它来通过发布构建。
::::

:::: collapse 内核能开机但管理器显示不可用
先核对管理器 APK 签名与构建日志中的 DER size/hash，再检查设备是否真的启动了刚构建的镜像。版本号相同不能证明镜像已更新。
::::

:::: collapse 模块文件不生效
分别检查驱动身份、当前挂载后端、模块是否启用、`skip_mount`、App Profile 与安全模式。Built-In 只改变驱动编译方式，不会自动修复挂载配置。
::::

:::: collapse 开启 SUSFS 后 modpost 或符号失败
确认使用的是匹配内核版本的 SUSFS 补丁、`CONFIG_KSU=y`，并核对补丁是否要求本仓库已经提供的符号。不要同时叠加整套上游驱动补丁。
::::

:::: collapse 启用 SUSFS Inline 后构建提示 hook-site check failed
检查补丁是否在真实的 `fs/`、`drivers/input/` 与 `kernel/` 源码中加入全部直接入口，且没有残留旧钩子名或 Manual Hook 配置。Inline 不能通过 LKM 绕过该检查。
::::

:::: collapse 可以拿另一个 KMI 的镜像试刷吗
不可以。KMI、内核配置、分区布局、AVB 与厂商补丁都可能不同。一个 KMI 构建成功不能证明其他 KMI 可启动。
::::

:::::

## 相关页面

- [Built-In 安装](/guide/install/built-in/)：面向安装者的分区、刷写与恢复流程。
- [SuperCall 与 UAPI](/developer/supercall/)：驱动 FD、ioctl 权限与 ABI 同步。
- [SUSFS](/builtin/susfs/)：源码集成、Hook 类型与 SUSFS-LKM 的边界。
- [构建 FinaSU](/developer/build/)：LKM、载荷、APK 与最终发布流程。
