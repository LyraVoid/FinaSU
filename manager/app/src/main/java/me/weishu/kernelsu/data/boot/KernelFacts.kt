package me.weishu.kernelsu.data.boot

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class KernelFieldValue(
    val value: String,
    val source: String,
    val confidence: String,
)

@Immutable
data class KernelImageFacts(
    val role: String,
    val name: String,
    val path: String,
    val exists: Boolean,
    val readable: Boolean,
    val format: String,
    val headerVersion: Int?,
    val payloadSize: Long?,
    val sha256: String?,
    val avb: KernelAvbFacts?,
    val reason: String?,
)

@Immutable
data class KernelAvbFacts(
    val footerVersion: String,
    val originalImageSize: Long,
    val signatureVerified: Boolean,
)

@Immutable
data class KernelSlotFacts(
    val id: String,
    val current: Boolean,
    val exists: Boolean,
    val readable: Boolean,
    val confidence: String,
    val reason: String?,
    val images: List<KernelImageFacts>,
    val release: KernelFieldValue?,
    val version: KernelFieldValue?,
    val cmdline: List<KernelFieldValue>,
    val bootconfig: List<KernelFieldValue>,
    val missingFields: List<String>,
) {
    val kernelValuesAvailable: Boolean
        get() = !release?.value.isNullOrBlank() && !version?.value.isNullOrBlank()

    val sourceIdentity: String?
        get() = if (images.isEmpty()) null else images.joinToString(";") {
            "${it.name}:${it.sha256 ?: it.reason ?: "unknown"}"
        }
}

@Immutable
data class KernelSlotEvidence(
    val source: String,
    val slot: String?,
    val current: Boolean,
    val status: String,
    val detail: String?,
)

@Immutable
data class KernelFacts(
    val parser: String? = null,
    val currentSlot: String? = null,
    val conflicts: List<String> = emptyList(),
    val evidence: List<KernelSlotEvidence> = emptyList(),
    val slots: List<KernelSlotFacts> = emptyList(),
    val readAt: String? = null,
) {
    fun slot(id: String?): KernelSlotFacts? = slots.firstOrNull { it.id == id }

    companion object {
        fun parse(text: String): KernelFacts {
            val json = JSONObject(text)
            return KernelFacts(
                parser = json.stringOrNull("parser"),
                currentSlot = json.stringOrNull("current_slot"),
                conflicts = json.optJSONArray("conflicts").strings(),
                evidence = json.optJSONArray("slot_evidence").objects().map { item ->
                    KernelSlotEvidence(
                        source = item.getString("source"),
                        slot = item.stringOrNull("slot"),
                        current = item.optBoolean("current"),
                        status = item.getString("status"),
                        detail = item.stringOrNull("detail"),
                    )
                },
                slots = json.getJSONArray("slots").objects().map { item ->
                    val fields = item.getJSONObject("fields")
                    val images = item.getJSONArray("images").objects().map { image ->
                        KernelImageFacts(
                            role = image.getString("role"),
                            name = image.getString("name"),
                            path = image.getString("path"),
                            exists = image.getBoolean("exists"),
                            readable = image.getBoolean("readable"),
                            format = image.getString("format"),
                            headerVersion = image.intOrNull("header_version"),
                            payloadSize = image.longOrNull("payload_size"),
                            sha256 = image.stringOrNull("sha256"),
                            avb = image.optJSONObject("avb")?.let { avb ->
                                KernelAvbFacts(
                                    footerVersion = avb.getString("footer_version"),
                                    originalImageSize = avb.getLong("original_image_size"),
                                    signatureVerified = avb.getBoolean("signature_verified"),
                                )
                            },
                            reason = image.stringOrNull("reason"),
                        )
                    }
                    KernelSlotFacts(
                        id = item.getString("id"),
                        current = item.getBoolean("current"),
                        exists = item.getBoolean("exists"),
                        readable = item.getBoolean("readable"),
                        confidence = item.getString("confidence"),
                        reason = item.stringOrNull("reason"),
                        images = images,
                        release = fields.optJSONObject("release")?.fieldValue(),
                        version = fields.optJSONObject("version")?.fieldValue(),
                        cmdline = fields.optJSONArray("cmdline").fields(),
                        bootconfig = fields.optJSONArray("bootconfig").fields(),
                        missingFields = item.optJSONArray("missing_fields").strings(),
                    )
                },
                readAt = json.stringOrNull("read_at"),
            )
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.intOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.longOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun JSONObject.fieldValue() = KernelFieldValue(
    value = getString("value"),
    source = optString("source"),
    confidence = optString("confidence"),
)

private fun JSONArray?.fields(): List<KernelFieldValue> =
    objects().mapNotNull { item ->
        item.optJSONObject("field")?.fieldValue()
            ?: item.takeIf { it.has("value") }?.fieldValue()
    }

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return List(length()) { getJSONObject(it) }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { getString(it) }
}
