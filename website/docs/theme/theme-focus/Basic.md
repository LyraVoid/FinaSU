---
title: 基础样式
description: FocusUI 四张卡的标题、字段、操作与共享背景模型
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-focus/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 基础样式

<Badge text="语义分区" type="tip" /> <Badge text="共享透明度" type="info" /> <Badge text="子卡只存图片" type="warning" />

FocusUI 的每张卡都有图标、标题、分隔线和内容行。卡片使用统一 tonal card 形状；有背景图时，文字与分隔线会切换到适合图片的对比色。

## 状态卡

标题为“状态”，显示：

- 运行中/未安装/不支持；
- 版本与 UAPI；
- 当前运行模式；
- 安全模式；
- 越狱模式；
- 自定义 LKM 状态。

卡片头部可能显示：

:::: table 状态操作

| 条件 | 操作 |
| --- | --- |
| 管理器过旧 | 更新管理器 |
| 内核或内置载荷需要更新 | 更新 |
| 未安装且可临时越狱 | 越狱 |
| 未安装且可正常安装 | 安装 |

::::

## 管理器卡

标题为“管理器”，内容是当前系统事实：

- 管理器版本；
- 内核版本；
- 内核完整版本；
- 设备型号；
- 指纹（可隐藏）；
- SELinux；
- Seccomp；
- Zygisk 提供者（存在且未隐藏）；
- 模块挂载实现（可用且未隐藏）；
- SUSFS 状态（检测到且未隐藏）。

这张卡比“管理器版本”一项更广，它承担整个系统的可识别事实。

## 设备卡

三个环形指标：

1. 电池温度；
2. CPU 温度；
3. 电池电量。

单位分别是 °C、°C、%。不可读取时显示 `—`，不会把未知值画成 0。

## 存储卡

按顺序显示：

- 内部 Data 使用量/总量；
- RAM 使用量/总量；
- zram（总量大于 0 时）；
- swap（总量大于 0 时）。

当前 Android 公共接口采集器不会主动填充 zram/swap，因此这两行在许多设备上不会出现；这是数据源限制，不是主题隐藏。

## 背景模型

::::: field-group

:::: field 父级
@type `layout.focus`
总开关、透明度、日夜透明度、暗度、日夜暗度。
::::

:::: field 状态卡图片
@type `focus_card_kernel_bg`
仅控制该卡是否绘制自己的图片。
::::

:::: field 管理器卡图片
@type `focus_card_app_bg`
只保存图片存在位，数值仍来自父级。
::::

:::: field 设备与存储卡图片
@type `focus_card_device_bg` / `focus_card_storage_bg`
两张卡分别选择图片，共享父级透明度与暗度。
::::

:::::

## 相关页面

- [硬件信息](/theme/theme-focus/hardware/)
- [全局背景](/theme/global/transparency/)
- [首页与布局](/setting/home/)
