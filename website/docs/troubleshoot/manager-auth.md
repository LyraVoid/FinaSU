---
title: 管理器认证失败
createTime: 2026/10/24 00:00:00
permalink: /troubleshoot/manager-auth/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 管理器认证失败

FinaSU 通过 APK 签名识别管理器。管理器显示驱动不可用，或更换 APK 后立即失效，通常说明签名与内核记录不匹配。

## 常见原因

- 安装了 debug、自行编译或不同密钥签名的 APK。
- 卸载后重新签名的构建覆盖了官方管理器。
- 内核构建使用了旧的 `KSU_EXPECTED_SIZE` / `KSU_EXPECTED_HASH`。
- 动态管理器授权没有同时写入签名和 appid。

## 恢复

1. 安装与当前内核匹配的官方管理器。
2. 如果确实需要其他构建，在该应用的 App Profile 中设为动态管理器。
3. 自己构建内核时，重新从目标 APK 计算证书大小与 SHA-256，并重新编译。

恢复官方管理器不会清空白名单。不要为了绕过认证而修改内核的签名校验或伪造包名。
