---
title: 内核集成
createTime: 2026/10/24 00:00:00
permalink: /developer/builtin-integration/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 内核集成

FinaSU 默认以可加载模块构建，也可以把驱动编入内核镜像。

## 内核选项

至少需要：

| 选项 | 用途 |
| --- | --- |
| `CONFIG_KSU=y` | 把驱动编入内核，而不是构建 LKM |
| `CONFIG_KPROBES=y` | 安装 kprobe/tracepoint 钩子 |
| `CONFIG_EXT4_FS=y` | 驱动依赖 |
| `CONFIG_HEADERS_INSTALL=y` | 构建 preinit 运行器 |
| `CONFIG_THREAD_INFO_IN_TASK=y` | SUSFS 相关构建 |

显式传入 `KSU_REQUIRE_PREINIT=1`，避免缺少 UAPI 头文件时静默关闭 preinit。

## 接入

在目标内核源码根目录执行：

```sh
./kernel/setup.sh --dir /path/to/FinaSU
scripts/config --enable KSU
scripts/config --enable KPROBES
scripts/config --enable EXT4_FS
scripts/config --enable HEADERS_INSTALL
make ... KSU_REQUIRE_PREINIT=1
```

编译器必须把管理器证书的大小与 SHA-256 编入驱动。使用本地签名密钥时，从 `build.sh` 或 release workflow 获取对应值；内核和管理器签名不匹配时，管理器无法识别驱动。

## SUSFS

SUSFS 内核实现不在此仓库。只应用与目标内核版本匹配的 SUSFS 补丁，并开启 `CONFIG_KSU_SUSFS`。当前 SUSFS 集成通常要求 `CONFIG_KSU=y`，因为补丁会直接引用驱动符号。

编入内核的版本不受构建 CI 的设备启动覆盖。发布前必须验证启动、Root 授权、模块挂载与 SELinux 状态。
