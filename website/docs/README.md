---
title: FinaSU
createTime: 2026/10/24 00:00:00
permalink: /
pageLayout: home
externalLinkIcon: false
config:
  - type: doc-hero
    full: true
    background: tint-plate
    tintPlate: 255,217,220
    hero:
      name: FinaSU
      tagline: Kernel-based Android Root
      text: 基于内核的 Android Root 方案
      image: /icon.png
      actions:
        - theme: brand
          text: 快速开始 →
          link: /guide/intro/
        - theme: alt
          text: GitHub
          link: https://github.com/LyraVoid/FinaSU
        - theme: alt
          text: Telegram
          link: https://t.me/FinaSU
  - type: features
    features:
      - title: 内核级 su 与白名单
        icon: material-symbols:shield-lock-outline
        details: Root 裁决发生在内核中，只有白名单内的应用能够获得授权。
        link: /guide/intro/
      - title: Folk Mount
        icon: material-symbols:folder-open-outline
        details: 在启动阶段注入 Magisk 风格模块，并可按环境选择挂载后端。
        link: /builtin/folk-mount/
      - title: App Profile
        icon: material-symbols:account-cog-outline
        details: 把 Root 与卸载策略限定在单个应用范围内。
        link: /manager/app-profile/
      - title: FolkZygisk
        icon: material-symbols:extension-outline
        details: 内置 Zygisk 引擎，运行 Zygisk 模块无需另行安装提供者。
        link: /builtin/folkzygisk/
      - title: Sui
        icon: material-symbols:hub-outline
        details: 内置 Shizuku API 后端，为兼容应用提供受管理的系统 API 访问。
        link: /guide/sui/
      - title: SUSFS 与内核级隐藏
        icon: material-symbols:visibility-off-outline
        details: 在内核支持时提供挂载、SELinux 与审计日志层面的隐藏能力。
        link: /builtin/susfs/
      - title: NoMount
        icon: material-symbols:mountains-outline
        details: 通过 VFS 路径接管注入模块，避免向进程暴露传统挂载记录。
        link: /builtin/nomount/
      - title: .fpt 主题与个性化
        icon: material-symbols:palette-outline
        details: 使用主题包、多页壁纸与自定义字体定制管理器界面。
        link: /theme/theme-use/
      - title: 模块仓库
        icon: material-symbols:storefront-outline
        details: 浏览兼容的模块来源，并直接查看、下载和安装模块。
        link: /manager/module-store/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---
