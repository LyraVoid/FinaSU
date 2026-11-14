package me.weishu.kernelsu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.media.TitleTextMode
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.bottombar.BottomBarIconConfig
import me.weishu.kernelsu.wallpaper.ThemeResourcePolicy
import me.weishu.kernelsu.wallpaper.ThemeSettingsMapping
import me.weishu.kernelsu.wallpaper.ThemePart
import me.weishu.kernelsu.wallpaper.ThemeSelection
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.ThemeSurfaceMapping
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import org.json.JSONObject
import java.io.File

/** The wall layer of a theme, resolved without touching the stored wallpaper settings. */
@Immutable
data class ThemePreviewWallpaper(
    val file: File,
    val opacity: Float,
    val blur: Float,
    val dim: Float,
)

/** The home title image and its layout values, resolved from the package without installing it. */
@Immutable
data class ThemePreviewVisualMedia(
    val titleImage: File?,
    val titleEnabled: Boolean,
    val titleDayOpacity: Float,
    val titleNightOpacity: Float,
    val titleDim: Float,
    val titleOffsetX: Float,
    val titleTextMode: TitleTextMode,
    val customTitleText: String,
)

/**
 * Everything a theme would change about the app's looks, read from a prepared package.
 *
 * Pages render normally; only the appearance inputs that would otherwise come from preferences or
 * stored files are overridden. Nothing is written while a preview is on screen.
 */
@Immutable
data class ThemePreviewSnapshot(
    val name: String,
    val appSettings: AppSettings,
    val fontFamily: FontFamily? = null,
    val homeLayout: String? = null,
    val wallpapers: List<ThemePreviewWallpaper?> = emptyList(),
    val videoBackground: File? = null,
    val surfaces: Map<SurfaceId, SurfaceConfig> = emptyMap(),
    val navIcons: Map<BottomBarDestination, File>? = null,
    val visualMedia: ThemePreviewVisualMedia? = null,
    val statsTopLayout: String? = null,
    val listWorkingCardModeHidden: Boolean? = null,
)

val LocalThemePreview = staticCompositionLocalOf<ThemePreviewSnapshot?> { null }
val LocalThemePreviewBack = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Builds the snapshot for a package, using the current settings as the base of every absent field. */
fun themePreviewSnapshot(
    name: String,
    json: JSONObject,
    files: Map<String, File>,
    current: AppSettings = ThemeController.getAppSettings(),
    selection: ThemeSelection = ThemeSelection.All,
): ThemePreviewSnapshot {
    val main = ThemeResourcePolicy.select("background", files)
    val mainSelected = ThemePart.MAIN_BACKGROUND in selection
    val pagesSelected = ThemePart.PAGE_BACKGROUNDS in selection
    val multi = if (pagesSelected || mainSelected) json.optBoolean("isMultiBackgroundEnabled", false) else WallpaperConfig.multiBackgroundEnabled
    val enabled = if (mainSelected) json.optBoolean("isBackgroundEnabled", false) else WallpaperConfig.enabled
    val fontFile = if (ThemePart.FONT in selection) ThemeResourcePolicy.select("font", files) else null
    val repository = me.weishu.kernelsu.data.repository.SettingsRepositoryImpl()

    return ThemePreviewSnapshot(
        name = name,
        appSettings = previewAppSettings(JSONObject().also { filtered ->
            json.keys().forEach { key ->
                val interfaceKey = key == "nightModeEnabled" || key == "nightModeFollowSys"
                if ((interfaceKey && ThemePart.INTERFACE in selection) || (!interfaceKey && ThemePart.COLORS in selection)) filtered.put(key, json.get(key))
            }
        }, current),
        fontFamily = fontFile?.let(FontConfig::fontFamilyFromFile),
        homeLayout = if (ThemePart.HOME_LAYOUT in selection) previewHomeLayout(json) else null,
        videoBackground = if (mainSelected && json.optBoolean("isVideoBackgroundEnabled", false)) {
            ThemeResourcePolicy.select("video_background", files)
        } else null,
        wallpapers = listOf("background_home", "background_kernel", "background_superuser", "background_system_module", "background_settings").mapIndexed { page, base ->
            val oldPath = if (multi) listOf(WallpaperConfig.homeBackgroundUri, WallpaperConfig.pluginBackgroundUri, WallpaperConfig.superuserBackgroundUri, WallpaperConfig.moduleBackgroundUri, WallpaperConfig.settingsBackgroundUri)[page] else WallpaperConfig.uri
            val oldFile = oldPath?.let { android.net.Uri.parse(it).path }?.let(::File)
            val file = if (multi && pagesSelected) ThemeResourcePolicy.select(base, files) else if (!multi && mainSelected) main ?: oldFile else oldFile
            file?.takeIf { enabled }?.let {
                ThemePreviewWallpaper(it,
                    if (mainSelected) json.optDouble("backgroundOpacity", 0.5).toFloat() else WallpaperConfig.opacity,
                    if (mainSelected) json.optDouble("backgroundBlur", 0.0).toFloat() else WallpaperConfig.blur,
                    if (mainSelected) json.optDouble("backgroundDim", 0.2).toFloat() else WallpaperConfig.dim)
            }
        },
        surfaces = if (ThemePart.SURFACES !in selection) emptyMap() else SurfaceRegistry.themeSlots().associate { descriptor ->
            val old = SurfaceStore.config(descriptor.id)
            val parsed = ThemeSurfaceMapping.read(descriptor, json)
            val file = ThemeResourcePolicy.select(descriptor.themeBase ?: descriptor.id.value, files)
            val wantsImage = ThemeSurfaceMapping.wantsImage(descriptor, json, parsed, file != null)
            val uri = if (wantsImage && file != null) android.net.Uri.fromFile(file).toString()
                else if (ThemeSurfaceMapping.presenceKey(descriptor) != null) null else old.imageUri
            descriptor.id to parsed.copy(imageUri = uri, enabled = if (descriptor.parentId != null) uri != null else parsed.enabled)
        },
        navIcons = if (ThemePart.NAV_ICONS in selection) previewNavIcons(json, files) else null,
        visualMedia = if (mainSelected) previewVisualMedia(json, files) else null,
        statsTopLayout = if (ThemePart.INTERFACE in selection) {
            json.optString("statsTopLayout").ifBlank { repository.statsTopLayout }
        } else null,
        listWorkingCardModeHidden = if (ThemePart.INTERFACE in selection) {
            if (json.has("isListWorkingCardModeHidden")) {
                json.optBoolean("isListWorkingCardModeHidden")
            } else repository.listWorkingCardModeHidden
        } else null,
    )
}

internal fun previewNavIcons(json: JSONObject, files: Map<String, File>): Map<BottomBarDestination, File> {
    val enabled = json.optBoolean("navIconCustomEnabled", false) ||
        json.optBoolean(BottomBarIconConfig.ENABLED_KEY, false)
    if (!enabled) return emptyMap()
    val icons = json.optJSONObject("navIcons") ?: return emptyMap()
    return BottomBarDestination.entries.mapNotNull { destination ->
        val entry = icons.optString(destination.themeKey)
        files[entry]?.let { destination to it }
    }.toMap()
}

internal fun previewVisualMedia(json: JSONObject, files: Map<String, File>) = ThemePreviewVisualMedia(
    titleImage = ThemeResourcePolicy.select("title_image", files),
    titleEnabled = json.optBoolean("isAdvancedTitleStyleEnabled", false),
    titleDayOpacity = json.optDouble("titleImageDayOpacity", 1.0).toFloat().coerceIn(0f, 1f),
    titleNightOpacity = json.optDouble("titleImageNightOpacity", 1.0).toFloat().coerceIn(0f, 1f),
    titleDim = json.optDouble("titleImageDim", 0.0).toFloat().coerceIn(0f, 1f),
    titleOffsetX = json.optDouble("titleImageOffsetX", 0.0).toFloat().coerceIn(-2f, 2f),
    titleTextMode = TitleTextMode.fromKey(json.optString("titleTextMode")),
    customTitleText = json.optString("titleCustomText"),
)

private fun previewAppSettings(json: JSONObject, current: AppSettings): AppSettings {
    val tokenColor = ThemeSettingsMapping.colors[json.optString("customColor")]
    val keyColor = when {
        json.has(ThemeSettingsMapping.KEY_COLOR) -> json.getInt(ThemeSettingsMapping.KEY_COLOR)
        json.has(ThemeSettingsMapping.LEGACY_KEY_COLOR) -> json.getInt(ThemeSettingsMapping.LEGACY_KEY_COLOR)
        tokenColor != null -> tokenColor
        else -> current.keyColor
    }
    val mode = if (json.has("nightModeEnabled") || json.has("nightModeFollowSys")) {
        when {
            json.optBoolean("nightModeFollowSys", current.colorMode.isSystem) -> ColorMode.SYSTEM
            json.optBoolean("nightModeEnabled", current.colorMode.isDark) -> ColorMode.DARK
            else -> ColorMode.LIGHT
        }
    } else current.colorMode
    val style = ThemeSettingsMapping.styles[json.optString("colorStyle")] ?: current.paletteStyle
    return current.copy(
        colorMode = mode,
        keyColor = keyColor,
        paletteStyle = style,
        colorSpec = when (json.optString("colorStandard")) {
            "M3E_2025" -> ColorSpec.SpecVersion.SPEC_2025
            "MD3_2021" -> ColorSpec.SpecVersion.SPEC_2021
            else -> current.colorSpec
        },
        useSystemDynamicColor = if (json.has("useSystemDynamicColor")) {
            json.getBoolean("useSystemDynamicColor")
        } else current.useSystemDynamicColor,
        colorGenerationMode = if (json.has("colorGenerationMode")) {
            json.getString("colorGenerationMode")
        } else current.colorGenerationMode,
        contrastLevel = when (json.optString("colorContrast")) {
            "MEDIUM" -> 0.5
            "HIGH" -> 1.0
            "STANDARD" -> 0.0
            else -> current.contrastLevel
        },
    )
}

private fun previewHomeLayout(json: JSONObject): String? = when (json.optString("homeLayoutStyle")) {
    HomeLayoutStyle.GRID, HomeLayoutStyle.FOCUS, HomeLayoutStyle.DASHBOARD, HomeLayoutStyle.STATS ->
        json.getString("homeLayoutStyle")
    HomeLayoutStyle.CIRCLE, "default", "sign" -> HomeLayoutStyle.CIRCLE
    else -> null
}
