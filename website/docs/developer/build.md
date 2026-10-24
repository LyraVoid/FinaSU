---
title: 构建 FinaSU
createTime: 2026/10/24 00:00:00
permalink: /developer/build/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 构建 FinaSU

`build.sh` 是本地发布流程的入口，与 GitHub Actions 的构建顺序一致。

## 依赖

- Linux 构建环境、Rust、Android SDK/NDK 与 JDK。
- Docker，用于按 KMI 构建 `kernelsu.ko` 和 NoMount。
- NDK `29.0.14206865`；更新版本可能破坏静态 `ksuinit` 链接。
- `manager/keystore.properties` 中有效的签名配置。

## 完整构建

```sh
cp manager/sign.example.properties manager/keystore.properties
./build.sh all
```

步骤依次为 `lkm`、`nomount`、`sui`、`ksuinit`、`ksud`、`apk`、`repack`。最终 APK 位于 `dist/`，其中包含本次构建的 `ksud`。

## 分步构建

```sh
./build.sh ksud
./build.sh apk
./build.sh repack
```

不要手工把 `libksud.so` 放进 APK 后作为正式包发布。该做法只适合临时调试。

## 常用变量

- `KMIS`：要构建的 KMI 列表。
- `ABIS`：目标 ABI。
- `DDK_RELEASE`：DDK 镜像版本。
- `KSUD_BUILD_TYPE`、`APP_BUILD_TYPE`：构建类型。
- `OUT_DIR`：输出目录。
- `KEYSTORE_PROPS`：签名配置文件。

本地 LKM 构建只生成指定 KMI。面向完整设备矩阵发布时，以 CI 产物为准。
