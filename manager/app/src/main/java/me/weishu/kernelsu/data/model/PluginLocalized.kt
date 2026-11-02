package me.weishu.kernelsu.data.model

import java.util.Locale

/**
 * 按当前系统语言从插件的多语言 map 中选出一个值。
 * 优先级：完整语言标签 (zh-CN) -> 基础语言代码 (zh)
 */
fun Map<String, String>.pluginLocalizedOrNull(): String? {
    if (isEmpty()) return null
    val locale = Locale.getDefault()
    val lang = locale.language // "zh", "ja", "en"

    this[locale.toLanguageTag()]?.takeIf { it.isNotBlank() }?.let { return it }
    this[lang]?.takeIf { it.isNotBlank() }?.let { return it }
    
    return null
}