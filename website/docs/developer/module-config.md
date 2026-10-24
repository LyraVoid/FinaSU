---
title: 模块配置
createTime: 2026/10/24 00:00:00
permalink: /developer/module-config/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# 模块配置

模块配置存放在 `/data/adb/ksu/module_configs/<module-id>/`，包括持久配置 `persist.config` 与重启时清除的 `tmp.config`。两者都是带魔数、版本和长度字段的二进制文件，不应手工编辑。

## CLI

在模块脚本中，`KSU_MODULE` 指向当前模块，因此可以直接调用：

```sh
ksud module config get mode
ksud module config set mode fast
ksud module config set mode balanced --temp
ksud module config list
ksud module config delete mode
ksud module config clear --temp
```

不在模块环境时可以传 `--internal <name>`，实际 ID 为 `internal.<name>`。

## 优先级

读取时先加载 `persist.config`，再用 `tmp.config` 覆盖相同键。临时值在启动阶段清理。`set` 默认写入持久配置，`--temp` 写入临时配置。

## 限制

- 模块 ID 与配置键遵循 `^[a-zA-Z][a-zA-Z0-9._-]+$`。
- 单个键最大 256 字节，单个值最大 1 MiB，最多 32 项。
- 值按 UTF-8 保存；布尔解析接受大小写不敏感的 `true` 或 `1`。
- 写入使用临时文件替换，读取方不应在外部并发改写配置文件。
