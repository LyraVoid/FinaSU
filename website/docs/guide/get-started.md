---
title: 快速开始
createTime: 2026/10/24 00:00:00
permalink: /guide/get-started/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 快速开始

本页只介绍两种持久化驱动方式：**LKM** 和**内核源码集成**。另有只对当前启动有效的越狱/延迟加载，不在本文范围。大多数用户应该先选 LKM：它不需要你编译 Linux 内核，由管理器把与设备 KMI 匹配的 `kernelsu.ko` 放进启动镜像。只有 LKM 不支持你的设备，或者你明确需要内置驱动、SUSFS 等内核侧改动时，才进入源码集成。

::: tip 新手首选 LKM

- **普通安装与升级**：使用官方 LKM，管理器会自动选择匹配的驱动。
- **不要先编译内核**：源码集成需要完整的设备内核源码、构建环境和刷机经验，属于进阶路线。

:::

| 方式 | 适用场景 |
| --- | --- |
| **LKM（推荐）** | 官方支持当前 KMI；不编译内核；后续驱动更新更方便 |
| 内核源码集成（进阶） | LKM 不支持当前设备；需要内置驱动；需要集成 SUSFS |

::: warning 这里的 LKM 不是第三方内核模块

“LKM”专指 FinaSU 的 Root 驱动 `kernelsu.ko`。管理器“内核模块”页面里的条目由 `ksud kmod` 管理，与获得 Root 无关。

:::

## LKM 支持范围

可以先把 KMI 理解为“内核与驱动之间的接口版本”。它不是 Android 系统版本，也不能只看内核版本号。官方 release 的预构建 LKM 当前覆盖以下 KMI：

| Android 基线 | 内核版本 |
| --- | --- |
| Android 12 | 5.10 |
| Android 13 | 5.10、5.15 |
| Android 14 | 5.15、6.1 |
| Android 15 | 6.6 |
| Android 16 | 6.12 |
| Android 17 | 6.18 |

对应目录名为 `android12-5.10`、`android13-5.10`、`android13-5.15`、`android14-5.15`、`android14-6.1`、`android15-6.6`、`android16-6.12` 和 `android17-6.18`。

支持范围还要同时满足以下条件：

- **架构**：官方预构建驱动支持 `arm64-v8a` 和 `x86_64`。riscv64 没有 LKM 驱动资产，不能只靠安装管理器获得 Root。
- **KMI**：设备实际 KMI 必须与目录名完全一致。Android 版本相邻或内核版本相近都不能代替 KMI 匹配。
- **启动方式**：实体设备通常需要解锁 Bootloader（开机时加载 Android 的引导程序），并能修补、刷入管理器识别出的启动分区；模拟器等环境以管理器提供的对应镜像流程为准。
- **系统状态**：安装前移除其他内核级 Root 方案，避免多个驱动同时接管启动链。

::: important 不确定时先停在管理器里

如果管理器无法识别当前 KMI，或者预构建列表中没有这个 KMI，不要下载名称相近的 `.ko` 强行安装。此时设备不在官方 LKM 覆盖范围内，应先确认兼容性；旧内核、非 GKI 内核或厂商改动较大的内核通常需要源码集成或移植。

:::

## 推荐：使用 LKM

::: steps

1. **准备管理器与备份**

   安装官方 FinaSU 管理器，确认当前 KMI，并备份 `boot`、`init_boot` 或管理器提示的分区。

2. **在管理器中选择安装方式**

   | 设备状态 | 建议方式 |
   | --- | --- |
   | 首次安装，有可用启动镜像 | 选择文件并修补 |
   | 有完整 OTA 或 factory 镜像 | 下载文件并修补 |
   | 设备已有可用 Root | 直接安装 |
   | A/B 设备已 OTA、尚未重启 | 安装到未使用的槽位 |

3. **使用管理器提供的驱动**

   保持默认的内置 LKM 选择即可，通常不需要手动选择 `.ko` 文件。直接安装时使用当前设备的 KMI；下载 OTA 或 factory 镜像时，管理器会从镜像中探测 KMI。选择本地启动镜像时，要先确认该镜像确实属于当前设备和系统。

4. **修补并刷入启动分区**

   修补完成后，用 `fastboot` 或设备对应的刷写方式写入管理器提示的同一分区。Android 13 及以后拆分 ramdisk 的设备通常会修补 `init_boot`，其他设备可能修补 `boot`；最终以管理器识别的分区为准。

5. **重启并验证**

   打开管理器首页，确认驱动显示为 `LKM`；然后在终端应用发起一次 Root 请求，确认出现授权界面。

:::

### 管理器做了什么

管理器会修补启动镜像：把 `ksuinit` 和与设备 KMI 匹配的 `kernelsu.ko` 放进 ramdisk，由 `ksuinit` 在启动早期加载驱动。它不是把 `.ko` 放进 `/data/adb/modules/`，也不是管理器“内核模块”页面里的普通第三方模块。

核心修补过程等价于下面的命令，普通用户不需要手动执行：

```sh
ksud boot-patch -b boot.img -m android15-6.6_kernelsu.ko
```

省略 `-m` 时，`ksud` 会根据当前设备和输入镜像等可用信息自动确定 KMI，并使用自身内嵌的匹配驱动。管理器实际执行时还会指定输出文件、目标分区等参数；上面的命令只展示核心修补过程。不要把 LKM 放进 `/data/adb/modules/`，也不要尝试用普通 `insmod` 开机脚本代替启动镜像修补。

### 驱动文件从哪里来

FinaSU release 会提供 `FinaSU_<tag>_kernel-bundle.zip`，其中包含 arm64 与 x86_64 各个 KMI 的驱动和 `ksud`：

::: file-tree title="kernel-bundle.zip"

- FinaSU_\<tag\>_kernel-bundle.zip
  - arm64/
    - android15-6.6_kernelsu.ko
    - ...
  - x86_64/
    - android15-6.6_kernelsu.ko
    - ...
  - ksud/
    - android-arm64
    - android-x86_64
    - ...

:::

面向真实设备安装时，优先使用管理器内置的完整 release 资产。如果必须手动选择本地 `.ko`，应使用 FinaSU 为同一 KMI 构建的驱动；文件名写得一致并不能证明文件兼容。来源不明的 `.ko` 不应加载。

::: warning 本地 LKM 不是通用文件

一个 `android15-6.6_kernelsu.ko` 只适用于对应 KMI。架构或 KMI 不匹配可能导致驱动无法加载；驱动内置的管理器签名不匹配时，驱动即使加载成功，管理器也不会把它识别为可管理状态。

:::

### LKM 更新

LKM 模式下，当管理器自带的内置驱动与当前运行版本不一致时，管理器可以提示安装；更新在重启后生效，不需要重新编译内核。内核源码集成模式则需要重新构建并刷入整个内核镜像。

## 进阶：从内核源码集成

::: warning 新手可以从这里停止

下面的流程面向能够编译和刷写设备内核的用户。它需要与设备系统完全匹配的内核源码，并且不在 CI 的设备启动测试范围内。只要官方 LKM 支持你的 KMI，就不需要走这条路。

:::

适合源码集成的典型情况：

- 官方 LKM 不提供当前 KMI。
- 需要把驱动固定在内核镜像中。
- 需要集成 SUSFS，而当前集成要求 `CONFIG_KSU=y`。

使用设备当前系统对应的内核源码、分支、提交和配置。其他版本的内核源码即使版本号相同，也可能带有不同的厂商修改，不能直接套用。

:::: steps

1. **接入 FinaSU 驱动**

   在目标内核源码根目录执行：

   ```sh
   /path/to/FinaSU/kernel/setup.sh --dir /path/to/FinaSU
   ```

   `setup.sh` 会创建 `drivers/kernelsu` 链接，并在 `drivers/Makefile` 与 `drivers/Kconfig` 中加入驱动入口。GKI 源码会自动使用 `common/drivers`。保留 FinaSU 仓库的 `.git`，完整版本号才能正常显示。

2. **启用内核选项**

   ```sh
   scripts/config --enable KSU
   scripts/config --enable KPROBES
   scripts/config --enable EXT4_FS
   scripts/config --enable HEADERS_INSTALL
   ```

   | 配置 | 用途 |
   | --- | --- |
   | `CONFIG_KSU=y` | 将驱动编入内核镜像 |
   | `CONFIG_KPROBES=y` | 安装驱动使用的 kprobe/tracepoint 钩子 |
   | `CONFIG_EXT4_FS=y` | 驱动构建依赖 |
   | `CONFIG_HEADERS_INSTALL=y` | 构建 preinit 运行器所需的 UAPI 头文件 |

3. **构建内核**

   在目标内核原有构建参数中显式加入 `KSU_REQUIRE_PREINIT=1`。下面的 `...` 只是占位，必须替换成设备内核原本的 `make` 参数。否则缺少目标 UAPI 头文件时，构建只会关闭 preinit，不会报错：

   ```sh
   make ... KSU_REQUIRE_PREINIT=1
   ```

4. **打包并刷入**

   按目标设备原有的内核构建、镜像打包和刷写流程操作。生成镜像后，先确认启动分区、槽位和 AVB 处理方式，再执行刷写。

::::

### 管理器签名

内核校验的是管理器 APK 内签名证书的 DER 数据，不是 APK 文件本身的哈希。官方 FinaSU 管理器证书（`CN=FolkSU`）的签名信息已经公开，位于第二签名槽：

| 参数 | 官方 FinaSU 管理器 |
| --- | --- |
| 证书长度 | `0x02cc`（716 字节） |
| 证书 SHA-256 | `2d89b50aaf1db013c2b6738f973f48b62ea21a3a8d7216bfec3521065d448458` |

将官方签名和第二个受信任签名槽一起传给内核构建：

```sh
make ... \
  KSU_REQUIRE_PREINIT=1 \
  KSU_EXPECTED_SIZE2=0x02cc \
  KSU_EXPECTED_HASH2=2d89b50aaf1db013c2b6738f973f48b62ea21a3a8d7216bfec3521065d448458 \
  KSU_SECOND_SIGNATURE_RELEASE=1
```

签名不匹配时，内核通常仍能启动，但管理器不会识别这个驱动，也就无法通过管理器管理授权和配置。

::: details 使用自行签名的管理器

自行重签的 APK 不能使用上面的官方哈希，需要从**最终要安装的那份 APK**导出证书。准备 Android SDK Build-Tools 中的 `apksigner`：

```sh
manager_apk=/path/to/FinaSU_vX.Y.Z.apk

apksigner verify --print-certs-pem "$manager_apk" |
  openssl x509 -outform DER > manager-cert.der

cert_size=$(printf '0x%04x' "$(wc -c < manager-cert.der)")
cert_hash=$(sha256sum manager-cert.der | awk '{print $1}')

printf 'size=%s\nhash=%s\n' "$cert_size" "$cert_hash"
```

用输出值替换 `KSU_EXPECTED_SIZE2` 和 `KSU_EXPECTED_HASH2`，并保留 `KSU_SECOND_SIGNATURE_RELEASE=1`。也可以用 `apksigner verify --print-certs "$manager_apk"` 交叉检查证书 SHA-256。`apksigner` 通常位于 Android SDK 的 `build-tools/<版本>/apksigner`。

:::

### SUSFS

只有在内核目标版本匹配时，才应用 SUSFS 补丁并启用 `CONFIG_KSU_SUSFS`。当前集成还要求 `CONFIG_KSU=y` 和 `CONFIG_THREAD_INFO_IN_TASK=y`。FinaSU 仓库提供对接入口，但不包含 SUSFS 内核实现（`fs/susfs.c` 及各内核版本钩子）。

完整的内核侧说明见 [内核集成](/developer/builtin-integration/#susfs) 和 [SUSFS 与内核级隐藏](/builtin/susfs/)。

### 自己构建 LKM

这是维护者流程，普通安装不需要执行。需要 Docker 和目标 KMI 对应的 DDK 镜像，FinaSU 默认只构建 `android15-6.6`：

```sh
KMIS=android15-6.6 ./build.sh lkm
```

产物为 `kernel/android15-6.6_kernelsu.ko`，同时暂存到 `userspace/ksud/bin/aarch64/` 供 `ksud` 打包。一次本地构建只覆盖 `KMIS` 中列出的 KMI；需要支持多台设备或完整发布矩阵时，应使用 CI 生成的 kernel bundle。

## 验证

打开管理器首页，确认驱动显示为 `Built-In` 或 `LKM`，然后访问“超级用户”页面。终端应用首次请求 Root 时应出现授权界面。模块、Zygisk 与 Sui 的改动通常要重启后才生效。

源码集成不在 CI 的设备测试范围内。至少要在目标设备上验证启动、Root 授权、模块挂载和 SELinux 状态，不能只凭编译成功判断可以发布。

::: danger

错误的 KMI、未匹配的系统源码或不一致的管理器签名都可能导致驱动不可用；刷入未经验证的内核镜像还可能导致无法启动。先备份，并准备可用的恢复通道。

:::

::: warning
不要在未备份启动镜像时尝试不熟悉的模块或内核文件。无法开机时参见 [启动循环救援](/guide/rescue-bootloop/)。
:::
