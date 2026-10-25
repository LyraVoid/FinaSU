---
title: 工作卡片高级定制
description: Grid 工作卡图片、透明度、暗度、日夜与三个隐藏开关
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-grid/working-card/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 工作卡片高级定制

<Badge text="独立 surface" type="tip" /> <Badge text="支持长按编辑" type="info" /> <Badge text="图片存在 ≠ 已启用" type="warning" />

Grid 工作卡是 FinaSU 当前唯一同时拥有图片、透明度、暗度和元素隐藏项的工作卡 surface。StatsUI 网格顶部复用同一份配置。

## 完整选项

::::: table 工作卡参数

| 选项 | 单一日夜值 | 分离开启后 | 范围/取值 |
| --- | --- | --- | --- |
| 背景开关 | `enabled` | — | 布尔 |
| 图片 | `imageUri` 对应实际资源 | — | 图片文件 |
| 不透明度 | `opacity` | `day_opacity` / `night_opacity` | 0–1 |
| 透明度分离 | — | `dual_opacity` | 布尔 |
| 暗度 | `dim` | 无 Grid 日夜分离 | 0–1 |
| 隐藏状态图标 | `hide_icon` | — | 布尔 |
| 隐藏标题 | `hide_text` | — | 布尔 |
| 隐藏模式标签 | `hide_mode` | — | 布尔 |

:::::

主题中的主键为：

```text
surface.layout.grid.workCard.enabled
surface.layout.grid.workCard.opacity
surface.layout.grid.workCard.dual_opacity
surface.layout.grid.workCard.day_opacity
surface.layout.grid.workCard.night_opacity
surface.layout.grid.workCard.dim
surface.layout.grid.workCard.flag.hide_icon
surface.layout.grid.workCard.flag.hide_text
surface.layout.grid.workCard.flag.hide_mode
```

兼容实现还会写入 `isGridWorkingCardBackgroundEnabled`、`gridWorkingCardBackgroundOpacity`、`gridWorkingCardBackgroundDim`、日夜不透明度和三个隐藏标志。FinaSU 优先读取主键，再回退历史键。

## 图片与启用

图片条目支持：

```text
jpg png gif webp jpeg
```

GIF 由系统动画图片解码路径处理；其他格式按普通图片加载。图片是否绘制由“有图片并且 enabled”共同决定。

因此会出现两种容易误解的状态：

- 已选择图片但关闭背景：文件保留，卡片不显示图片；
- 开启背景但文件缺失：产生资源 warning，卡片回退到语义色。

## 文本与标签

::::: field-group

:::: field 隐藏状态图标
@type `hide_icon`
隐藏右上角的成功/警告/阻止图标，不影响卡片点击。
::::

:::: field 隐藏标题
@type `hide_text`
隐藏“运行中/未安装/不支持”。状态仍可通过图标、模式和安装按钮判断。
::::

:::: field 隐藏模式标签
@type `hide_mode`
隐藏 `LKM` / `Built-In`。安全模式、越狱模式与自定义 LKM 仍可能以其他标记出现。
::::

:::::

不建议同时隐藏图标、标题和模式后再移除其他页面入口；状态异常时，首页会缺少定位信息。

## 长按与恢复

长按工作卡可打开 surface 选项。选择图片后由图片裁剪器按工作卡实际边界处理。

“恢复默认主题”会清除工作卡图片并重置 surface。仅清除工作卡图片不会自动清除页面壁纸。

## 常见问题

::::: collapse accordion

:::: collapse 图片已经选了但不出现
检查背景开关、文件是否能解码，以及当前布局是否为 Grid，或 Stats 顶部是否为 `grid`。
::::

:::: collapse 切换日间/夜间不透明度没有变化
先开启“日夜不透明度分离”，再分别设置日间和夜间值。
::::

:::: collapse 暗度能日夜分离吗
当前 Grid 工作卡没有 `dual_dim`、`day_dim`、`night_dim` 字段；日夜暗度分离只在支持它的 surface 上出现。
::::

:::: collapse 隐藏模式标签后还能区分 LKM/Built-In 吗
首页工作卡不再显示，但可到安装/更新页面或状态详情查看。隐藏只影响卡片绘制。
::::

:::::

## 相关页面

- [背景设置](/theme/theme-grid/background/)
- [组件隐藏](/theme/theme-grid/component-hiding/)
- [主题包结构](/developer/theme-package/#surface-字段)
