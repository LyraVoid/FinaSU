---
title: SuperCall 与 UAPI
createTime: 2026/10/24 00:00:00
permalink: /developer/supercall/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# SuperCall 与 UAPI

FinaSU 内核驱动通过匿名 inode 上的 ioctl 接收用户态请求。`ksud` 与管理器 JNI 通过驱动 FD 调用这些接口。

## 版本

当前 `KERNEL_SU_UAPI_VERSION` 为 6。`KSU_IOCTL_GET_INFO` 返回驱动版本、能力位、最大 feature ID 与 UAPI 版本。

## 主要命令

- 信息与事件：`GET_INFO`、`GET_FULL_VERSION`、`REPORT_EVENT`、`CHECK_SAFEMODE`。
- 白名单：`NEW_GET_ALLOW_LIST`、`NEW_GET_DENY_LIST`、`UID_GRANTED_ROOT`。
- Profile：`GET_APP_PROFILE`、`SET_APP_PROFILE`。
- 功能开关：`GET_FEATURE`、`SET_FEATURE`。
- 挂载：`GET_WRAPPER_FD`、`ADD_TRY_UMOUNT`、`MANAGE_MARK`。
- 诊断：`GET_SULOG_FD`、`LOOKUP_SYMBOLS`。
- 动态管理器：设置签名、查询授权应用与更新授权 UID。
- 内核策略：UTS View、NetIsolate 与网络事件。

结构体和 ioctl 编号位于 `kernel/include/uapi/supercall.h`。

## 同步要求

修改 ABI 时必须同步：

1. `kernel/include/uapi/` 中的结构体、版本与 feature ID。
2. `userspace/ksud/src/ksu_uapi.rs` 和 `ksucalls.rs`。
3. `manager/app/src/main/cpp/ksu.cc` 与 `Natives.kt`。

内核会检查请求长度、权限与管理器身份。不要绕过 `perm.c` 的检查，也不要把仅由 Root 管理的数据暴露给普通应用。
