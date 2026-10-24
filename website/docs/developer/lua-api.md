---
title: Lua API 参考
description: FinaSU 插件运行时 18 个全局函数、返回值、错误与资源限制
createTime: 2026/10/25 00:00:00
permalink: /developer/lua-api/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Lua API 参考

<Badge text="Lua 5.5" type="tip" /> <Badge text="64 MiB 内存" type="warning" /> <Badge text="Root 权限" type="danger" />

每次回调都在新的 Lua 状态中运行。宿主注册 2 个全局变量和 18 个函数；插件应只依赖这些接口，不要把 `io`、`os` 等原生标准库当作稳定兼容契约。

```lua
return {
  service = function()
    info("plugin=" .. PLUGIN_ID .. " dir=" .. PLUGIN_DIR)
    local result = exec("id")
    if not result.ok then
      warn("id failed: " .. result.code)
    end
  end,
}
```

## 全局变量

:::: table 只读上下文

| 名称 | 类型 | 含义 |
| --- | --- | --- |
| `PLUGIN_ID` | string | 当前插件 ID |
| `PLUGIN_DIR` | string | 当前插件安装目录的绝对路径 |

::::

代码、配置和状态文件应基于 `PLUGIN_DIR` 构造。数据需要跨回调保留时写入插件配置或明确管理的状态文件，因为每次调用都会重新创建 Lua 状态。

## 日志

### `info(message)` / `warn(message)`

```lua
info("service started")
warn("optional feature is unavailable")
```

两个函数都接收一个字符串，无返回值。消息写入插件的 `last_output.log`，换行会被替换为空格，使一条日志保持一行。

日志文件上限为 64 KiB。打开一个已超限日志时会先截断；不要把日志当作长期数据库。

## 属性

### `getprop(name)`

```lua
local sdk = getprop("ro.build.version.sdk")
```

读取系统属性并返回去除了尾部空白的字符串。属性名必须非空、不超过 256 字节且不能包含 NUL。

宿主调用 `/system/bin/getprop`。只有在命令无法启动等宿主错误时抛 Lua 错误；当前实现不检查 `getprop` 的退出状态，因此属性不存在时可能返回空字符串。

### `setprop(name, value)`

```lua
local ok = setprop("debug.example.enabled", "1")
```

属性名规则同 `getprop`；值不超过 256 字节且不能包含 NUL。成功返回 `true`，命令非零退出或启动后执行失败返回 `false`。

::: warning 属性不是通用配置存储

Android 属性有长度、命名和权限限制。插件自己的持久数据应使用 `get_config` / `set_config` 或 `PLUGIN_DIR` 内文件。

:::

## 命令执行

### `exec(program, ...)`

```lua
local result = exec("/system/bin/sh", "-c", "printf 'hello\\n'")

if result.ok then
  info(result.stdout)
else
  warn("exit=" .. result.code .. " stderr=" .. result.stderr)
end
```

直接启动程序，`program` 与参数合计最多 256 个，任何参数都不能包含 NUL。子进程的 stdin 为空。

返回值：

```lua
{
  ok = boolean,
  code = integer,
  stdout = string,
  stderr = string,
}
```

- `ok` 表示进程是否以退出码 0 结束。
- 普通退出时 `code` 是退出码。
- 被信号终止时 `ok=false`，`code=128+signal`。
- stdout 与 stderr 分别最多保留 4 MiB。
- 任一路输出超限时会追加 `[output truncated]` 并终止子进程。
- 程序无法启动时抛 Lua 错误，而不是返回失败表。

::: danger 不要拼接不可信 shell 命令

`exec` 不接受 shell 命令字符串；需要 shell 时应显式启动 `/system/bin/sh`。即使如此，也要把外部输入作为独立参数或严格转义，不能直接拼接。

:::

## 文件系统

### `read_file(path)`

```lua
local text = read_file(PLUGIN_DIR .. "/data/state.json")
```

读取最多 4 MiB 的内容。非 UTF-8 字节按 lossy 转换；文件不存在、读取失败或超过上限时抛 Lua 错误。

### `write_file(path, content)`

```lua
local ok = write_file(PLUGIN_DIR .. "/data/state.txt", "ready\n")
```

内容最多 4 MiB。父目录不存在时自动创建；成功写入后权限为 `0644` 并返回 `true`。普通写入失败返回 `false`；内容超限或参数类型错误抛 Lua 错误。

### `sysctl(name, value)`

```lua
local ok = sysctl("vm.swappiness", "60")
```

`name` 使用点分形式，例如 `kernel.panic_on_oops`。不能是绝对路径或以点开头，每个片段只允许 ASCII 字母、数字、`_`、`-`。值不超过 256 字节且不能包含 NUL。写入成功返回 `true`，否则返回 `false`。

实际路径固定为 `/proc/sys/<分段路径>`。

### `chmod(path, mode)`

```lua
local ok = chmod(PLUGIN_DIR .. "/bin/helper", "0755")
```

`mode` 可以是整数，也可以是八进制字符串。范围是 `0` 到 `07777`。Lua 没有八进制字面量，推荐始终用字符串写法。成功返回 `true`。

### `mkdir(path, recursive)`

```lua
mkdir(PLUGIN_DIR .. "/data/cache", true)
```

`recursive` 必须显式传布尔值。成功创建返回 `true`；路径已存在且是目录也返回 `true`；其他失败返回 `false`。

### `rm(path)`

```lua
local removed = rm(PLUGIN_DIR .. "/data/old.txt")
```

先尝试删除文件，再尝试删除空目录。它不会递归删除。成功返回 `true`，其他情况返回 `false`。

### `list_dir(path)`

```lua
local entries = list_dir(PLUGIN_DIR .. "/data")
for _, name in ipairs(entries) do
  info(name)
end
```

返回按名称排序的 1-based 数组。最多接受 10,000 项，超过时抛错；非 UTF-8 文件名会被跳过。

### `file_exists(path)`

```lua
if file_exists(PLUGIN_DIR .. "/disable-feature") then
  return
end
```

路径存在时返回 `true`，否则返回 `false`。

::: danger 这些函数没有目录沙箱

插件以 `ksud` 的权限运行。路径可以指向插件目录之外；API 的大小限制是资源边界，不是权限边界。只安装可信插件，并避免让网络输入直接决定高权限写入路径。

:::

## JSON

### `json_decode(text)`

```lua
local value = json_decode('{"enabled":true,"items":[1,2]}')
info(value.items[1])
```

接受最多 4 MiB 的 JSON。数组转换成 Lua 1-based table，对象转换成字符串键 table，JSON `null` 转换成 `nil`。解析失败时抛错。

### `json_encode(value)`

```lua
local text = json_encode({enabled = true, items = {"a", "b"}})
```

返回 JSON 字符串，最多 4 MiB，嵌套深度最多 32，节点最多 10,000。以下情况抛错：

- table 存在循环引用。
- 数字是 `NaN` 或无穷值。
- 对象键既不是字符串也不是整数。
- 键转换后重复。
- 值类型无法表示。

空 table 编码为 `{}`。只有键恰好是连续 `1..n` 的整数时才会编码为数组；稀疏 table 或混合键会编码成对象。

## 插件配置

### `get_config(key)`

```lua
local mode = get_config("mode")
if mode == "" then
  mode = "balanced"
end
```

键最多 64 字节且不能包含 `/` 或 `\`。未保存的键返回空字符串 `""`，不是 `nil`。配置文件损坏、超限或不是合法 JSON 对象时抛错。

`plugin.json` 中的 `config[].default` 只用于管理器展示初始值，不会由宿主自动注入 Lua。插件必须自行处理空值 fallback。

### `set_config(key, value)`

```lua
set_config("mode", "fast")
```

键规则同 `get_config`。值必须能转换为字符串并以 JSON 字符串保存。成功后返回 `true`；整个配置超过 4 MiB、文件损坏或写入失败时抛错。

配置实际保存在插件目录的 `config.json`，不会随 Lua 状态结束而消失。

## 常驻回调

### `start_daemon(callback, interval)`

```lua
return {
  service = function()
    local ok = start_daemon("tick", 5)
    if not ok then
      warn("tick daemon did not start")
    end
  end,

  tick = function()
    info("tick")
  end,
}
```

启动同一插件的指定回调作为独立进程：

- `callback` 必须匹配回调名规则，最多 64 字节。
- `interval` 小于 1 时按 1 秒处理。
- 成功加载并报告就绪时返回 `true`。
- 插件被禁用、启动失败或 10 秒内未就绪时返回 `false`。
- 同一插件、同一回调只允许一个 daemon。
- 每轮创建全新的 Lua 状态，轮次之间不会保留 Lua 全局变量。

宿主环境没有 Android 进程控制能力，`start_daemon` 固定返回 `false`。

::: warning daemon 回调自己负责及时返回

启动阶段派发的 daemon 轮次没有每轮 120 秒监督超时。回调必须快速完成并返回；需要长时间运行时，再显式启动并管理外部进程。

:::

## 错误处理速查

:::: table 失败方式

| API | 抛 Lua 错误 | 返回 `false` / 空值 |
| --- | --- | --- |
| `info` / `warn` | Lua 参数类型错误 | 无 |
| `getprop` | 参数非法、程序无法启动 | 不检查命令退出码 |
| `setprop` | 参数非法、程序无法启动 | 命令执行失败 |
| `exec` | 参数非法、程序无法启动、输出读取失败 | 进程非零或信号退出 |
| `read_file` | 参数错误、读取失败、超过 4 MiB | 无 |
| `write_file` | 内容超限、参数类型错误 | 普通写入失败 |
| `sysctl` | 名称或值非法 | 写入失败 |
| `chmod` | 模式非法 | 设置失败 |
| `mkdir` | 参数类型错误 | 创建失败 |
| `rm` | 参数类型错误 | 删除失败 |
| `list_dir` | 读取失败、超过 10,000 项 | 无 |
| `file_exists` | 参数类型错误 | 路径不存在 |
| `json_decode` | JSON 非法或超过 4 MiB | 无 |
| `json_encode` | 无法安全表示或超过限制 | 无 |
| `get_config` | 键非法、文件损坏或超限 | 未设置时返回 `""` |
| `set_config` | 键非法、写入失败或总量超限 | 无 |
| `start_daemon` | 参数类型错误 | 未就绪、禁用或宿主环境不支持 |

::::

::::: collapse accordion

:::: collapse 为什么 `pcall` 仍然必要
Lua 错误会终止当前回调。需要给出可读诊断或继续清理时，应把可能失败的调用包在 `pcall` 中，并记录错误。
::::

:::: collapse 为什么不能保存 Lua 函数跨轮次
每次回调都是全新状态。应保存字符串、JSON 或其他持久数据，再在下一轮重新构造函数和 table。
::::

:::: collapse 为什么输出会被截断
stdout 与 stderr 各限制 4 MiB，超限后会终止子进程并追加截断标记，避免插件耗尽内存或卡住宿主。
::::

:::: collapse 为什么配置值是字符串
管理器只保存 JSON 字符串。数字和布尔值应由插件显式解析；`get_config("count")` 得到的是字符串，不是 number。
::::

:::::

## 相关页面

- [Lua 插件包与生命周期](/developer/lua-plugin/)：清单、安装、回调、依赖和管理器行为。
- [模块配置](/developer/module-config/)：标准模块的二进制配置接口，与插件 `config.json` 不同。
