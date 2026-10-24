---
title: Lua 插件接口
createTime: 2026/10/24 00:00:00
permalink: /developer/lua-plugin/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Lua 插件接口

`ksud` 内置 Lua 5.5 解释器。插件存放在 `/data/adb/plugins/<id>/`，入口脚本返回回调表。

## 最小结构

ZIP 根目录直接包含：

```text
hello-plugin.zip
├── plugin.json
└── main.lua
```

```json
{
  "id": "hello_plugin",
  "name": "Hello Plugin",
  "version": "1.0.0",
  "entry": "main.lua",
  "callbacks": ["service", "action"],
  "has_action": true
}
```

```lua
return {
  service = function()
    info("service started: " .. PLUGIN_ID)
  end,
  action = function()
    assert(write_file(PLUGIN_DIR .. "/hello.txt", "Hello FinaSU\n"))
  end,
}
```

## 生命周期

- `post_fs_data`、`service`、`boot_completed`：按依赖顺序参与自动调度。
- `main`：声明后作为常驻回调启动。
- `action`：由操作入口调用。
- 任意自定义回调：`/data/adb/ksud plugin run <id> <callback>`。

每次普通调用都会创建新 Lua 状态并重新加载入口；跨调用状态应写入配置或文件。

## API

| API | 用途 |
| --- | --- |
| `info` / `warn` | 写入 `last_output.log` |
| `getprop` / `setprop` | 读写系统属性 |
| `exec` | 直接启动程序并返回退出码、stdout、stderr |
| `read_file` / `write_file` | 读写文件 |
| `mkdir` / `rm` / `chmod` / `list_dir` | 目录与权限操作 |
| `sysctl` | 写入 `/proc/sys` 参数 |
| `json_encode` / `json_decode` | JSON 转换 |
| `get_config` / `set_config` | 插件持久配置 |
| `start_daemon` | 启动独立循环回调 |

全局变量 `PLUGIN_ID` 与 `PLUGIN_DIR` 指向当前插件。

## 安全

“安全标准库”不提供文件系统或权限沙箱。插件拥有 `ksud` 的运行权限，可以执行系统程序和读写文件。只安装可信插件，路径应基于 `PLUGIN_DIR` 构造。

## 限制

| 项目 | 限制 |
| --- | --- |
| 单次 Lua 状态内存 | 64 MiB |
| 普通 / 启动回调 | 300 / 120 秒 |
| 文件、JSON、单路输出 | 4 MiB |
| 配置键 / 值 | 256 字节 / 1 MiB |
| ZIP 条目 / 单文件 / 总量 | 2048 / 16 MiB / 64 MiB |
| 压缩比 | 200 |

## CLI

```sh
ksud plugin install /data/local/tmp/hello-plugin.zip
ksud plugin list
ksud plugin run hello_plugin service
ksud plugin action hello_plugin
ksud plugin log hello_plugin
ksud plugin config --id hello_plugin set greeting 'Hello'
ksud plugin disable hello_plugin
```
