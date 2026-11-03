package me.weishu.kernelsu.data.model

import java.util.Locale

/**
 * Picks the value for the system language out of a manifest's language map. Keys may be a full
 * tag (`zh-CN`), its underscore form (`zh_CN`) or the bare language (`zh`), tried in that order.
 */
fun Map<String, String>.pluginLocalizedOrNull(): String? {
    if (isEmpty()) return null
    val locale = Locale.getDefault()
    val language = locale.language
    val candidates = listOfNotNull(
        locale.toLanguageTag(),
        locale.country.takeIf { it.isNotBlank() }?.let { "${language}_$it" },
        language,
    )
    return candidates.firstNotNullOfOrNull { key -> this[key]?.takeIf { it.isNotBlank() } }
}
