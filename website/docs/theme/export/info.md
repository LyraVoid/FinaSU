---
title: 导出主题
icon: material-symbols:save-as-outline
createTime: 2026/10/24 00:00:00
permalink: /theme/export/info/
category: 主题
tag:
  - 导出
  - FPT
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 导出主题

导出前填写主题名称、作者、版本和描述。名称与版本应保持稳定，方便主题商店和本地列表识别更新。

## 导出内容

`.fpt` 可以包含：

- 全局或分页壁纸、视频背景与高级标题图片。
- 颜色模式、强调色、布局与导航图标。
- 工作卡片和模块横幅相关资源。
- 自定义字体、背景音乐与音效。
- 受支持的主题配置字段。

主题不会包含 Root 授权记录、App Profile 数据或已安装模块列表。

## 保存位置

管理器导出的默认可浏览目录为 `Download/FinaSU/Themes`。也可以使用系统文件选择器指定保存位置。

导出会先校验配置与资源，再生成单文件包。资源缺失或超过包大小限制时导出失败。
