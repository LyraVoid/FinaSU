---
title: 主题与颜色
createTime: 2026/10/24 00:00:00
permalink: /setting/theme/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 主题与颜色

<Badge text="只改管理器" type="tip" /> <Badge text="壁纸种子优先" type="warning" />

“设置 → 外观 → 主题设置”集中管理明暗模式、强调色和 Material 3 Expressive 视觉参数。它不改变 Android 系统主题，也不影响 Root 状态。

## 明暗模式

- **跟随系统**：使用设备的深浅色状态。
- **浅色**：固定浅色界面。
- **深色**：固定深色界面。
- **AMOLED 纯黑主题**：只在深色模式下出现，把主要背景和 surface 设为纯黑。

## 动态配色

颜色选择器的“随光”对应系统动态种子；选择具体色块后保存该 ARGB 强调色。界面提供 15 个预设颜色，主题协议还兼容 5 个 FolkPatch 颜色 token。

壁纸页面可以启用“从壁纸取色”。多背景模式下固定使用首页图片提取的种子作为全局强调色，不会随每次页面滑动重新生成配色；视频模式不会实时从视频帧取色。

## 色彩规范

- **色彩标准**：MD3 2021 或 M3E 2025。
- **色彩风格**：在柔和、中性、活力、表现、彩虹、缤纷、单色、忠实和内容等色相关系中选择。
- **按钮指示器**：在开关中加入状态图标。
- **离散滑块**：在滑块轨道显示刻度。

色彩协议字段还支持 `colorGenerationMode` 和 `colorContrast`，但当前页面没有这两个字段的独立滑块。完整说明见[夜间模式、颜色与标题](/theme/global/theme-style/)；主题包导入导出见[主题预览与使用](/theme/theme-use/)。
