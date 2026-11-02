package me.weishu.kernelsu.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.Plugin
import me.weishu.kernelsu.data.model.PluginConfigField
import me.weishu.kernelsu.data.model.PluginQuickAction
import me.weishu.kernelsu.ui.util.execKsudResult
import org.json.JSONArray
import org.json.JSONObject

/** Raised when the manager daemon reports that a plugin command failed. */
class PluginCommandException(val code: Int, message: String) : Exception(message)

class PluginRepositoryImpl : PluginRepository {

    override suspend fun getPlugins(): Result<List<Plugin>> = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray(run("plugin list"))
            (0 until array.length()).map { index -> array.getJSONObject(index).toPlugin() }
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                run("plugin ${if (enabled) "enable" else "disable"} ${quote(id)}")
            }.map { Unit }
        }

    override suspend fun install(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { run("plugin install ${quote(path)}") }.map { Unit }
    }

    override suspend fun uninstall(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { run("plugin uninstall ${quote(id)}") }.map { Unit }
    }

    override suspend fun getLog(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { run("plugin log ${quote(id)}") }
    }

    override suspend fun getConfig(id: String): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = JSONObject(run("plugin config --id ${quote(id)} list"))
                values.keys().asSequence().associateWith { key -> values.optString(key) }
            }
        }

    override suspend fun setConfig(id: String, key: String, value: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                run("plugin config --id ${quote(id)} set ${quote(key)} ${quote(value)}")
            }.map { Unit }
        }

    override suspend fun deleteConfig(id: String, key: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                run("plugin config --id ${quote(id)} delete ${quote(key)}")
            }.map { Unit }
        }

    override suspend fun run(id: String, callback: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { run("plugin run ${quote(id)} ${quote(callback)}") }.map { Unit }
        }

    private suspend fun run(arguments: String): String {
        val result = execKsudResult(arguments)
        if (!result.isSuccess) {
            val reason = result.err.joinToString("\n").trim()
            throw PluginCommandException(
                result.code,
                reason.ifBlank { "plugin command failed with code ${result.code}" },
            )
        }
        return result.out.joinToString("\n")
    }
}

/**
 * Quotes one argument so a package id can never reach the shell as a command
 * fragment of its own.
 */
private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

// 1. 修改 toPlugin() 的构造部分：
private fun JSONObject.toPlugin(): Plugin = Plugin(
    id = getString("id"),
    name = optString("name"),
    names = optJSONObject("names").toStringMap(),
    author = optString("author"),
    version = optString("version"),
    description = optString("description"),
    descriptions = optJSONObject("descriptions").toStringMap(), // 新增
    license = optString("license"),
    enabled = optBoolean("enabled"),
    entry = optString("entry"),
    quickAction = optJSONObject("quick_action")?.let {
        PluginQuickAction(
            function = it.optString("function"),
            label = it.optString("label"),
            labels = it.optJSONObject("labels").toStringMap(),    // 新增
        )
    },
    config = optJSONArray("config").toConfigFields(),
    depends = optJSONArray("depends").toStringList(),
    callbacks = optJSONArray("callbacks").toStringList(),
    has_manifest = optBoolean("has_manifest", true), // 新增
    has_action = optBoolean("has_action", false),    // 新增
    error = if (isNull("error")) null else optString("error").ifBlank { null },
)

// 2. 修改 toConfigFields() 的构造部分：
private fun JSONArray?.toConfigFields(): List<PluginConfigField> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        val field = getJSONObject(index)
        PluginConfigField(
            key = field.optString("key"),
            label = field.optString("label"),
            labels = field.optJSONObject("labels").toStringMap(), // 新增
            type = field.optString("type"),
            default = if (field.isNull("default")) null else field.opt("default")?.toString(),
            options = field.optJSONArray("options").toStringList(),
        )
    }
}


private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { index -> optString(index) }
}

// 3. 在文件最底部（类外面或里面均可）加上这个工具函数：
private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { key -> optString(key) }
        .filterValues { it.isNotBlank() }
}
