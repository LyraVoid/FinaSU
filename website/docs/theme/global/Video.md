---
title: 视频背景
icon: material-symbols:movie
createTime: 2026/10/24 00:00:00
permalink: /theme/global/video/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 视频背景

视频背景在管理器界面底层循环播放，并受壁纸主开关与遮罩控制。

## 支持

主题资源策略接受 `.mp4`、`.webm` 和 `.mkv`。具体解码能力取决于设备系统播放器。视频会裁切填充界面，并可按配置控制音量。

## 性能

高分辨率、高帧率或长视频会持续占用解码器与 GPU。移动设备建议选择短循环、适度分辨率的素材。

## 主题导出

启用视频背景后，视频资源会以 `video_background` 为基础名写入 `.fpt`，因此主题包体积会明显增大。

背景、遮罩与性能设置见 [壁纸](/setting/wallpaper/)。
