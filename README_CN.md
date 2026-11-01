<div align="center">
<img src="logo.png" width="180" alt="FinaSU logo">

[![Latest Release](https://img.shields.io/github/v/release/LyraVoid/FinaSU?label=Release&logo=github)](https://github.com/LyraVoid/FinaSU/releases/latest)
[![Telegram](https://img.shields.io/badge/Telegram-FinaSU-blue?logo=telegram)](https://t.me/FinaSU)
[![License](https://img.shields.io/github/license/LyraVoid/FinaSU?logo=gnu)](/LICENSE)

</div>

**语言：** [English](./README.md) / [中文](./README_CN.md)

FinaSU 是基于 [KernelSU](https://github.com/tiann/KernelSU) 构建的 Android 内核级 Root 方案，集成了内核 `su` 与 Root 权限管理、模块系统、内置 Zygisk 引擎、SUSFS 支持以及可配置的现代化管理器。

---

## 功能概览

FinaSU 提供：

- 内核级 `su` 与基于白名单的 Root 权限管理
- 基于 overlayfs 与 metamodule 的类 Magisk 模块系统
- App Profile，将 Root 权限限制在单个应用策略内
- 内置 FolkZygisk 引擎，并提供管理器控制
- 内置 Sui 扩展，为支持的应用提供 Shizuku API
- SUSFS 支持，按内核能力开关
- 编译期 syscall Hook 选择：默认 Tracepoint，ARM64 Built-In 内核可选择 SUSFS Inline
- 内核级隐藏：挂载隐藏、SELinux 隐藏、AVC 日志伪装与卸载策略
- `.fpt` 主题，与 FolkPatch 互通，并提供在线主题商店
- 多页壁纸、自定义字体与启动图标切换
- 模块仓库浏览与一键安装
- 应用内语言切换与社区翻译

## 前置要求

- ARM64（arm64-v8a）Android 设备；同时保留 `x86_64` 与 `riscv64` 钩子
- 推荐 GKI 内核（5.10+）；4.14+ 受支持，但需要自行编译内核
- 通过 LKM（可加载的 `kernelsu.ko`）、修补 boot 镜像或 AnyKernel3 包进行安装

## 管理器

- 多种首页布局，包括 GridUI 与仪表盘式页面
- 可配置的主题：配色、壁纸与字体
- 分类式设置中心，涵盖通用、外观、行为、功能、安全与多媒体
- 可选更新检查与开机模块屏蔽

## 模块与扩展

### KernelSU 模块

标准 KernelSU 模块由管理器管理。同一时间只允许一个 metamodule 提供挂载后端；当已启用的 metamodule 与内置引擎不兼容时，FinaSU 会给出提示。

### FolkZygisk

FolkZygisk 是内置的 Zygisk 引擎。管理器提供安装与激活控制；已有外置 Zygisk provider 时 `ksud` 会让内置引擎自动让位，因此 provider 模块可正常安装。

### Sui

Sui 是内置的 Shizuku API 后端：使用 Shizuku 的应用直接走该扩展，无需另装 Shizuku。载荷由本仓库构建并内置于 ksud，因此安装、启停与修复都由 FinaSU 负责，不走 Sui 自带安装器；注入的 root 与 shell 服务由 `ksud` 监管。

界面完全由管理器提供：逐应用的 Shizuku 权限列表、默认权限、对 Sui 隐藏的应用、诊断信息，以及真实请求的授权弹窗——请求经管理通道回传。上游 Sui 自带的界面不再随包提供。

### SUSFS

SUSFS 管理按能力开关。仅在支持的内核上显示相关控件，每次配置变更都会附带应用回执。`CONFIG_KSU_SUSFS` 只启用 SUSFS 功能层，与 syscall Hook 后端相互独立；源码集成的 ARM64 内核可以在编译期选择直接入口的 SUSFS Inline，LKM 发布包继续使用 Tracepoint。管理器只通过内核 ABI 读取后端，不能在运行时切换。

### Folk-Mount

Folk-Mount 是内置的模块挂载引擎，可在自动、内置或 metamodule 模式间切换。

### NoMount

NoMount 是内建的 VFS 路径重定向后端：不产生任何挂载，而是由内核模块直接接管路径解析与目录遍历，因此不会污染 `/proc/mounts`。内核模块随 ksud 一起分发（按 KMI 内嵌，由本仓库自行编译），后端本身不依赖模块或 `nm` 二进制。

管理器提供驱动状态、注入规则数、可见性例外与「关闭挂载」；挂载模式可选 `自动 / 内建 Folk Mount / 仅 metamodule / NoMount / 关闭`，默认是 `自动`，只有显式选择才会进入 NoMount。选择 NoMount 时若驱动不可用会自动回退到内建引擎，并在设置页说明原因。ksud 还会在装载与注入之前先落一份「进行中」记录：如果某次 NoMount（或 metamodule）注入之后没能走完启动，下次开机会跳过它、改用内建引擎把模块挂上，并把原因写进 `/data/adb/ksu/log/mount.log`；同一份日志（有体积上限、会自动轮转）也会记下每次注入的规则数与命中的顶层目录，方便从 bug 报告里判断注入到底碰了哪些路径。要查"卡住时内核在等什么"，可以先 `ksud mount capture on`（默认卡过 150 秒就取证，`ksud mount capture on 60` 可以改时长，`off` 关掉）：取证结果同样落在 `/data/adb/ksu/log/mount_capture.log`，包含 `sysrq w` 打印的全部阻塞任务栈、内核日志结尾、D 状态任务与 pstore。可见性是一份由 ksud 保存的例外名单：名单内的应用看到原厂文件系统，其余应用都能看到注入的文件；NoMount 页可以按需把各应用 Profile 的「卸载模块」判定复制进这份名单，改动 Profile 后需要再复制一次。

目录型符号链接会被当作目录递归遍历并**逐文件合并**（带环路检测与按模块去重）；`.replace` 标记或 opaque 目录同样默认降级为逐文件合并，只有白名单里的 `/product/fonts`、且其原目录不存在或为空时才允许整目录替换。分区根目录，以及名为 `lib64`、`lib`、`bin`、`etc` 的目录，**永远不会**被整目录替换。降级会写进日志：`mount.log` 里带 `dir_replace=N`，`ksud nomount inject --dry-run --verbose` 也会分别标注文件重定向与目录替换。

内建 NoMount 还会启动一个独立的 UI 就绪观察者（默认 180 秒）：它要求 `sys.boot_completed=1`、SurfaceFlinger 正在运行、开机动画已结束，而不只是收到 boot-completed 回调。**观察跑完但没能确认**时会留下一个持久标记，下一次开机会改用内建挂载引擎并记录一条 warning；如果观察**根本没来得及跑**（设备在时限内就重启，或助手进程被结束），则判为「不确定」，标记会被丢弃、NoMount 照常启用。这些信号不保证显示真的可用，它也不是内核装载器的超时。把 `/data/adb/ksu/mount_ui_guard` 写成 `delay=300` 可以改观察时长（30–900 秒），写 `off` 则停止新的观察；「已确认失败」的标记会在下一次用内建引擎成功挂载后自动清除，所以重试之前请先把那次失败开机的 `/data/adb/ksu/log/mount.log` 取走。

## 安全提示

管理器通过 APK 签名验证自身身份：只有受信任的管理器以及用户显式认领的应用才能管理内核。Root 权限基于白名单，只有被授权的应用才能访问 `su`。漏洞上报请参阅 [SECURITY.md](./SECURITY.md)。

## 翻译

英语和中文是参考语言。翻译修正请只提交对应语言的文件；新增语言时，请在 Pull Request 中提供完整翻译文件。

## 下载与安装

1. 从[发布页面](https://github.com/LyraVoid/FinaSU/releases/latest)下载最新安装包。
2. 在设备上安装管理器。
3. 按应用内引导安装内核模块、修补 boot 镜像，或刷入 AnyKernel3 包。

## 从源码构建

仓库提供了与 CI 一致的本地构建入口：

```bash
./build.sh ksud     # 构建 Rust 用户态守护进程
./build.sh lkm      # 构建内核模块
./build.sh apk      # 构建管理器 APK（需先构建 ksud）
./build.sh all      # 完整本地构建
```

各组件具体的构建与检查命令请参阅 [AGENTS.md](./AGENTS.md)。

## 开源致谢

- [KernelSU](https://github.com/tiann/KernelSU)：上游项目
- [ReZygisk](https://github.com/PerformanC/ReZygisk)：FolkZygisk 由其改写而来
- [YukiSU](https://github.com/Anatdx/YukiSU)：动态管理器参考
- [BakaSU](https://github.com/Baka-SU/BakaSU)：AnyKernel3 刷写及部分小功能参考
- [Magisk](https://github.com/topjohnwu/Magisk)：`magiskpolicy` 与模块概念
- [SUSFS](https://gitlab.com/simonpunk/susfs4ksu)：内核级隐藏
- [Sui](https://github.com/XiaoTong6666/Sui)：内置 Shizuku API 后端实现
- [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)：扩展实现的 Shizuku 客户端 API 库
- [FolkPatch](https://github.com/LyraVoid/FolkPatch)：主题格式与设计参考
- [MMRL](https://github.com/MMRLApp/MMRL)：模块仓库格式参考

## 许可证

- `kernel` 目录下的文件遵循 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- 其余部分遵循 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html)。

## 社区

- Telegram：[**@FinaSU**](https://t.me/FinaSU)
