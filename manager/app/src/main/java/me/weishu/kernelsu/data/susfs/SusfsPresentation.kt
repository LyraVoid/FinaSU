package me.weishu.kernelsu.data.susfs

import org.json.JSONObject

/** A receipt from FinaSU, not an inventory of kernel or third-party rules. */
data class SusfsApplyReport(
    val status: String,
    val applied: List<String>,
    val failures: Map<String, String>,
    val skipped: Map<String, String>,
    val deferred: List<String>,
    val pendingReboot: List<String>,
) {
    companion object {
        fun parse(text: String): SusfsApplyReport {
            val json = JSONObject(text)
            fun strings(key: String): List<String> = json.getJSONArray(key).let { array ->
                List(array.length()) { array.getString(it) }
            }
            val failures = json.getJSONObject("failures")
            val skipped = json.optJSONObject("skipped")
            return SusfsApplyReport(json.getString("status"), strings("applied"),
                failures.keys().asSequence().associateWith { failures.getString(it) },
                skipped?.let { values ->
                    values.keys().asSequence().associateWith { values.getString(it) }
                }.orEmpty(),
                json.optJSONArray("deferred")?.let { array ->
                    List(array.length()) { array.getString(it) }
                }.orEmpty(),
                strings("pending_reboot"))
        }
    }
}

object SusfsInputs {
    private fun sameJson(left: Any?, right: Any?): Boolean = when {
        left is JSONObject && right is JSONObject -> left.length() == right.length() &&
            left.keys().asSequence().all { right.has(it) && sameJson(left.get(it), right.get(it)) }
        left is org.json.JSONArray && right is org.json.JSONArray -> left.length() == right.length() &&
            (0 until left.length()).all { sameJson(left.get(it), right.get(it)) }
        else -> left == right
    }
    fun validPath(value: String): Boolean = value.startsWith("/") && value != "/" &&
        value.toByteArray(Charsets.UTF_8).size < 256 && !value.contains("//") &&
        value.none { it.isWhitespace() || it.isISOControl() } &&
        value.split('/').none { it == "." || it == ".." }

    fun validText(value: String, capacity: Int): Boolean = value.isNotBlank() &&
        value.toByteArray(Charsets.UTF_8).size < capacity && value.none(Char::isISOControl)

    fun validCmdline(value: String): Boolean = value.isNotBlank() &&
        value.toByteArray(Charsets.UTF_8).size < 8192 &&
        value.none { it.isISOControl() && it != '\n' && it != '\t' }

    /** Match the entry shown to the user, never an index from an obsolete screen snapshot. */
    fun replaceRule(config: JSONObject, original: String?, replacement: JSONObject?) {
        val rules = config.getJSONArray("rules")
        if (original == null) {
            requireNotNull(replacement)
            rules.put(replacement)
            return
        }
        val expected = JSONObject(original)
        val index = (0 until rules.length()).firstOrNull {
            val current = rules.getJSONObject(it)
            current.getString("kind") == expected.getString("kind") &&
                current.getString("path") == expected.getString("path")
        } ?: error("Rule changed elsewhere; refresh before editing")
        val current = rules.getJSONObject(index)
        require(sameJson(current, expected)) { "Rule changed elsewhere; refresh before editing" }
        if (replacement == null) rules.remove(index) else rules.put(index, replacement)
    }

    /** Preview only. The daemon remains the authoritative schema validator before any write. */
    fun preview(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= 128 * 1024) { "Configuration exceeds 128 KiB" }
        val json = JSONObject(text)
        require(json.getInt("schema") in 1..2) { "Unsupported schema" }
        json.getBoolean("automatic")
        json.getJSONObject("settings")
        require(json.getJSONArray("rules").length() <= 128) { "Too many rules" }
        val lkm = json.getJSONObject("lkm")
        lkm.getJSONArray("hidden_modules")
        lkm.getJSONArray("mount_prefixes")
        return json
    }
}
