package me.weishu.kernelsu.data.model

/**
 * Overall layout variant of the main pages.
 *
 * [DEFAULT] keeps the historical look: compact top bars everywhere.
 * [M3E] switches the main pages to Material 3 Expressive large titles, with a
 * collapsible inline search bar on the pages that own a search.
 */
object LayoutVariant {
    const val DEFAULT = "default"
    const val M3E = "m3e"

    fun fromValue(value: String?): String = when (value) {
        M3E -> M3E
        else -> DEFAULT
    }

    val supported: List<String> = listOf(DEFAULT, M3E)
}
