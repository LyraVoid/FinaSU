---
title: 平板适配指南
description: FocusUI 精确 2×2 断点、窗口尺寸与导航自动模式
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-focus/tablet/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 平板适配指南

<Badge text="600dp 宽度" type="tip" /> <Badge text="必须横屏关系" type="info" /> <Badge text="页面缩放不改导航断点" type="warning" />

FocusUI 不是按设备类型判断平板，而是按当前窗口尺寸决定单列或 2×2。

## 精确条件

```text
完整功能环境
&& screenWidthDp >= 600
&& screenWidthDp > screenHeightDp
```

全部满足时：

```text
状态   管理器
设备   存储
```

否则按状态、管理器、设备、存储纵向排列。

:::: table 常见结果

| 窗口 | 结果 |
| --- | --- |
| 手机关屏宽度，竖屏 | 单列 |
| 手机横屏且宽度不足 600dp | 单列 |
| 平板竖屏，宽 600dp 但高更大 | 单列 |
| 平板横屏，宽 600dp 且宽 > 高 | 2×2 |

::::

## 页面缩放

Focus 断点读取 Android 配置的 `screenWidthDp`。页面缩放会改变应用内容的视觉密度，但不能把它当成“让任意手机变成平板布局”的开关。

导航“自动”模式另外使用未缩放的窗口容器尺寸：

```text
widthDp >= 600 && widthDp > heightDp
```

因此 Focus 重排和侧边导航通常在同方向变化，但它们是两条不同实现，不应在文档里写成一个通用“平板模式”。

## 导航选择

- 自动：满足上述宽高条件时用侧边栏，否则用底部或应用默认形态；
- 始终侧边：主动选择 rail；
- 始终底部：主动选择 docked bar；
- 悬浮：使用浮动栏。

## 卡片布局与裁切

2×2 时同一行卡片按 Intrinsic height 对齐。图片 surface 按每张卡实际测量边界裁切；极长文本、字体和页面缩放可能改变卡片高度与图片有效构图。

发布平板主题时，至少测试：

- 600×? 临界宽度；
- 横屏与竖屏切换；
- 三键/手势导航；
- 侧边 rail 与悬浮栏；
- 大字体与页面缩放。

## 相关页面

- [导航](/setting/navigation/)
- [Focus 基础样式](/theme/theme-focus/basic/)
- [Focus 硬件信息](/theme/theme-focus/hardware/)
