package me.weishu.kernelsu.data.susfs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.RootShell
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SusfsCapabilities(
    val hideMounts: Boolean = false,
    val logging: Boolean = false,
    val avcSpoof: Boolean = false,
    val uname: Boolean = false,
    val cmdline: Boolean = false,
    val path: Boolean = false,
    val map: Boolean = false,
    val dynamicKstat: Boolean = false,
    val fullClone: Boolean = false,
    val staticKstat: Boolean = false,
    val staticKstatFields: List<String> = emptyList(),
    val redirect: Boolean = false,
    val redirectUidSchemes: List<Int> = emptyList(),
    val hiddenModules: Boolean = false,
    val mountPrefixes: Boolean = false,
)

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
    val staticKstatSupported: Boolean = false,
    val capabilities: SusfsCapabilities = SusfsCapabilities(),
    val receipt: SusfsApplyReport? = null,
    val schema1BackupAvailable: Boolean = false,
    val unameApplied: Boolean = false,
    val unameScheduled: Boolean = false,
    val unameUncertain: Boolean = false,
    val error: String? = null,
) {
    val detected get() = protocol == "compatible" || protocol == "unknown_protocol"
    val writable get() = protocol == "compatible" && error == null && ownership == "builtin" && pendingOwner == null
    val canImport get() = protocol == "compatible" && ownership == "builtin" && pendingOwner == null
    fun supports(feature: String) = protocol == "compatible" && "CONFIG_KSU_SUSFS_$feature" in features
    fun proc(node: String) = protocol == "compatible" && implementation == "lkm" && node in procNodes

    companion object {
        fun parse(text: String): SusfsStatus {
            val json = JSONObject(text)
            val detection = json.getJSONObject("detection")
            val ownership = json.optJSONObject("ownership")
            val ownershipValid = ownership != null && ownership.has("mode") && ownership.has("pending") &&
                (ownership.isNull("pending") || ownership.optJSONObject("pending")?.optString("target") in setOf("builtin", "external"))
            fun strings(key: String): List<String> {
                val array = detection.getJSONArray(key)
                return List(array.length()) { array.getString(it) }
            }
            fun integers(array: org.json.JSONArray?) =
                array?.let { values -> List(values.length()) { values.getInt(it) } }.orEmpty()
            fun capabilityStrings(array: org.json.JSONArray?) =
                array?.let { values -> List(values.length()) { values.getString(it) } }.orEmpty()
            val features = strings("features")
            val procNodes = strings("proc_nodes")
            val implementation = detection.getString("implementation")
            val protocol = detection.getString("status")
            val legacyStatic = json.optBoolean("static_kstat_supported", false)
            val capability = json.optJSONObject("capabilities")
            val advertised = capabilityStrings(capability?.optJSONArray("supported")).toSet()
            fun enabled(name: String, legacy: Boolean) =
                if (capability == null) legacy else name in advertised
            val staticFields = capabilityStrings(capability?.optJSONArray("static_kstat_fields"))
                .ifEmpty { if (legacyStatic) listOf("size", "ctime_sec") else emptyList() }
            val redirectSchemes =
                integers(capability?.optJSONArray("redirect_uid_schemes")).filter { it in 0..2 }
            val redirect = enabled(
                "redirect",
                protocol == "compatible" && "CONFIG_KSU_SUSFS_OPEN_REDIRECT" in features,
            )
            val staticKstat = enabled("static_kstat", legacyStatic)
            return SusfsStatus(
                protocol = protocol,
                version = detection.getString("version"),
                implementation = implementation,
                variant = detection.getString("variant"),
                features = features, procNodes = procNodes,
                management = json.getString("management"), automatic = json.getBoolean("automatic"),
                ownership = if (ownershipValid) ownership.optString("mode") else "unknown",
                pendingOwner = if (ownershipValid) ownership.optJSONObject("pending")?.optString("target") else null,
                switchReady = ownershipValid && json.optBoolean("switch_ready", false),
                staticKstatSupported = staticKstat,
                capabilities = SusfsCapabilities(
                    hideMounts = enabled("hide_mounts",
                        protocol == "compatible" && "CONFIG_KSU_SUSFS_SUS_MOUNT" in features),
                    logging = enabled("logging",
                        protocol == "compatible" && "CONFIG_KSU_SUSFS_ENABLE_LOG" in features),
                    avcSpoof = enabled("avc_spoof",
                        protocol == "compatible" && (implementation != "lkm" ||
                            "susfs_avc_spoof" in procNodes)),
                    uname = enabled("uname",
                        protocol == "compatible" && "CONFIG_KSU_SUSFS_SPOOF_UNAME" in features),
                    cmdline = enabled("cmdline",
                        protocol == "compatible" &&
                            "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG" in features),
                    path = enabled("path",
                        protocol == "compatible" && "CONFIG_KSU_SUSFS_SUS_PATH" in features),
                    map = enabled("map",
                        protocol == "compatible" && "CONFIG_KSU_SUSFS_SUS_MAP" in features),
                    dynamicKstat = enabled("dynamic_kstat",
                        protocol == "compatible" && "CONFIG_KSU_SUSFS_SUS_KSTAT" in features),
                    fullClone = enabled("full_clone",
                        protocol == "compatible" &&
                            "CONFIG_KSU_SUSFS_SUS_KSTAT" in features &&
                            (implementation != "lkm" || "susfs_kstat" in procNodes)),
                    staticKstat = staticKstat,
                    staticKstatFields = staticFields,
                    redirect = redirect,
                    redirectUidSchemes = if (capability == null && redirect) listOf(0, 1, 2) else redirectSchemes,
                    hiddenModules = enabled("hidden_modules",
                        protocol == "compatible" && implementation == "lkm" &&
                            "susfs_hide_modules" in procNodes),
                    mountPrefixes = enabled("mount_prefixes",
                        protocol == "compatible" && implementation == "lkm" &&
                            "susfs_hide_mounts" in procNodes),
                ),
                receipt = json.optJSONObject("receipt")?.let { SusfsApplyReport.parse(it.toString()) },
                schema1BackupAvailable = json.optBoolean("schema1_backup_available", false),
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
    suspend fun restoreSchema1() { error("Schema 1 backup restore unavailable") }
}

/** Only trusted fixed subcommands; imported JSON is stdin, never executable shell text. */
class SusfsRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SusfsDataSource {
    private suspend fun command(action: String, input: String? = null): String = withContext(dispatcher) {
        require(action in setOf("status", "export", "import", "apply", "switch builtin", "switch external",
            "confirm-switch", "cancel-switch", "restore-schema1"))
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
    override suspend fun restoreSchema1() { command("restore-schema1") }
}
