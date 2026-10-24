---
title: /data/adb 存储结构
description: FinaSU、普通模块、Lua 插件、内核模块、Sui、日志与运行状态的真实路径
createTime: 2026/10/24 00:00:00
permalink: /manager/storage/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# /data/adb 存储结构

<Badge text="诊断参考" type="tip" /> <Badge text="不要随手删除" type="danger" />

FinaSU 与其他 Root 组件共享 `/data/adb`。这里的目录包含授权、模块、插件、日志和运行状态；删除一个看起来无关的标记文件，可能改变下一次启动行为。

::: warning 本页用于定位，不是手工编辑指南

除非对应页面明确要求，否则不要直接修改这些文件。先通过管理器操作，需要排查时只读取或复制。

:::

## 总览

```file-tree title="/data/adb 主要结构"
/data/adb/
├── ksud                         # 设备上的 ksud 可执行文件
├── modules/                     # 普通 Android 模块
│   └── <module-id>/
├── modules_update/              # 等待合并的模块更新
├── metamodule -> modules/<id>   # 当前元模块符号链接
├── plugins/                     # Lua 插件
│   └── <plugin-id>/
└── ksu/
    ├── .allowlist               # 内核 Root 授权与 App Profile
    ├── .dynamic_manager         # ksud 维护的动态管理器 JSON
    ├── dynamic_manager          # 内核持久化的动态管理器快照
    ├── .ksurc                   # ksud 运行配置
    ├── bin/
    │   └── ksud -> /data/adb/ksud
    ├── lib/
    │   └── libadbroot.so
    ├── log/                     # 管理器、SU 与挂载日志
    ├── lkm/                     # 管理器导入的 Linux .ko
    ├── module_configs/          # 模块持久/临时配置
    ├── plugin_locks/            # Lua 插件锁
    ├── plugin_stage/            # Lua 插件暂存
    ├── plugin_state/            # Lua 插件确认状态
    ├── profile/
    │   ├── templates/           # App Profile 模板
    │   └── selinux/             # App Profile SELinux 规则
    ├── sui/                     # Sui 状态与日志
    ├── workdir/                 # 内置挂载后端工作目录
    ├── mount_mode               # 下一轮挂载提供者配置
    ├── mount_runtime            # 本次启动挂载结果
    ├── nomount_runtime          # 本次启动 NoMount 结果
    ├── preinit_status           # 预初始化状态
    └── zygisk.json              # 内置 Zygisk 配置
```

实际目录会随运行方式、功能开关和内核支持增减；没有出现的文件通常表示该功能尚未创建状态，而不是安装损坏。

## 核心文件

:::: table FinaSU 自身

| 路径 | 作用 | 注意事项 |
| --- | --- | --- |
| `/data/adb/ksud` | 用户态命令行工具。 | 管理器和其他组件通过子命令调用；替换版本后可能造成 UAPI 不匹配。 |
| `/data/adb/ksu/bin/ksud` | 指向主 `ksud` 的兼容入口。 | 不要创建指向未知二进制的替换链接。 |
| `/data/adb/ksu/lib/libadbroot.so` | ADB Root 功能使用的库。 | 删除会影响 ADB Root，不会移除普通 Root。 |
| `/data/adb/ksu/.allowlist` | 内核维护的授权与 Profile 数据。 | 格式错误可能丢失授权或使策略失效。 |
| `/data/adb/ksu/.ksurc` | `ksud` 的运行配置。 | 不要用普通文本编辑器随意改写。 |

::::

## 动态管理器文件

动态管理器有两份不同用途的持久状态：

| 路径 | 维护者 | 内容 |
| --- | --- | --- |
| `/data/adb/ksu/.dynamic_manager` | `ksud` | JSON 格式的签名列表与已授权 UID。 |
| `/data/adb/ksu/dynamic_manager` | 内核 | 启动时恢复信任表所需的二进制快照。 |

两者不是可以互换的普通配置文件。修改任意一份都可能导致管理器身份、授权列表或启动恢复不一致。需要切换管理器时使用[动态管理器](/manager/dynamic-manager/)页面。

## 普通模块

普通模块位于 `/data/adb/modules/<module-id>/`。模块内常见文件：

:::: table 模块标记

| 文件 | 含义 |
| --- | --- |
| `module.prop` | 模块 ID、名称、版本、作者与描述。 |
| `post-fs-data.sh` | 早期启动脚本。 |
| `service.sh` | 服务阶段脚本。 |
| `boot-completed.sh` | 启动完成脚本。 |
| `action.sh` | 管理器“操作”按钮执行的手动脚本。 |
| `webroot/` | 模块 WebUI 文件。 |
| `disable` | 禁用标记；模块不参与后续启动。 |
| `remove` | 待删除标记；重启清理阶段处理后移除目录。 |
| `update` | 更新标记，表示模块目录来自待合并更新。 |
| `skip_mount` | 跳过文件注入，但模块脚本仍可运行。 |

::::

`modules_update/` 用于暂存更新，`metamodule` 是指向当前元模块目录的符号链接。不要手动删除 `remove` 标记后继续使用半更新目录，也不要在两个管理器中同时操作同一模块。

::: important `skip_mount` 不是停用

`skip_mount` 只让挂载后端跳过该模块的文件注入，模块脚本仍有资格运行；`disable` 则会阻止模块参与启动。当前普通模块卡片不把 `skip_mount` 作为常规开关展示。

:::

## 内核模块

`/data/adb/ksu/lkm/` 保存通过“内核模块”页面导入的 `.ko` 条目：

```file-tree title="受管内核模块"
/data/adb/ksu/lkm/
├── <managed-id>/
│   ├── module.ko
│   ├── config.json
│   ├── disable
│   ├── last_result.json
│   └── banner.png
├── .state/
└── .stage/
```

- `module.ko` 是实际文件；
- `config.json` 保存自动加载、参数和元数据；
- `disable` 是持久阻止加载标记；
- `last_result.json` 记录最近一次加载尝试；
- `.state/`、`.stage/` 与回滚目录由管理器维护。

这与 `/data/adb/modules/` 完全不同。不要因为两者都有“模块”二字就移动文件。

## Lua 插件

插件安装在 `/data/adb/plugins/<plugin-id>/`：

| 文件 | 作用 |
| --- | --- |
| `plugin.json` | 插件清单与回调声明。 |
| `main.lua` | 默认入口，也可能使用清单中的其他入口名。 |
| `config.json` | 管理器保存的插件配置。 |
| `last_output.log` | 追加保存的回调输出；超过上限后从空文件重新写入。 |
| `disable` | 停用标记。 |

`/data/adb/ksu/plugin_locks/`、`plugin_stage/` 和 `plugin_state/` 保存安装事务、进程锁与包确认状态，属于运行内部数据。

## App Profile 与 Sui

:::: table Profile 与 Sui

| 路径 | 内容 |
| --- | --- |
| `/data/adb/ksu/profile/templates/` | App Profile 模板。 |
| `/data/adb/ksu/profile/selinux/` | 应用 Root Profile 使用的 SELinux 规则。 |
| `/data/adb/modules/finasu-sui/` | Sui 载荷模块目录。 |
| `/data/adb/ksu/sui/` | Sui 状态、启动标记、管理器身份与守护进程日志。 |
| `/data/adb/ksu/sui.lock` | Sui 生命周期操作锁。 |

::::

不要手工删除 `finasu-sui` 目录来“关闭 Sui”；Sui 有独立启停、卸载和载荷管理流程，见 [Sui 使用指南](/builtin/sui/)。

## 日志

`/data/adb/ksu/log/` 可能包含：

- `sulog-YYYY-MM-DD*.log`：SU 日志；
- `mount.log`：模块挂载结果；
- `mount_capture.log`：启动未完成时的可选捕获；
- 其他内核与内置引擎诊断日志。

日志目录权限通常较严格。需要反馈时优先使用管理器导出功能，并先检查其中是否包含个人路径、包名或凭据。

## 挂载与内核状态

这些文件用于记录“当前启动实际发生了什么”和“下一次启动准备使用什么”：

| 路径 | 作用 |
| --- | --- |
| `mount_mode` | 下次启动的 Folk Mount 提供者选择。 |
| `mount_runtime` | 本次启动实际使用的挂载提供者与结果。 |
| `nomount_runtime` | 本次启动 NoMount 驱动与注入状态。 |
| `nomount_uids` | 需要隐藏模块文件的 UID。 |
| `mount_inflight` | 防止启动阶段未完成的操作在下次启动无条件重试。 |
| `workdir/` | 内置挂载后端工作目录。 |

::: danger 状态文件不能用来“强制生效”

修改 `mount_mode`、删除 `mount_inflight` 或手工伪造运行记录，会绕过安全保护，不能替代正确的设置和重启流程。

:::

## 备份与恢复原则

::: steps

1. 先使用管理器提供的备份与导出功能。
2. 需要手工复制时，停止相关写入并保留文件权限与 SELinux 上下文。
3. 不要复制整个 `/data/adb` 覆盖另一台设备。
4. 恢复前核对设备型号、系统版本、Root 部署方式与路径来源。
5. 恢复后重新检查授权、模块、插件、动态管理器和挂载状态。

:::

## 相关页面

- [备份与 WebDAV](/manager/backup/)：可恢复内容的实际行为。
- [模块管理](/manager/module/)：普通模块操作。
- [Lua 插件](/manager/plugin/)：插件目录与生命周期。
- [内核模块](/manager/kernel-module/)：`.ko` 状态与配置。
- [动态管理器](/manager/dynamic-manager/)：两份动态管理器状态的用途。
- [基础概念](/guide/information/)：模块系统与系统分区的背景知识。
