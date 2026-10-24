---
title: 主题包结构
createTime: 2026/10/24 00:00:00
permalink: /developer/theme-package/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 主题包结构

`.fpt` 是含 `theme.json` 与平铺资源文件的单文件包。FinaSU 通过 AES-CBC 加密流读取包内容，再按 ZIP 条目解析。

## 约束

- 最多 128 个文件条目。
- `theme.json` 最大 1 MiB。
- 解压后总量最大 1 GiB。
- 条目必须位于包根目录，不能包含 `/`、`\`、`.`、`..` 或 NUL。
- `theme.json` 必须存在且为 JSON 对象。

## 元数据

```json
{
  "meta_name": "Theme name",
  "meta_type": "phone",
  "meta_version": "1.0.0",
  "meta_author": "Author",
  "meta_description": "Description"
}
```

## 常见资源名

| 资源 | 文件名 |
| --- | --- |
| 主壁纸 | `background.{jpg,png,gif,webp,jpeg}` |
| 视频背景 | `video_background.{mp4,webm,mkv}` |
| 高级标题图 | `title_image.{jpg,png,gif,webp,jpeg}` |
| 字体 | `font.{ttf,otf}` |
| 分页壁纸 | `background_home.*` 等 |
| 导航图标 | `navIcons` 中声明的文件名 |
| 音乐与音效 | 配置中记录的实际文件名 |

同一种资源存在多个扩展名时，按当前资源策略选择第一个可用项。

## 兼容

导入会补齐旧主题缺少的默认字段，并保留无法映射的未知字段。非法布尔、超范围数值和未知枚举会产生警告或回退，不应依赖损坏字段实现隐藏功能。

主题只保存外观相关数据，不包含 Root 授权、App Profile 或模块列表。
