---
title: 音效设置
icon: material-symbols:volume-up
description: FinaSU 触摸音效、启动音效、预置资源、反馈范围和主题导出边界
createTime: 2026/10/24 00:00:00
permalink: /theme/global/sound/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 音效设置

<Badge text="触摸与启动分开" type="tip" /> <Badge text="可本地或预置" type="info" /> <Badge text="启动音不随主题导出" type="warning" />

FinaSU 有两个独立音效功能：

- **触摸音效**：点击界面控件时播放，可选择所有触摸或仅导航控件；
- **启动音效**：打开管理器时播放，目前不随 `.fpt` 主题导出。

## 触摸音效

:::: table 触摸音效设置

| 设置 | 行为 |
| --- | --- |
| 总开关 | 关闭后不播放触摸音效 |
| 来源 | 本地文件或预置音效 |
| 本地文件 | 从 `audio/*` 选择器导入 |
| 预置音效 | `Zako`、`Zako2`、`Imoi`、`Ehe`、`Baka`、`Ciallo` |
| 反馈范围 | `global`（所有触摸）或 `bottom_bar`（导航控件） |
| 试听 | 使用当前设置播放一次 |

::::

主题 JSON 对应：

```json
{
  "isSoundEffectEnabled": true,
  "soundEffectFilename": "sound_effect_custom.wav",
  "soundEffectScope": "bottom_bar"
}
```

预置音效导出为：

```text
sound_effect_preset_<Preset>.wav
```

本地音效使用导入后的实际文件名。导入时先查找同名 ZIP 条目，再把它复制到管理器的音效目录，并把来源类型恢复为本地文件。

## 启动音效

启动音效有独立开关、本地/预置来源和试听按钮。当前预置为：

```text
Zako
Hentai
```

它不是 `.fpt` 的导出项，导入主题也不会覆盖启动音效。主题中的 `isSoundEffectEnabled` 与 `soundEffectScope` 只描述触摸音效。

:::: warning 不要混用两个开关

触摸音效关闭时，启动音效仍可单独开启；反之亦然。排查“打开应用没声音”时先检查启动音效，排查“点按钮没声音”时检查触摸音效和反馈范围。

::::

## 播放范围

`global` 表示响应用户的全局触摸反馈路径，不表示修改 Android 系统触摸声音，也不会影响其他应用。

`bottom_bar` 只覆盖导航控件，适合避免整页交互都出现声音。若当前操作不在导航范围内，选择该模式后没有声音是预期行为。

## 常见问题

::::: collapse accordion

:::: collapse 本地音效没有声音
检查文件能否被系统解码、总开关和反馈范围。清除后重新选择文件，避免继续使用损坏的旧文件。
::::

:::: collapse 主题导入了音效，但仍使用设备原有设置
启动音效和振动不进入主题。触摸音效还需要主题资源与 `soundEffectFilename` 完全匹配。
::::

:::: collapse 预置音效在别的设备不播放
预置资源会以 WAV 随主题导出，但最终仍取决于目标设备的媒体解码能力。
::::

:::: collapse 音量能调整吗
当前触摸音效没有独立音量滑块。它使用设备媒体音量与资源自身增益；需要更低的响度时，应换一个音量更合适的音频文件。
::::

:::::

## 相关页面

- [多媒体设置](/setting/multimedia/)：音效入口与操作。
- [背景音乐](/theme/global/music/)：独立音乐播放器。
- [震动触感](/theme/global/haptic/)：不属于音效的触觉反馈。
- [主题包结构](/developer/theme-package/)：音效资源命名与校验。
