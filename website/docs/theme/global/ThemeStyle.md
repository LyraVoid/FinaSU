---
title: 夜间模式、颜色与标题
icon: material-symbols:palette-outline
description: FinaSU 明暗模式、强调色、色彩规范、色彩风格、壁纸取色与顶栏标题
createTime: 2026/10/24 00:00:00
permalink: /theme/global/theme-style/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 夜间模式、颜色与标题

<Badge text="Material 3 Expressive" type="tip" /> <Badge text="壁纸取色可覆盖种子" type="warning" /> <Badge text="对比度无独立 UI" type="info" />

颜色设置位于“设置 → 外观 → 主题设置”。它控制管理模式器的 Material 配色、明暗模式和顶栏标题，不改变 Root 授权与系统级主题。

## 明暗模式

:::: table 界面选项

| 选项 | 行为 |
| --- | --- |
| 跟随系统 | 系统深色时用深色，否则用浅色 |
| 浅色 | 强制浅色配色 |
| 深色 | 强制深色配色 |
| AMOLED 纯黑主题 | 仅在深色模式下出现；把主要背景与 surface 设为纯黑 |

::::

AMOLED 是深色模式的附加开关，不是第四种独立模式。切回浅色或系统浅色时，不会保留纯黑表面。

## 强调色

颜色选择器第一项“随光”对应 `keyColor = 0`。选择它会重新允许系统动态取色；选择一个具体色块会保存该 ARGB 值并关闭这一动态路径。

界面提供 15 个可直接选择的颜色：

```text
赤霞 桃夭 藤紫 夜阑 青黛 沧澜 霁青
碧潭 竹青 流金 琥珀 橘霞 沉香 远岫 樱雪
```

主题协议另外兼容 5 个 FolkPatch 颜色 token：

```text
light_blue light_green lime deep_orange ink_wash
```

FinaSU 导出时同时写入 `customColor` 和精确的 `folksu_keyColor`。前者让兼容实现有可识别的目录项，后者让 FinaSU 在回导时保留任意 ARGB 精度。

:::: warning 壁纸取色优先于强调色

当壁纸总开关开启、当前页面有可用图片背景且“从壁纸取色”开启时，管理器使用壁纸提取出的 ARGB 种子。此时设置里的强调色不会覆盖该页面种子。多背景模式下，固定取首页壁纸作为全局强调色种子，避免每次滑动页面都重新生成整套配色。视频模式会隐藏这个取色开关，也不会从视频帧实时取色。

::::

## 色彩标准

::::: field-group

:::: field MD3 2021
@type `colorStandard=MD3_2021`
使用 Material Design 3 2021 色彩规范。FinaSU 的 `classic` 生成模式还会优先使用内置 FolkPatch 色表。
::::

:::: field M3E 2025
@type `colorStandard=M3E_2025`
使用 Material 3 Expressive 2025 色彩规范。只对 TonalSpot、Neutral、Vibrant 和 Expressive 直接生效。
::::

:::::

选择其他色彩风格时，2025 设置会回退到 2021 规范。界面仍保留当前 `M3E_2025` 偏好，以便之后切回支持它的风格。

## 色彩风格

:::: table 九种 PaletteStyle

| 界面名称 | 主题值 | 特征 |
| --- | --- | --- |
| 柔和 | `TONAL_SPOT` | 低饱和、均衡的默认风格 |
| 中性 | `NEUTRAL` | 接近灰阶，强调色更克制 |
| 活力 | `VIBRANT` | 饱和度更高 |
| 表现 | `EXPRESSIVE` | 更鲜明的 Material 3 Expressive 配色 |
| 彩虹 | `RAINBOW` | 多色相变化更明显 |
| 缤纷 | `FRUIT_SALAD` | 多种颜色组合更活跃 |
| 单色 | `MONOCHROME` | 主要使用同色相明暗层级 |
| 忠实 | `FIDELITY` | 更接近种子色本身 |
| 内容 | `CONTENT` | 更强调内容色与种子色的关系 |

::::

这些名称是当前简体中文资源中的界面文案，其他语言会随应用翻译变化；主题 JSON 始终使用上表的稳定枚举值。

## 生成模式与对比度

主题协议还支持两个影响配色生成但当前主题设置页没有独立滑块的字段：

:::: table 协议字段

| JSON 字段 | 值 | 实际作用 |
| --- | --- | --- |
| `colorGenerationMode` | `classic` | 有预设种子时优先使用 FolkPatch 色表；无种子时使用系统动态色 |
| `colorGenerationMode` | `custom` | 使用 MaterialKolor 按种子、风格与规范生成 |
| `colorContrast` | `STANDARD` | 对比度 0.0 |
| `colorContrast` | `MEDIUM` | 对比度 0.5 |
| `colorContrast` | `HIGH` | 对比度 1.0 |

::::

用户切换色彩风格或规范时，管理器会把生成模式设为 `custom`。不要为了在 UI 中看到“对比度”而寻找不存在的入口；它目前主要由主题包导入、导出和内部设置携带。

## 壁纸的明暗极性

壁纸启用后，管理器会读取当前页面背景的亮度：

- 深色文字与浅色文字跟随背景亮度调整；
- 深色主题不会因为亮壁纸自动翻转成浅色主题；
- 夜间通过暗度改善可读性；
- 分页背景只改变该页背景与内容极性，固定取首页壁纸作为全局强调色种子。

“从壁纸取色”在“设置 → 外观 → 壁纸”中单独开关。它和明暗模式是两条不同路径：前者决定种子色，后者决定亮色/暗色方案。

## 顶栏标题

顶栏标题位于“设置 → 外观 → 布局”的“高级标题图片”区域。标题文字有三态：

:::: table 标题文字模式

| 模式 | `titleTextMode` | 显示 |
| --- | --- | --- |
| 应用名 | `default` | 当前应用名称 |
| KernelSU | `kernelsu` | `KernelSU` |
| 自定义 | `custom` | `titleCustomText` 中保存的文本 |

::::

启用 `isAdvancedTitleStyleEnabled` 后，`title_image.*` 会替代文字标题。图片显示区域为约 180×40 dp，使用 `FIT_CENTER`，不会随任意长宽比拉伸。

图片可调：

- 日间不透明度 `0..1`；
- 夜间不透明度 `0..1`；
- 暗化 `0..1`；
- 水平偏移：JSON 为 `-2..2`，界面显示为 `-200..200 dp`；实际位移按 `titleImageOffsetX × 100dp` 计算。

关闭标题图片后会回退到当前文字模式。只有图片本身存在时，标题图才能显示；选文件会自动启用标题图，清除文件会关闭该功能。

## 恢复默认

“恢复默认主题”会恢复：

- 跟随系统；
- `keyColor = 0`；
- 系统动态色可用；
- TonalSpot；
- 2025 色彩规范；
- `custom` 生成模式；
- 标准对比度；
- 标题图资源清除。

当前 `resetTheme` 的媒体重置只关闭标题图并恢复日/夜不透明度、暗度和偏移，不会清空 `titleTextMode` 或 `titleCustomText`。因此恢复后若标题图已关闭，顶栏仍可能显示之前选择的文字模式。

普通“恢复默认字体”不会同时重置颜色。完整恢复入口在[主题预览与使用](/theme/theme-use/#恢复默认主题)。

## 相关页面

- [壁纸](/setting/wallpaper/)：背景、模糊与取色开关。
- [首页与布局](/setting/home/)：布局、信息隐藏和底部卡片。
- [全局背景](/theme/global/transparency/)：表面透明度与暗度。
- [主题包结构](/developer/theme-package/)：颜色字段和枚举。
