---
title: 自定义字体
icon: material-symbols:font-download-outline
description: FinaSU 字体模式、文件格式、字体主题资源与缺字回退
createTime: 2026/10/24 00:00:00
permalink: /theme/global/font/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 自定义字体

<Badge text="只改管理器" type="tip" /> <Badge text="TTF / OTF" type="info" /> <Badge text="缺字会回退" type="warning" />

FinaSU 支持软件默认、系统默认和自定义字体三种模式。字体只影响管理模式器的 Compose 界面，不替换 Android 系统字体，也不影响其他应用或 Root 命令的输出。

## 三种模式

:::: table 字体模式

| 界面名称 | `fontMode` | 文件要求 |
| --- | --- | --- |
| 软件默认 | `app` | 不需要字体文件 |
| 系统默认 | `system` | 使用当前 Android 系统字体 |
| 自定义 | `custom` | 从系统文件选择器导入 TTF 或 OTF |

::::

选择文件后会自动进入自定义模式。切换回软件默认或系统默认不会删除已保存的自定义文件；清除自定义字体会删除管理器保存的那一份。

## 文件格式与存储

主题资源策略优先选择：

```text
font.ttf
font.otf
```

用户选择的文件会被复制到管理器私有目录，原始文件移动、改名或删除后不影响已应用字体。主题导出使用协议名 `font.ttf` 承载字体字节；即使源文件是 OTF，导出的 ZIP 条目仍可能是 `font.ttf`，渲染器按文件内容与 Android 字体支持处理。

:::: warning 文件后缀不是字体质量证明

把任意文件改成 `.ttf` 不会让它变成字体。导入后无法阅读或应用失败时，应使用真实、可被 Android 解析的字体文件。

::::

## 字符覆盖

字体只包含界面实际使用的字符时，缺失字形会按 Android 字体回退显示。常见结果是中英文、数字和标点粗细不一致。

发布主题字体前至少检查：

- 简体中文完整界面；
- 拉丁字母、数字、百分号与单位；
- 常用标点、箭头和程度符号；
- 主题自定义标题中的文字；
- 目标语言需要的扩展字形。

如果字体没有完整 CJK 覆盖，FinaSU 不会生成缺失字形，也不会把其他字体合并进去。

## 主题字段

```json
{
  "fontMode": "custom",
  "isFontEnabled": true
}
```

旧主题可能只有 `isFontEnabled`。导入时：

- `fontMode` 可用时以它为准；
- 没有 `fontMode` 但 `isFontEnabled=true` 时解释为自定义；
- 没有 `fontMode` 且 `isFontEnabled=false` 时回退软件默认；
- `fontMode=custom` 但文件缺失时产生 warning，并回退到应用字体。

## 恢复

“恢复默认字体”只处理字体模式与字体文件，不会清除壁纸、颜色或布局。完整“恢复默认主题”会同时清理字体与主题其他资源。

## 常见问题

::::: collapse accordion

:::: collapse 字体在设置页可见，但其他页面没有变化
确认当前选择的是自定义模式，并重启管理器页面或以系统提供的方式重建界面。字体只作用于管理器。
::::

:::: collapse 中文变成方块或混用其他字体
字体缺少对应字形，系统回退到其他字体。更换含完整字符集的字体，不要通过修改主题字段强制覆盖。
::::

:::: collapse 导入字体后文件管理器中的原文件消失
FinaSU 复制的是独立私有副本，删除原文件不应影响已保存字体。若原文件被其他工具删除，恢复原文件或重新选择。
::::

:::: collapse 主题字体没有加载
检查 `fontMode`、`isFontEnabled`、`font.ttf` / `font.otf` 条目和字体是否可解析。缺失字体会回退，不保证主题整体失败。
::::

:::::

## 相关页面

- [字体设置](/setting/font/)：逐项操作入口。
- [主题包结构](/developer/theme-package/)：字体资源和字段校验。
- [夜间模式、颜色与标题](/theme/global/theme-style/)：文字相关样式。
