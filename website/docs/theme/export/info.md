---
title: 导出主题
icon: material-symbols:save-as-outline
description: FinaSU .fpt 的导出入口、元数据、资源范围、保存位置和限制
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

<Badge text="从当前状态快照" type="tip" /> <Badge text="写入 Downloads" type="info" /> <Badge text="不含授权与模块" type="danger" />

导出会把当前管理器里可主题化的资源和设置组合成一个 `.fpt` 文件。它不是设备整机备份，也不是模块安装包。

## 入口

打开“设置 → 外观 → 壁纸”，在“主题包”分组中选择“导出主题包”。管理器会先打开元数据编辑器。

:::: table 导出元数据

| 字段 | 必填 | 用途 |
| --- | --- | --- |
| 名称 | 是 | 导入预览、文件名和主题识别 |
| 设备类型 | 是 | `phone` 或 `tablet`，用于分类与预览 |
| 版本 | 否 | 内容版本字符串，默认 `1` |
| 作者 | 否 | 显示署名 |
| 描述 | 否 | 说明主题内容与限制 |

::::

名称只校验非空。生成文件名时会替换不适合文件名的字符；主题内部仍保存原始名称。

## 实际导出内容

::::: table 会随主题导出的内容

| 类别 | 资源或字段 | 导出条件 |
| --- | --- | --- |
| 主壁纸 | `background.<图片扩展名>`、透明度、模糊、暗度、分页开关 | 当前保存的壁纸资源存在时 |
| 分页壁纸 | `background_home.*`、`background_kernel.*`、`background_superuser.*`、`background_system_module.*`、`background_settings.*` | 多背景开启且对应页面有资源时 |
| 视频背景 | `video_background.*`、视频开关与音量 | 视频文件存在时 |
| 高级标题 | `title_image.*`、显隐、透明度、暗度、偏移、文字模式 | 标题资源存在时 |
| Grid 工作卡 | `grid_working_card_background.*`、surface 字段与显隐标志 | surface 文件或配置存在时，即使当前首页不是 Grid/Stats |
| Focus 卡 | 四张 `focus_card_*_bg.*` 与共享父级字段 | surface 文件或配置存在时 |
| Dashboard | `dashboard_card_bg.*` 与 Hero surface 字段 | surface 文件或配置存在时 |
| 字体 | `font.*` 与字体模式 | 自定义字体启用且文件存在时 |
| 导航图标 | `nav_icon_<页面>.png` 与 `navIcons` | 自定义导航图标启用且文件存在时 |
| 音乐 | 当前音乐实际文件名与播放字段 | 音乐启用且文件存在时 |
| 音效 | 本地音效实际文件名或 `sound_effect_preset_<Preset>.wav` | 触摸音效启用且资源存在时 |
| 设置 | 颜色、布局、Stats 顶部、状态标签、语言等 | 当前 FinaSU 主题字段 |

:::::

导出还会合并 `theme_passthrough/theme.json` 中保留的未知字段，以及不属于当前管理器资源映射的保留资源。这样可以尽量支持再次导入或其他兼容实现。surface 导出按注册表遍历，不会只因为当前首页是 CircleUI 就丢弃以前保存的 Grid/Focus/Dashboard 资源。

## 不会导出的内容

- Root 授权、allowlist 和 App Profile；
- 已安装模块、模块启用状态和模块列表；
- 内核、SUSFS、Folk Mount、NoMount 或 SuperCall 配置；
- App 锁、管理器认证、WebDAV 密码和备份密钥；
- 启动音效；
- 振动开关、强度和反馈范围；
- 模块横幅、模块卡片等服务端或非主题资源；
- 任意可执行脚本与目录结构。

:::: warning 启动音和振动不是主题资源

主题导入说明中明确写着它们仍属于设备设置。不要为了让主题携带启动音或振动而手工扩展字段，当前导出流程不会生成这类资源。

::::

## 保存位置

管理器使用 `MediaStore.Downloads` 写入公共下载集合：

```text
Download/FinaSU/Themes/<主题名称>.fpt
```

默认基础名为 `wallpaper.fpt`，但正常导出对话框会使用主题名生成文件名。选择 `application/octet-stream` 是为了避免部分下载提供器把未知 `.fpt` 后缀改成 `.bin`。

如果系统没有可写的下载集合、存储空间不足或写入被中断，导出会失败，并删除已经创建的 pending 文件。

## 打包过程

::::: steps

1. 读取当前主题资源和保留字段。
2. 生成当前版本的受支持字段，写入元数据。
3. 选择每个资源的当前文件，并检查条目名冲突。
4. 对将要写入的 JSON 再次执行校验。
5. 生成 16 字节随机 IV，以 AES-CBC/PKCS5 包装平铺 ZIP。
6. 通过临时 `IS_PENDING` 下载条目写完后发布为可见文件。

:::::

:::: table 导出硬限制

| 限制 | 数值 |
| --- | --- |
| 条目数 | 资源条目必须少于 128 个，JSON 另计 |
| `theme.json` | 不超过 1 MiB |
| 解压后总预算 | 不超过 1 GiB |
| 密文容器预算 | 解密上限 1 GiB + 32 MiB ZIP 开销 |
| 空资源 | 不允许 |

::::

这些是容器边界，不是性能建议。包含长视频、多张高分辨率壁纸和完整字体时，即使没有达到上限，也可能不适合低存储设备分发。

## 导出失败怎样排查

::::: collapse accordion

:::: collapse 提示名称为空
名称是唯一必填元数据。填入非空名称后再导出。
::::

:::: collapse 文件没有出现在预期目录
部分系统的下载目录需要等待媒体库刷新。先确认系统“下载”中存在 `FinaSU/Themes`，再检查存储权限和剩余空间。
::::

:::: collapse 文件后缀变成 `.bin`
这只应出现在其他 SAF 写入流程。当前 FinaSU 导出到 Downloads 会显式保留 `.fpt` 名称；若仍被改写，应保存后手动恢复扩展名再验证。
::::

:::: collapse 导出后体积异常大
检查是否启用了视频、GIF、高分辨率壁纸、多个分页背景或体积较大的字体。主题包会把当前实际文件一并写入。
::::

:::: collapse 导入后少了一项设置
先确认该设置是否属于主题协议。启动音、振动、Root 权限和模块状态本来就不会进入 `.fpt`。
::::

:::::

## 相关页面

- [分享主题](/theme/export/share/)：导出后的分发与接收。
- [成为主题开发者](/theme/theme-dev/)：修改字段和资源。
- [主题包结构](/developer/theme-package/)：容器安全、字段与调试。
- [壁纸](/setting/wallpaper/)：主题包与壁纸设置入口。
