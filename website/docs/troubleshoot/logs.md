---
title: 日志与反馈
description: 导出 FinaSU 诊断包、采集最小系统日志并识别敏感信息
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/logs/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 日志与反馈

<Badge text="先复现再导出" type="tip" /> <Badge text="压缩包含敏感信息" type="danger" /> <Badge text="保留时间线" type="warning" />

有效的反馈不是“日志都在这里”，而是让人能从时间线看出：做了什么、预期是什么、实际发生了什么、当时运行在哪一层。FinaSU 的“发送日志”会导出完整环境快照，不只是管理器自己的几行记录。

:::: steps

1. **记录问题时间**

   记下设备本地时间、时区和最近一次重启时间。不要只写“刚才”。

2. **执行固定复现步骤**

   例如“打开某应用 → 请求 Root → 执行命令 → 出现错误”。记录预期结果和实际结果。

3. **在复现后立即导出**

   先导出日志，再尝试重启、清缓存或修改模块。后续操作会改变现场。

4. **检查敏感信息**

   日志包包含包名、UID、路径、设备信息和授权状态。公开前逐项确认。

::::

## “发送日志”实际包含什么

入口在 **设置 → 诊断与反馈 → 发送日志**。管理器会生成：

```text
/cache/KernelSU_bugreport_YYYY-MM-DD_HH_mm.tar.gz
```

压缩包的核心内容如下：

```file-tree title="诊断包内容"
KernelSU_bugreport_YYYY-MM-DD_HH_mm.tar.gz
├── basic.txt                 # 设备、构建、内核、SELinux、运行模式
├── process.txt               # 进程、线程、UID、命令行与 SELinux label
├── dmesg.txt                 # 本次启动内核日志快照
├── logcat.txt                # all 缓冲区，带 UID，一次性 dump
├── bootlog.tar.gz            # /data/adb/ksu/log
├── mounts.txt                # PID 1 的 mountinfo
├── filesystems.txt           # 内核支持的文件系统
├── adb_tree.txt              # /data/adb 目录树
├── adb_details.txt           # /data/adb 递归权限与 SELinux 上下文
├── ksu_size.txt              # /data/adb/ksu 各项占用
├── packages.txt              # /data/system/packages.list
├── props.txt                 # getprop 全量属性
├── allowlist.bin             # Root 授权与 App Profile 数据
├── modules.json              # 当前模块状态
├── proc_modules.txt          # 已加载内核模块
├── boot_config.txt           # /proc/bootconfig
├── defconfig.gz              # /proc/config.gz（设备提供时）
├── kallsyms.txt              # 内核符号快照
├── tombstones.tar.gz         # /data/tombstones
├── dropbox.tar.gz            # /data/system/dropbox
├── pstore.tar.gz             # /sys/fs/pstore
├── diag.tar.gz               # 部分小米/Redmi 设备的 diag
└── oplus.tar.gz              # 部分 OPPO/OnePlus 启动日志
```

设备上没有对应目录或当前权限无法读取时，某些子项可能缺失或为空。缺失单个可选项不等于压缩包损坏。

::: important 完整导出依赖可用的 Root 环境

“发送日志”会通过 Root Shell 收集系统信息。管理器身份、`ksud` 或 Root Shell 不可用时，导出可能失败或不完整。此时先用 ADB 能采集多少就采集多少，并明确标注缺失项。

:::

## 各个日志能回答什么

:::: table 日志用途

| 数据 | 适合回答 | 不适合单独回答 |
| --- | --- | --- |
| `basic.txt` | 设备、Android、内核、SELinux、LKM 状态、安全模式 | Root 请求为何被拒 |
| `dmesg.txt` | 内核 panic、模块加载、SELinux/AVC、驱动初始化 | 应用侧完整堆栈 |
| `logcat.txt` | Android 服务、应用崩溃、管理器与 Sui 日志 | 启动前的内核错误 |
| `bootlog.tar.gz` | `ksud` 启动脚本、挂载、SU 日志与回退记录 | 内核自身的早期 panic |
| `tombstones` / `dropbox` | Java/native 崩溃与系统异常记录 | 模块文件是否可见 |
| `pstore` | 部分设备上一次崩溃的持久记录 | 所有机型都一定存在 |
| `mounts.txt` / `proc_modules.txt` | 当前挂载与已加载模块 | 内核没有提供某项能力的原因 |
| `allowlist.bin` / `modules.json` | 授权、Profile 与模块状态的结构化快照 | 完整解释应用行为 |
| `packages.txt` | 包名与 UID 对应关系 | 应用是否真的请求了 Root |

::::

## SU 日志与挂载日志

SU 日志位于 `/data/adb/ksu/log/`，记录 Root execve、传统 SU、授权和守护进程事件。挂载日志主要是：

```file-tree title="常用事件日志"
/data/adb/ksu/log
├── sulog-YYYY-MM-DD.log
├── sulog-YYYY-MM-DD-1.log
├── mount.log
└── mount_capture.log
```

`mount.log` 记录 Folk Mount 或 NoMount 本次启动的提供者、注入结果和回退原因。`mount_capture.log` 只在主动启用异常启动捕获后出现，不是每次启动都有。

先在管理器中按时间定位，再导出完整包。不要为了“让日志干净”先清空 SU 日志或 `dmesg`。

## 有 ADB 时的最小采集

以下命令在电脑上执行。设备没有相应权限时，输出可能为空或显示 `Permission denied`，不要伪造成成功。

:::: code-tabs

@tab:active 基础信息

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.fingerprint
adb shell uname -a
adb shell getenforce
```

@tab 内核与 Android 日志

```sh
adb shell dmesg -r > dmesg.txt
adb shell logcat -b all -v uid -d > logcat.txt
```

@tab FinaSU 状态

```sh
adb shell /data/adb/ksud boot-info current-kmi > current-kmi.txt
adb shell /data/adb/ksud boot-info supported-kmis > supported-kmis.txt
adb shell /data/adb/ksud module list > modules.txt
adb shell /data/adb/ksud nomount status --json > nomount.json
```

@tab 文件与挂载

```sh
adb shell cat /proc/1/mountinfo > mountinfo.txt
adb shell cat /proc/modules > proc_modules.txt
adb shell ls -alRZ /data/adb > adb-details.txt
```

::::

ADB 是普通 Shell 时，部分命令可能被 SELinux 或权限拒绝。若设备提供 ADB Root，按[安全设置](/setting/security/)启用后再采集；排错结束后关闭。

::: danger 不要清空日志再采集

`dmesg -c`、清空 SU 日志或删除 `/data/adb/ksu/log` 会破坏时间线。除非维护者明确要求且已经保存原始日志，否则不要执行清理。

:::

## 怎样整理时间线

提交时把“操作”和“日志时间”放在一起：

```text
20:41:03  重启设备
20:42:10  打开管理器，首页显示 LKM / 驱动已连接
20:42:18  在目标应用执行 Root 请求
20:42:19  应用提示“无 Root”
20:42:25  打开超级用户，UID 为 10xxx，Root 开关为关闭
20:43:00  导出日志
```

如果日志使用 UTC、内核单调时间或其他格式，保留原始时间并补充换算后的设备本地时间。不要只提供一张裁剪掉状态栏时间的截图。

## 隐私检查

:::: collapse accordion

### 设备与账户信息

属性、包列表和日志可能包含设备型号、构建指纹、序列号、账户相关字段、网络地址和安装来源。只保留定位问题所需部分。

### 应用与 UID

`packages.txt`、SU 日志、进程快照和 allowlist 会暴露安装包、运行进程、UID 与授权状态。若某些应用名称敏感，公开前用一致代号替换，并说明替换规则。

### 文件路径与命令行

进程命令行可能包含脚本路径、挂载点、账号、令牌、内网地址或私有参数。优先删除值，不要删除整段上下文。

### 管理器与备份凭据

不要公开管理器密码、WebDAV 用户名/密码、应用锁 PIN、私钥或备份密钥。FinaSU 的普通日志包不应被当作可直接公开的附件。

### 内核符号与内存地址

`kallsyms.txt` 可能包含大量内核符号和地址。维护者需要时再提供，公开前询问是否有替代采集方式。

::::

## 只发最小片段还是完整包

::::: field-group

:::: field 完整包适合
@type `复杂问题`
启动异常、模块挂载、内核能力、管理器身份和多层依赖问题。维护者可以交叉核对，但必须先处理隐私。
::::

:::: field 最小片段适合
@type `单一问题`
某个明确错误文本、单个应用的 SU 请求或一条确定的 UAPI 报错。截取后仍要保留时间、进程和前后文。
::::

:::: field 不要只发截图
@type `证据不足`
截图无法搜索、无法保留完整堆栈，也常遗漏状态栏时间。应同时提供原始文本。
::::

:::::

## 相关页面

- [诊断与反馈](/setting/diagnostics/)：设置入口与反馈边界。
- [SU 日志](/manager/su-log/)：事件类型、筛选和隐私。
- [提交问题报告](/troubleshoot/report/)：报告模板与必填字段。
- [驱动运行异常](/troubleshoot/runtime/)：日志中常见的运行层问题。
