---
title: 自定义字体
icon: material-symbols:font-download-outline
createTime: 2026/10/24 00:00:00
permalink: /theme/global/font/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 自定义字体

FinaSU 支持软件默认、系统默认和自定义字体三种模式。自定义字体从文件选择器导入。

## 格式

当前主题资源策略接受 `.ttf` 和 `.otf`。字体应包含界面实际使用的字符；中文界面需要完整的 CJK 字形，否则回退字体可能造成混排。

## 应用

在“设置 → 外观 → 字体”中选择字体文件。文件会复制到管理器私有目录，之后移除原始文件不会影响应用。

导入失败或出现缺字时选择“恢复默认字体”。主题包中的字体资源使用 `font.ttf` 共享命名。
