---
title: 基础样式
description: CircleUI 的顶栏、状态、计数、系统信息与视觉层级
createTime: 2026/10/24 00:00:00
permalink: /theme/theme-circle/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 基础样式

<Badge text="单列布局" type="tip" /> <Badge text="状态优先" type="info" /> <Badge text="系统事实不会伪造" type="warning" />

CircleUI 使用统一的全宽 tonal card 与纵向间距。页面本身滚动，顶栏保持应用级操作；卡片颜色来自当前 Material 配色，壁纸启用后由 surface 材质接管透明度与暗度。

## 顶栏

顶栏标题可以是：

- 应用名；
- `KernelSU`；
- 自定义文字；
- 高级标题图片。

顶栏右侧操作按当前能力显示：

- SUSFS 已检测时显示管理入口；
- 背景音乐启用且文件存在时显示播放/暂停；
- 安装/更新按钮；
- 重启菜单。

延迟加载模式下，安装入口会被禁用，避免在无法安全刷写启动镜像的会话里诱导用户操作。

## 状态卡

状态标题只有三种基本结果：

:::: table 状态判断

| 标题 | 条件 | 常见操作 |
| --- | --- | --- |
| 运行中 | 驱动版本已连接 | 点击进入安装/更新流程 |
| 未安装 | 没有驱动，但内核为 GKI | 点击安装 |
| 不支持 | 没有驱动，且内核不满足安装假设 | 阅读兼容性；不提供伪造状态 |

::::

运行中时还会显示驱动版本与内核 UAPI 组合，并可能附加：

- `LKM` 或 `Built-In` 运行模式；
- 安全模式标签；
- 越狱模式标签；
- 自定义 LKM 标签。

未安装且 SELinux 为 Permissive 时，可能显示越狱按钮。越狱入口是当前环境下的操作，不等于永久 Root。

## 计数卡

完整功能环境中，超级用户与模块计数以横向两张卡显示。不可用或非完整环境不会绘制计数区。

计数点击只负责导航，不会修改授权或安装模块。

## 系统信息卡

基础事实始终显示：

- 管理器版本；
- 内核版本；
- 内核完整版本（设备提供时）；
- 设备型号；
- SELinux；
- Seccomp。

可选事实按能力显示：

- 指纹；
- Zygisk 实际提供者；
- Folk Mount / NoMount 实际提供者；
- 检测到的 SUSFS 版本、实现与管理方式。

这些行来自实际状态快照，不会因为主题字段缺少而伪造一项能力。

## 颜色与字体

Circle 没有独立颜色页面。全部使用：

- “设置 → 外观 → 主题设置”的颜色方案；
- “设置 → 外观 → 字体”的字体；
- “设置 → 外观 → 壁纸”的背景与表面设置。

## 相关页面

- [工作卡片](/theme/theme-circle/working-card/)
- [背景设置](/theme/theme-circle/background/)
- [首页与布局](/setting/home/)
