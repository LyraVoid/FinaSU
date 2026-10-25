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
      text: 一个简约&功能丰富的 Root 管理器 方案
      image: /icon.png
      actions:
        - theme: brand
          text: 快速开始 →
          link: /guide/intro/
        - theme: alt
          text: 安装与刷写 →
          link: /guide/install/
  - type: features
    features:
      - title: 高度自定义
        icon:
          src: /icons/home/palette.svg
          alt: 高度自定义
          width: 26
          height: 26
          wrap: true
        details: 5 套首页布局，可替换壁纸、视频背景、字体、导航图标、颜色、音乐、音效和振动反馈。
        link: /setting/appearance/
      - title: 五套首页布局
        icon:
          src: /icons/home/layout.svg
          alt: 五套首页布局
          width: 26
          height: 26
          wrap: true
        details: CircleUI、GridUI、FocusUI、DashboardUI、StatsUI；FocusUI 和 StatsUI 提供实时指标。
        link: /theme/theme-use/#五套首页布局
      - title: 主题包与商店
        icon:
          src: /icons/home/store.svg
          alt: 主题包与商店
          width: 26
          height: 26
          wrap: true
        details: 导入或导出 .fpt 主题，浏览在线主题库；主题可包含布局、颜色、壁纸、字体、图标与声音。
        link: /setting/theme-store/
      - title: 内核级 Root
        icon:
          src: /icons/home/shield-lock.svg
          alt: 内核级 Root
          width: 26
          height: 26
          wrap: true
        details: 授权、白名单和 App Profile 由内核处理，可按应用设置 UID/GID、能力、SELinux 域和模块卸载策略。
        link: /manager/app-profile/
      - title: 内置引擎
        icon:
          src: /icons/home/folder-open.svg
          alt: 内置引擎
          width: 26
          height: 26
          wrap: true
        details: Folk Mount、FolkZygisk、NoMount 和 Sui 随包提供，由 ksud 管理，无需再安装一组上游模块。
        link: /builtin/
      - title: Zygisk 与 Sui
        icon:
          src: /icons/home/plug.svg
          alt: Zygisk 与 Sui
          width: 26
          height: 26
          wrap: true
        details: FolkZygisk 提供 Zygisk 环境；Sui 为兼容 Shizuku API 的应用提供后端，也支持外部兼容提供者。
        link: /builtin/folkzygisk/
      - title: 内核功能
        icon:
          src: /icons/home/network.svg
          alt: 内核功能
          width: 26
          height: 26
          wrap: true
        details: UTS View、网络隔离和 SUSFS 对接按内核实际支持开放；SUSFS 需要源码集成或匹配的 SUSFS-LKM。
        link: /builtin/uts-view/
      - title: 模块与插件
        icon:
          src: /icons/home/cube.svg
          alt: 模块与插件
          width: 26
          height: 26
          wrap: true
        details: 支持 systemless 模块、WebUI、Metamodule 和 Lua 插件，可从内置、社区、KernelSU 或自定义仓库获取模块。
        link: /manager/module-store/
      - title: 三种安装方式
        icon:
          src: /icons/home/install.svg
          alt: 三种安装方式
          width: 26
          height: 26
          wrap: true
        details: LKM 保留内核并修改 ramdisk；Built-In 将驱动编入内核；越狱模式不改启动分区，但重启后不保留 Root。
        link: /guide/install/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---
