---
title: 设置总览
createTime: 2026/10/24 00:00:00
permalink: /setting/overview/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 设置总览

设置页按“通用、外观、行为、功能、安全、验证、多媒体、备份”分组。本页是 `SettingsSearchIndex.kt` 与文档页的对照表。

::: info 索引基线
当前源码可解析出 100 条静态分类行和 7 条运行时生成的身份验证目标，共 107 条；此前记录的 102 条口径已经随源码变化。设置首页另有 6 个非分类入口，列在本页末尾。
:::

## 通用

| 索引 ID | 文档 |
| --- | --- |
| `general.check_update` | [更新与版本](/setting/update/) |
| `general.check_module_update` | [更新与版本](/setting/update/) |
| `general.profile_template` | [App Profile 模板](/setting/profile-template/) |
| `general.language` | [语言](/setting/language/) |

## 外观

| 索引 ID | 文档 |
| --- | --- |
| `appearance.theme` | [主题与颜色](/setting/theme/) |
| `appearance.wallpaper` | [壁纸](/setting/wallpaper/) |
| `appearance.font` | [字体](/setting/font/) |
| `appearance.theme_store` | [主题商店](/setting/theme-store/) |
| `appearance.theme_store_mode` | [主题商店](/setting/theme-store/) |
| `appearance.launcher_icon` | [首页与布局](/setting/home/) |
| `appearance.home_layout` | [首页与布局](/setting/home/) |
| `appearance.layout_variant` | [导航](/setting/navigation/) |
| `appearance.support_links` | [首页与布局](/setting/home/) |
| `appearance.support_links_title` | [首页与布局](/setting/home/) |
| `appearance.support_links_subtitle` | [首页与布局](/setting/home/) |
| `appearance.support_links_url` | [首页与布局](/setting/home/) |
| `appearance.title_text` | [首页与布局](/setting/home/) |
| `appearance.title_image` | [首页与布局](/setting/home/) |
| `appearance.title_offset` | [首页与布局](/setting/home/) |
| `appearance.hide_home_fingerprint` | [首页与布局](/setting/home/) |
| `appearance.hide_home_zygisk` | [首页与布局](/setting/home/) |
| `appearance.hide_home_susfs` | [首页与布局](/setting/home/) |
| `appearance.hide_home_mount` | [首页与布局](/setting/home/) |
| `appearance.page_scale` | [首页与布局](/setting/home/) |
| `appearance.module_description_lines` | [模块卡片与横幅](/setting/module-cards/) |

## 行为

| 索引 ID | 文档 |
| --- | --- |
| `behavior.navigation_badge` | [导航](/setting/navigation/) |
| `behavior.predictive_back` | [导航](/setting/navigation/) |
| `behavior.nav_custom_icons` | [导航](/setting/navigation/) |
| `behavior.nav_mode` | [导航](/setting/navigation/) |
| `behavior.nav_show_plugin` | [导航](/setting/navigation/) |
| `behavior.nav_show_superuser` | [导航](/setting/navigation/) |
| `behavior.nav_show_module` | [导航](/setting/navigation/) |
| `behavior.floating_bar_style` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.floating_bar_glass` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.glass_blur_strength` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.glass_transparency` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.glass_highlight` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.glass_specular` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.glass_inner_glow` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.glass_border` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.floating_bar_compact` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.floating_auto_hide` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.floating_swipe_hide` | [悬浮导航栏](/setting/floating-bar/) |
| `behavior.sucompat` | [交互与兼容](/setting/interaction/) |
| `behavior.soft_reboot` | [交互与兼容](/setting/interaction/) |
| `behavior.web_debugging` | [交互与兼容](/setting/interaction/) |
| `behavior.auto_jailbreak` | [交互与兼容](/setting/interaction/) |
| `behavior.module_card_show_more` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_card_fold_system` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_card_spliced` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_card_button_style` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_enabled` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_folk` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_api_mode` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_api_source` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_marketplace` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_clear_cache` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_opacity` | [模块卡片与横幅](/setting/module-cards/) |
| `behavior.module_banner_custom_opacity` | [模块卡片与横幅](/setting/module-cards/) |

## 功能与安全

| 索引 ID | 文档 |
| --- | --- |
| `function.kernel_umount` | [Root 与挂载策略](/setting/root-policy/) |
| `function.umount_modules_default` | [Root 与挂载策略](/setting/root-policy/) |
| `function.avc_spoof` | [隐藏与内核安全](/setting/hiding/) |
| `function.sulog` | [诊断与反馈](/setting/diagnostics/) |
| `function.dynamic_manager` | [访问控制](/setting/access/) |
| `function.folk_zygisk` | [内置能力](/setting/builtin/) |
| `function.sui` | [内置能力](/setting/builtin/) |
| `function.sui_manage` | [内置能力](/setting/builtin/) |
| `function.folk_mount` | [内置能力](/setting/builtin/) |
| `function.nomount` | [内置能力](/setting/builtin/) |
| `function.uts_view` | [内置能力](/setting/builtin/) |
| `function.netisolate` | [内置能力](/setting/builtin/) |
| `security.selinux_hide` | [隐藏与内核安全](/setting/hiding/) |
| `function.mount_view_filter` | [隐藏与内核安全](/setting/hiding/) |
| `security.selinux_mode` | [隐藏与内核安全](/setting/hiding/) |
| `security.adb_root` | [访问控制](/setting/access/) |
| `security.shell_su_adb_only` | [访问控制](/setting/access/) |

## 验证

| 索引 ID | 文档 |
| --- | --- |
| `authentication.default_method` | [身份验证](/setting/authentication/) |
| `authentication.pin` | [身份验证](/setting/authentication/) |
| `authentication.pattern` | [身份验证](/setting/authentication/) |
| `authentication.target.app_start` | [身份验证](/setting/authentication/) |
| `authentication.target.module_install` | [身份验证](/setting/authentication/) |
| `authentication.target.module_uninstall` | [身份验证](/setting/authentication/) |
| `authentication.target.module_disable` | [身份验证](/setting/authentication/) |
| `authentication.target.module_flash` | [身份验证](/setting/authentication/) |
| `authentication.target.theme_import` | [身份验证](/setting/authentication/) |
| `authentication.target.reboot` | [身份验证](/setting/authentication/) |

## 多媒体

| 索引 ID | 文档 |
| --- | --- |
| `multimedia.vibration` | [多媒体](/setting/multimedia/) |
| `multimedia.music` | [多媒体](/setting/multimedia/) |
| `multimedia.autoplay` | [多媒体](/setting/multimedia/) |
| `multimedia.loop` | [多媒体](/setting/multimedia/) |
| `multimedia.startup_sound` | [多媒体](/setting/multimedia/) |
| `multimedia.sound` | [多媒体](/setting/multimedia/) |
| `multimedia.sound_source` | [多媒体](/setting/multimedia/) |
| `multimedia.sound_preset` | [多媒体](/setting/multimedia/) |
| `multimedia.sound_scope` | [多媒体](/setting/multimedia/) |
| `multimedia.select_file` | [多媒体](/setting/multimedia/) |
| `multimedia.clear_file` | [多媒体](/setting/multimedia/) |

## 备份

| 索引 ID | 文档 |
| --- | --- |
| `backup.local_enabled` | [备份](/setting/backup/) |
| `backup.webdav_enabled` | [备份](/setting/backup/) |
| `backup.open_folder` | [备份](/setting/backup/) |
| `backup.before_flash` | [备份](/setting/backup/) |
| `backup.modules` | [备份](/setting/backup/) |
| `backup.allowlist` | [备份](/setting/backup/) |
| `backup.now` | [备份](/setting/backup/) |
| `backup.list` | [备份](/setting/backup/) |
| `backup.webdav_test` | [备份](/setting/backup/) |
| `backup.webdav_browse` | [备份](/setting/backup/) |

## 设置首页入口

| 入口 | 文档 |
| --- | --- |
| 壁纸图库 | [壁纸](/setting/wallpaper/) |
| 问题反馈 | [诊断与反馈](/setting/diagnostics/) |
| 免责声明 | [维护与危险操作](/setting/maintenance/) |
| 发送日志 | [诊断与反馈](/setting/diagnostics/) |
| 关于 | [关于](/setting/about/) |
| 卸载 | [维护与危险操作](/setting/maintenance/) |
