---
title: 维护与危险操作
description: 设置首页的免责声明与卸载入口
createTime: 2026/10/24 00:00:00
permalink: /setting/maintenance/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 维护与危险操作

<Badge text="首页快捷入口" type="info" /> <Badge text="先备份" type="danger" />

设置首页的维护入口不是第九个设置分类。它目前包括免责声明，以及只在 LKM 模式下显示的“卸载”。

## 免责声明

免责声明说明 Root、刷写、第三方模块和安全策略变更可能造成系统更新异常、数据损失、保修状态变化或其他不可预期后果。

阅读并继续使用管理器，不等于已经完成安全准备。真正执行刷写前仍应：

- 保存原厂启动镜像。
- 备份模块与超级用户列表。
- 确认设备型号、系统版本和槽位。
- 准备可用的 `fastboot`、Recovery 或官方救援工具。

## 卸载

“卸载”只在 LKM 模式显示；越狱模式（延迟加载）下入口不可用。

当前对话框提供两个选项：

:::: field-group

::: field 永久卸载
@type `删除 Root`
@default `不可撤销`
进入刷写流程，完全卸载 KernelSU 系 Root 与模块管理数据。执行前先备份需要保留的模块和授权列表。
:::

::: field 恢复原厂镜像
@type `刷回启动分区`
@default `需要匹配镜像`
选择与当前设备、系统和槽位匹配的原厂启动镜像并刷回。该操作恢复启动分区，不等于清理所有 `/data` 管理数据。
:::

::::

::: danger 不要用“卸载”代替救砖

设备已经无法启动时，管理器里的卸载入口通常也无法使用。请直接按 [救砖](/guide/rescue-bootloop/) 进入 Bootloader 或 Recovery，恢复匹配的原厂镜像。

:::

## 操作前检查

:::: steps

1. 在“设置 → 备份”创建一次备份，并到“管理备份”确认条目存在。
2. 记录管理器版本、驱动运行方式、设备型号、Android 构建和 KMI。
3. 找出与当前系统完全匹配的原厂 `boot` / `init_boot` 镜像。
4. 确认电脑能识别设备，并知道当前 A/B 槽位。
5. 保持足够电量，执行期间不要拔线、断电或同时进行系统 OTA。

::::

::: warning 恢复原厂镜像前

原厂镜像必须来自当前设备型号与当前系统版本，不能使用相邻版本、其他地区固件或其他设备的镜像。A/B 设备还要确认目标槽位。

:::

## 相关页面

- [救砖](/guide/rescue-bootloop/)
- [更新 FinaSU](/guide/update/)
- [备份](/setting/backup/)
