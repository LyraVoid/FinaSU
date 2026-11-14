package me.weishu.kernelsu.wallpaper

import org.json.JSONObject

/**
 * A block of a theme package the user can import on its own.
 *
 * The granularity follows what a package actually owns: payload families (backgrounds, icon sets)
 * and the preference groups that travel with them. Assets are grouped through [themePartOf] rather
 * than each declaring a part, so the registry stays the single list of payloads.
 */
enum class ThemePart {
    MAIN_BACKGROUND,
    PAGE_BACKGROUNDS,
    SURFACES,
    FONT,
    HOME_LAYOUT,
    NAV_ICONS,
    MUSIC,
    SOUND,
    COLORS,
    INTERFACE,
    LANGUAGE,
}

/** Which parts of a prepared package are applied. Defaults to the whole package. */
class ThemeSelection(val parts: Set<ThemePart> = ThemePart.entries.toSet()) {
    operator fun contains(part: ThemePart): Boolean = part in parts
    val isEmpty: Boolean get() = parts.isEmpty()

    companion object {
        val All = ThemeSelection()
    }
}

private val mainBackgroundBases = setOf("background", "video_background", "title_image")

private val pageBackgroundBases = setOf(
    "background_home", "background_kernel", "background_superuser",
    "background_system_module", "background_settings",
)

internal fun themePartOf(base: String): ThemePart = when (base) {
    "font" -> ThemePart.FONT
    "home_layout" -> ThemePart.HOME_LAYOUT
    in mainBackgroundBases -> ThemePart.MAIN_BACKGROUND
    in pageBackgroundBases -> ThemePart.PAGE_BACKGROUNDS
    else -> ThemePart.SURFACES
}

/**
 * The parts a package can actually deliver.
 *
 * [assetBases] filters archive entries down to registered payloads first: media and icon files are
 * named by `theme.json` and would otherwise look like surface entries. Configuration-only parts are
 * read from their keys so a package with no image still offers them.
 */
internal fun themePartsIn(json: JSONObject, stems: Set<String>, assetBases: Set<String>): Set<ThemePart> = buildSet {
    stems.filter { it in assetBases }.forEach { add(themePartOf(it)) }
    if (json.has("isBackgroundEnabled") || json.has("backgroundOpacity") ||
        json.has("isVideoBackgroundEnabled") || json.has("isAdvancedTitleStyleEnabled")
    ) add(ThemePart.MAIN_BACKGROUND)
    if (json.has("isMultiBackgroundEnabled")) add(ThemePart.PAGE_BACKGROUNDS)
    if (json.has("fontMode") || json.has("isFontEnabled")) add(ThemePart.FONT)
    if (json.has("homeLayoutStyle")) add(ThemePart.HOME_LAYOUT)
    if (json.has("navIcons") || json.has("navIconCustomEnabled")) add(ThemePart.NAV_ICONS)
    if (json.has("isMusicEnabled") || json.has("musicFilename")) add(ThemePart.MUSIC)
    if (json.has("isSoundEffectEnabled") || json.has("soundEffectFilename")) add(ThemePart.SOUND)
    if (listOf(
            "customColor", ThemeSettingsMapping.KEY_COLOR, ThemeSettingsMapping.LEGACY_KEY_COLOR,
            "useSystemDynamicColor", "colorGenerationMode", "colorStandard", "colorStyle",
            "colorContrast",
        ).any(json::has)
    ) add(ThemePart.COLORS)
    if (listOf("nightModeEnabled", "nightModeFollowSys", "statsTopLayout", "isListWorkingCardModeHidden")
            .any(json::has)
    ) add(ThemePart.INTERFACE)
    if (json.has("appLanguage")) add(ThemePart.LANGUAGE)
}
