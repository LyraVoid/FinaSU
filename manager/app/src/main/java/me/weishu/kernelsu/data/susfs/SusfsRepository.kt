package me.weishu.kernelsu.data.susfs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.RootShell
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SusfsStatus(
    val protocol: String = "probing",
    val version: String = "",
    val implementation: String = "unknown",
    val variant: String = "",
    val features: List<String> = emptyList(),
    val procNodes: List<String> = emptyList(),
    val management: String = "saved",
    val automatic: Boolean = false,
    val ownership: String = "unknown",
    val pendingOwner: String? = null,
    val switchReady: Boolean = false,
    val unameApplied: Boolean = false,
    val unameScheduled: Boolean = false,
    val unameUncertain: Boolean = false,
    val error: String? = null,
) {
    val detected get() = protocol == "compatible" || protocol == "unknown_protocol"
    val writable get() = protocol == "compatible" && error == null && ownership == "builtin" && pendingOwner == null
    fun supports(feature: String) = protocol == "compatible" && "CONFIG_KSU_SUSFS_$feature" in features
    fun proc(node: String) = protocol == "compatible" && implementation == "lkm" && node in procNodes

    companion object {
        fun parse(text: String): SusfsStatus {
            val json = JSONObject(text)
            val detection = json.getJSONObject("detection")
            val ownership = json.optJSONObject("ownership")
            fun strings(key: String): List<String> {
                val array = detection.getJSONArray(key)
                return List(array.length()) { array.getString(it) }
            }
            return SusfsStatus(
                protocol = detection.getString("status"),
                version = detection.getString("version"),
                implementation = detection.getString("implementation"),
                variant = detection.getString("variant"),
                features = strings("features"), procNodes = strings("proc_nodes"),
                management = json.getString("management"), automatic = json.getBoolean("automatic"),
                ownership = ownership?.optString("mode") ?: "unknown",
                pendingOwner = ownership?.optJSONObject("pending")?.optString("target"),
                switchReady = json.optBoolean("switch_ready", false),
                unameApplied = json.optBoolean("uname_applied", false),
                unameScheduled = json.optBoolean("uname_scheduled", false),
                unameUncertain = json.optBoolean("uname_uncertain", false),
                error = if (json.isNull("config_error")) null else json.getString("config_error"),
            )
        }
    }
}

interface SusfsDataSource {
    suspend fun status(): SusfsStatus
    suspend fun export(): String
    suspend fun save(text: String)
    suspend fun apply(): String
    suspend fun switchOwnership(target: String) { error("Ownership switching unavailable") }
    suspend fun confirmOwnership() { error("Ownership switching unavailable") }
    suspend fun cancelOwnership() { error("Ownership switching unavailable") }
}

/** Only trusted fixed subcommands; imported JSON is stdin, never executable shell text. */
class SusfsRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SusfsDataSource {
    private suspend fun command(action: String, input: String? = null): String = withContext(dispatcher) {
        require(action in setOf("status", "export", "import", "apply", "switch builtin", "switch external", "confirm-switch", "cancel-switch"))
        require(input == null || input.toByteArray().size <= 128 * 1024)
        val shell = withTimeout(15_000L) { RootShell.awaitRoot() }
        val path = ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so"
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val invocation = "${quote(path)} susfs $action"
        val job = shell.newJob().add(if (input == null) invocation else "printf '%s' ${quote(input)} | $invocation")
        // Bound the wait without killing the shared shell or an in-flight kernel operation.
        // A timed-out apply may still finish; the next status query is authoritative.
        val future = job.to(output, errors).enqueue()
        val result = try { future.get(15, TimeUnit.SECONDS) }
            finally { if (!future.isDone) future.cancel(false) }
        check(result.isSuccess) { errors.joinToString("\n").ifEmpty { "SUSFS command failed (${result.code})" } }
        output.joinToString("\n")
    }

    override suspend fun status() = SusfsStatus.parse(command("status"))
    override suspend fun export() = command("export")
    override suspend fun save(text: String) {
        command("import", text)
    }
    override suspend fun apply() = command("apply")
    override suspend fun switchOwnership(target: String) { command("switch $target") }
    override suspend fun confirmOwnership() { command("confirm-switch") }
    override suspend fun cancelOwnership() { command("cancel-switch") }
}
