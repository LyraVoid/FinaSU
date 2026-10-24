---
title: 背景高斯模糊
icon: material-symbols:blur-on
createTime: 2026/10/24 00:00:00
permalink: /theme/global/blur/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 背景高斯模糊

壁纸页面的“模糊”控制背景图片的模糊半径，范围为 0 到 64。数值越高，背景细节越少，卡片文字越容易保持对比度。

模糊与“表面不透明度”“遮罩”一起决定最终层次：

- 不透明度决定卡片遮住多少背景。
- 模糊降低背景纹理干扰。
- 遮罩压暗背景，改善浅色文字的对比度。

高分辨率壁纸和高模糊值会增加 GPU 负担。旧设备应根据滚动流畅度降低参数。

相关设置见 [壁纸](/setting/wallpaper/)。
