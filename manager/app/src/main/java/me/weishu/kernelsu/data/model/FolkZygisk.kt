package me.weishu.kernelsu.data.model

import org.json.JSONObject

/** Current injection and next-boot selection are deliberately separate. */
data class FolkZygiskStatus(
    val enabled: Boolean,
    val engineAvailable: Boolean,
    val enableBlockers: List<String>,
    val externalProviders: List<String>,
    val injectionState: String,
    val rebootRequired: Boolean?,
    val startupFailures: Long,
    val startupBlocked: Boolean,
    val startupFailureCode: String?,
    val startupFailureDetail: String?,
    val startupGeneration: Long,
    val runtime: RuntimeStatus? = null,
    val engineVersion: String? = null,
    val matchesAssets: Boolean? = null,
    val canInstall: Boolean = false,
    val pendingRestart: Boolean = false,
    val modules: List<FolkZygiskModule> = emptyList(),
) {
    /**
     * Whether anything is actually injecting through Zygisk, the same test the home card uses to
     * decide its Zygisk row exists: the built-in engine when it is enabled or already injecting, or
     * any external provider module.
     */
    fun providesImplementation(): Boolean =
        (enabled && (engineAvailable || injectionState in INJECTING_STATES)) || externalProviders.isNotEmpty()

    companion object {
        private val INJECTING_STATES = setOf("injected", "partial", "starting")

        fun fromJson(json: JSONObject): FolkZygiskStatus {
            check(json.get("schema_version") == 1) { "Unsupported FolkZygisk schema" }
            fun boolean(key: String): Boolean = json.get(key) as? Boolean
                ?: error("Invalid FolkZygisk $key")
            fun strings(key: String): List<String> {
                val array = json.getJSONArray(key)
                return List(array.length()) { index ->
                    array.get(index) as? String ?: error("Invalid FolkZygisk $key")
                }
            }
            fun nullableString(key: String): String? {
                if (!json.has(key) || json.isNull(key)) return null
                return (json.get(key) as? String)?.takeIf(String::isNotBlank)
            }
            val state = json.get("injection_state") as? String
                ?: error("Invalid FolkZygisk injection_state")
            check(state in setOf("unverified", "failed", "starting", "injected", "partial")) {
                "Unknown FolkZygisk injection_state"
            }
            val failures = json.get("startup_failures")
            check(failures is Int || failures is Long) { "Invalid FolkZygisk startup_failures" }
            val count = (failures as Number).toLong()
            check(count in 0..4294967295L) { "Invalid FolkZygisk startup_failures" }
            val runtime = if (json.isNull("runtime")) null else {
                val abis = json.getJSONObject("runtime").getJSONObject("abis")
                RuntimeStatus(listOf("64", "32").associateWith { bits ->
                    val abi = abis.getJSONObject(bits)
                    fun flag(key: String) = abi.get(key) as Boolean
                    AbiStatus(flag("supported"), flag("injected"), flag("daemon_running"),
                        abi.get("daemon_pid") as Int, abi.get("zygote_pid") as Int, abi.get("reason") as String)
                })
            }
            val engine = if (json.isNull("engine")) null else json.getJSONObject("engine")
            return FolkZygiskStatus(
                enabled = boolean("enabled"),
                engineAvailable = boolean("engine_available"),
                enableBlockers = strings("enable_blockers"),
                externalProviders = strings("external_providers"),
                injectionState = state,
                rebootRequired = if (json.get("reboot_required") == JSONObject.NULL) null
                    else boolean("reboot_required"),
                startupFailures = count,
                startupBlocked = boolean("startup_blocked"),
                startupFailureCode = nullableString("startup_failure_code"),
                startupFailureDetail = nullableString("startup_failure_detail"),
                startupGeneration = json.optLong("startup_generation", 0),
                modules = json.optJSONArray("modules")?.let { array ->
                    List(array.length()) { index ->
                        val module = array.getJSONObject(index)
                        val abis = module.getJSONArray("abis")
                        val id = module.getString("id")
                        FolkZygiskModule(id, module.optString("name").ifBlank { id },
                            List(abis.length()) { abis.getString(it) })
                    }
                }.orEmpty(),
                runtime = runtime,
                engineVersion = engine?.opt("installed_version") as? String,
                matchesAssets = engine?.opt("matches_embedded_assets") as? Boolean,
                canInstall = engine?.opt("can_install") == true,
            )
        }
    }
}

data class FolkZygiskModule(val id: String, val name: String, val abis: List<String>)

data class RuntimeStatus(val abis: Map<String, AbiStatus>)
data class AbiStatus(val supported: Boolean, val injected: Boolean, val daemonRunning: Boolean,
    val daemonPid: Int, val zygotePid: Int, val reason: String)
