package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Driver state reported by `ksud nomount status --json`.
 *
 * The backend distinguishes three states on purpose: a module that loaded but
 * does not answer must never be reported as ready.
 */
enum class NomountDriverState(val token: String) {
    ABSENT("absent"),
    OUTDATED("outdated"),
    READY("ready");

    companion object {
        fun fromToken(token: String?): NomountDriverState? =
            entries.firstOrNull { it.token == token }
    }
}

/** One module that contributes redirection rules. */
@Immutable
data class NomountModule(
    val id: String,
    val rules: Int,
)

/** Decoded `ksud nomount status --json` payload (`schema_version` 1 or 2). */
@Immutable
data class NomountStatus(
    val driverState: NomountDriverState,
    val driverVersion: String?,
    val apiOk: Boolean,
    val rulesExpected: Int,
    val rulesApplied: Int,
    /** Everything the kernel holds, including directories it created. */
    val kernelRules: Int,
    /** Modules that contribute rules, with their rule counts. */
    val modules: List<NomountModule>,
    /** UIDs the stored policy excludes from redirection. */
    val uids: List<Int>,
    /** UIDs the kernel excludes right now. */
    val uidsActive: List<Int>,
    /** Module id when an installed module owns injection instead of this backend. */
    val deferredTo: String?,
    /** True when the built-in backend is the one injecting right now. */
    val ownsInjection: Boolean,
    val dataVerified: String?,
    val dataError: String?,
    /** `builtin` when the kernel carries the driver, `module` for an LKM. */
    val driverKind: String?,
    /** Machine-readable reason the driver is unusable; see the manager strings. */
    val unavailableCode: String?,
    /** The KMI or version [unavailableCode] refers to. */
    val unavailableDetail: String?,
    val uidCount: Int,
    val isolatedBlocked: Boolean,
    val error: String?,
) {
    /** True when the driver answers and the requested rules are all in place. */
    val injected: Boolean
        get() = apiOk &&
            rulesApplied >= rulesExpected &&
            rulesExpected > 0 &&
            dataVerified != null &&
            dataError == null

    companion object {
        /** Newest schema this app understands; older schemas stay readable. */
        const val SCHEMA_VERSION = 2
        private const val OLDEST_SCHEMA_VERSION = 1

        /**
         * @throws IllegalStateException when the payload is not a NoMount status
         * this app understands; callers treat that as a read failure rather than
         * inventing a state.
         */
        fun fromJson(json: JSONObject): NomountStatus {
            val schema = json.optInt("schema_version", -1)
            check(schema in OLDEST_SCHEMA_VERSION..SCHEMA_VERSION) {
                "unsupported nomount schema_version $schema"
            }
            val state = NomountDriverState.fromToken(json.optStringOrNull("driver_state"))
                ?: error("nomount status has no valid driver_state")
            val modules = json.optJSONArray("modules")?.let { array ->
                List(array.length()) { index ->
                    val entry = array.optJSONObject(index) ?: error("invalid module entry")
                    NomountModule(
                        id = entry.optStringOrNull("id") ?: error("module entry without id"),
                        rules = entry.optInt("rules", 0),
                    )
                }
            }.orEmpty()
            return NomountStatus(
                driverState = state,
                driverVersion = json.optStringOrNull("driver_version"),
                apiOk = json.optBoolean("api_ok", false),
                rulesExpected = json.optInt("rules_expected", 0),
                rulesApplied = json.optInt("rules_applied", 0),
                kernelRules = json.optInt("kernel_rules", json.optInt("rules_applied", 0)),
                modules = modules,
                uids = json.optIntList("uids"),
                uidsActive = json.optIntList("uids_active"),
                deferredTo = json.optStringOrNull("deferred_to"),
                ownsInjection = json.optBoolean("owns_injection", false),
                dataVerified = json.optStringOrNull("data_verified"),
                dataError = json.optStringOrNull("data_error"),
                driverKind = json.optStringOrNull("driver_kind"),
                unavailableCode = json.optStringOrNull("unavailable_code"),
                unavailableDetail = json.optStringOrNull("unavailable_detail"),
                uidCount = json.optInt("uid_count", 0),
                isolatedBlocked = json.optBoolean("isolated_blocked", false),
                error = json.optStringOrNull("error"),
            )
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

private fun JSONObject.optIntList(key: String): List<Int> {
    val array = optJSONArray(key) ?: return emptyList()
    return List(array.length()) { index -> array.optInt(index, 0) }
}
