---
title: 背景透明度与表面层级
icon: material-symbols:opacity
description: FinaSU 壁纸表面不透明度、暗度、日夜分离和布局专属 surface
createTime: 2026/10/24 00:00:00
permalink: /theme/global/transparency/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 背景透明度与表面层级

<Badge text="透明度作用于界面表面" type="tip" /> <Badge text="暗度作用于背景" type="info" /> <Badge text="Circle 无专属卡面" type="warning" />

FinaSU 将背景图片或视频与前景 surface 分开绘制。“不透明度”不是把壁纸本身调淡，而是调整前景卡片与容器的 alpha；“暗度”才是在背景上叠加黑色遮罩。

## 主壁纸

壁纸总开关控制所有背景模式。启用后可选择：

- 单图背景 `background.*`；
- 五个页面各自的分页背景；
- 视频背景 `video_background.*`。

主配置包含：

:::: table 主壁纸字段

| 字段 | 作用 | 范围 |
| --- | --- | --- |
| `backgroundOpacity` | 前景 surface 的透明度 | 0–1 |
| `backgroundDim` | 单一日夜共用暗度 | 0–1 |
| `isDualBackgroundDimEnabled` | 是否分开日间/夜间暗度 | 布尔 |
| `backgroundDayDim` | 日间暗度 | 0–1 |
| `backgroundNightDim` | 夜间暗度 | 0–1 |
| `isMultiBackgroundEnabled` | 各主页面是否使用自己的背景 | 布尔 |
| `wallpaper_use_color` | 是否从壁纸提取配色种子 | 布尔 |

::::

不透明度越高，背景越被卡片遮住，文字对比度越稳定；不透明度越低，背景纹理越明显。

## 页面背景

多背景模式提供五个槽位：

:::: table 分页资源

| 页面 | ZIP 基础名 | 内部页面索引 |
| --- | --- | --- |
| 首页 | `background_home.*` | Home |
| 插件 | `background_kernel.*` | Plugin |
| 超级用户 | `background_superuser.*` | Superuser |
| 模块 | `background_system_module.*` | Module |
| 设置 | `background_settings.*` | Settings |

::::

“插件”使用 `background_kernel` 是历史兼容命名，不是把图片写入内核。某个页面没有独立图片时，该页不绘制分页背景；不要把它误认为资源损坏。

## 布局专属 surface

::::: field-group

:::: field Grid 工作卡
@type `layout.grid.workCard`
独立图片 `grid_working_card_background.*`。支持单一不透明度、日夜不透明度、单一暗度、启用和三个隐藏开关。
::::

:::: field Focus 四张卡
@type `layout.focus`
父级保存共享不透明度、日夜不透明度和日夜暗度；四张子卡只保存各自图片。父级关闭时四张卡都不显示背景图。
::::

:::: field Dashboard Hero
@type `layout.dashboard.hero`
图片 `dashboard_card_bg.*`，支持独立启用、日夜不透明度和日夜暗度。
::::

:::: field Circle 状态卡
@type `无专属图片 surface`
CircleUI 不提供单独工作卡图片。它只使用页面背景与全局表面材质，因此壁纸页不会出现 Circle 专属工作卡入口。
::::

:::::

## 日夜分离

日夜不透明度只在对应 surface 开启 `dual_opacity` 后生效；日夜暗度只在开启 `dual_dim` 后生效。不要只修改 day/night 数值却忘记打开分离开关。

Grid 工作卡支持日夜不透明度，但当前注册表没有日夜暗度；Dashboard 与 Focus 父级同时支持不透明度和暗度的日夜分离。

## 视频背景

视频与图片共用壁纸总开关。视频模式下：

- 视频按裁切填充并在前台循环；
- 黑色遮罩使用壁纸暗度；
- 图片模糊不作用于视频；
- 视频模式下“表面不透明度”和“模糊”滑块会隐藏；已经保存的不透明度仍参与全局表面 token，模糊不会作用于视频；
- 从壁纸取色依赖已经保存的图片种子；视频帧不会实时提取新种子，没有可用种子时回退到强调色或系统动态色。

不要把视频的“暗度”与视频音量混为一谈。音量字段是 `videoVolume`，只控制视频声音。

## 常见问题

::::: collapse accordion

:::: collapse 调了不透明度，背景还是完全看不见
检查主壁纸开关、当前页面是否有背景、surface 是否启用，以及卡片是否本身就是不透明材质。
::::

:::: collapse 只有一页没有背景
多背景模式要求该页面槽位有独立图片。没有配置的页面不会拿其他页面的图片补位。
::::

:::: collapse Focus 四张卡只有一张有图
这是允许状态。四张子卡图片独立，父级开关与共享数值统一。
::::

:::: collapse 日间和夜间看起来一样
确认已开启日夜分离，并确认当前主题确实处于对应的亮色/暗色模式。
::::

:::: collapse 想在 Circle 上设置工作卡图片
当前 FinaSU CircleUI 没有该 surface。应切换到 Grid、Focus 或 Dashboard，或使用页面背景。
::::

:::::

## 相关页面

- [壁纸](/setting/wallpaper/)：全部滑块与分页选择入口。
- [背景模糊](/theme/global/blur/)：图片模糊范围。
- [视频背景](/theme/global/video/)：视频循环和生命周期。
- [Grid 工作卡](/theme/theme-grid/working-card/)：Grid surface 完整字段。
- [Focus 基础样式](/theme/theme-focus/basic/)：四卡共享配置。
