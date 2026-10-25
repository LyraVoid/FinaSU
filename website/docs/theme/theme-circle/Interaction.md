---
title: 动画与交互
description: CircleUI 的点击区域、安装入口、导航与系统动画边界
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-circle/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 动画与交互

<Badge text="Material 3 Expressive" type="tip" /> <Badge text="没有独立动画引擎" type="info" /> <Badge text="长按不编辑卡面" type="warning" />

CircleUI 的反馈来自 Compose Material 3 Expressive：卡片按压、涟漪、页面转换和系统预测性返回。主题不能注入自定义动画脚本，也没有 Circle 专属动画速度或时长字段。

## 可点击区域

:::: table 首页交互

| 区域 | 点击结果 |
| --- | --- |
| 状态卡 | 进入安装/更新流程；延迟加载时禁用 |
| 超级用户计数 | 跳到超级用户页面 |
| 模块计数 | 跳到模块页面 |
| SUSFS 行 | 打开 SUSFS 管理页 |
| 支持卡片 | 打开对应外部 URL |
| 顶栏安装 | 进入安装/更新 |
| 顶栏重启 | 打开重启动作列表 |

::::

Circle 状态卡没有长按操作。长按编辑图片与透明度是 Grid、Focus 或 Dashboard 的 surface 行为。

## 导航

底部栏、侧边栏、自动模式和悬浮模式由设置决定。自定义导航图标是主题资源，但不会改变页面数量或目标路由。

预测性返回是否可用还取决于 Android 版本与系统动画。FinaSU 只能按设置启用，不能向不支持的系统补上该能力。

## 减少动态效果

当前主题协议没有独立的“关闭全部动画”字段。系统减少动态效果、开发者选项和 Android 版本会影响部分系统级动画；应用内卡片反馈仍由 Compose 主题控制。

## 相关页面

- [导航](/setting/navigation/)
- [交互与兼容](/setting/interaction/)
- [主题预览与使用](/theme/theme-use/)
