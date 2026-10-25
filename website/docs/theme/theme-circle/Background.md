---
title: 背景设置
description: CircleUI 的页面背景、分页壁纸、视频与不存在的专属卡面
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-circle/background/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 背景设置

<Badge text="页面背景" type="tip" /> <Badge text="支持分页" type="info" /> <Badge text="无专属工作卡图" type="warning" />

CircleUI 的背景来自全局壁纸层，不来自 Circle 专属卡片 surface。它支持单图、多页面图片和视频，但不会为状态卡单独叠加一张图片。

## 单图背景

在“设置 → 外观 → 壁纸”中启用壁纸并选择图片。图片会按窗口裁切，作为所有页面的共同背景。

:::: tip 图片选择

可以直接选择普通图片，也可以从壁纸图鉴下载后再手动选择。图鉴下载不会自动应用为管理器壁纸。

::::

## 分页背景

开启多背景后，五个主页面可各自选择图片：

:::: table 分页对应

| 页面 | 主题条目 |
| --- | --- |
| 首页 | `background_home.*` |
| 插件 | `background_kernel.*` |
| 超级用户 | `background_superuser.*` |
| 模块 | `background_system_module.*` |
| 设置 | `background_settings.*` |

::::

Circle 首页只使用 `background_home.*`。没有为该页设置图片时，该页不会借用其他页面的背景。

## 视频背景

视频使用 `video_background.*`，由壁纸总开关统一门控。播放时替换图片背景层，并按裁切方式填满窗口。详见[视频背景](/theme/global/video/)。

## 表面层级

背景之上还有两层：

- 背景暗度：黑色遮罩，改善对比度；
- 前景 surface 不透明度：决定卡片遮住多少背景。

图片模糊只作用于图片，不作用于视频。Circle 状态卡没有独立图片和不透明度配置，因此跟随全局页面表面材质。

## 主题字段

```json
{
  "isBackgroundEnabled": true,
  "backgroundOpacity": 0.6,
  "backgroundBlur": 20,
  "backgroundDim": 0.2,
  "isDualBackgroundDimEnabled": true,
  "backgroundDayDim": 0.2,
  "backgroundNightDim": 0.4,
  "isMultiBackgroundEnabled": true,
  "wallpaper_use_color": false
}
```

## 相关页面

- [壁纸](/setting/wallpaper/)
- [全局背景](/theme/global/transparency/)
- [背景模糊](/theme/global/blur/)
