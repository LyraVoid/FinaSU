---
title: 动画与交互
description: StatsUI 顶部切换、Grid 工作卡长按、监控更新与导航
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-statsui/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 动画与交互

<Badge text="顶部形式可切换" type="tip" /> <Badge text="Grid 支持长按卡面" type="info" /> <Badge text="趋势不是动画脚本" type="warning" />

Stats 的交互由顶部形态、宽屏分组和实时采样共同决定。

## 顶部 list

- 状态卡点击进入安装/更新；
- 延迟加载时禁用安装路径；
- 运行模式标签受“隐藏状态标签”控制；
- 没有独立卡面长按。

## 顶部 grid

- 复用 Grid 工作卡和计数；
- 点击工作卡进入安装/更新；
- 长按工作卡打开 Grid surface；
- 点击计数进入超级用户或模块；
- 计数不可用时保留占位。

## 监控与统计

波形和进度条每 5 秒接收新样本，更新曲线与数值。没有可用数据时显示占位，不执行“假动画”。

环形图和图例没有点击动作。切换主题颜色会改变图表颜色，但不会改变数量。

## 宽屏切换

宽度达到 600dp 时切换为两列。这个过程是布局重排，不复制监控卡或统计卡，也没有单独的手动“桌面模式”开关。

## 动画边界

页面与控件反馈由 Material 3 Expressive 和系统提供。主题不能加入独立 Stats 动画、脚本化仪表盘或图表过渡参数。

## 相关页面

- [StatsUI 基础样式](/theme/theme-statsui/basic/)
- [首页与布局](/setting/home/)
- [导航](/setting/navigation/)
