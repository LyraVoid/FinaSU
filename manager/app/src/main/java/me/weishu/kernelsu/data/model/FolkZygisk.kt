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
            val state = json.get("injection_state") as? String
                ?: error("Invalid FolkZygisk injection_state")
            check(state in setOf("unverified", "failed", "starting", "injected", "partial")) {
                "Unknown FolkZygisk injection_state"
            }
            val failures = json.get("startup_failures")
            check(failures is Int || failures is Long) { "Invalid FolkZygisk startup_failures" }
            val count = (failures as Number).toLong()
            check(count in 0..4294967295L) { "Invalid FolkZygisk startup_failures" }
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
            )
        }
    }
}
