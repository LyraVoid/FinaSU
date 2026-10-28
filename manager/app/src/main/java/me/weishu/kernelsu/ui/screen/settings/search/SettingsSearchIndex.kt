package me.weishu.kernelsu.ui.screen.settings.search

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.automirrored.rounded.LabelOff
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.data.security.SecurityTarget
import me.weishu.kernelsu.media.VisualMediaConfig
import me.weishu.kernelsu.modulebanner.ModuleBannerConfig
import me.weishu.kernelsu.ui.component.settings.SettingsCategory
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState

/** Where a search result lands when its row is tapped. */
sealed interface SettingsSearchTarget {
    /** A control that lives on one of the category screens. */
    data class Category(val key: String) : SettingsSearchTarget

    /** A page of its own, opened directly. */
    data class Page(val route: Route) : SettingsSearchTarget

    /** An action the settings hub owns: a dialog, a sheet or an external link. */
    data class Hub(val action: SettingsHubAction) : SettingsSearchTarget
}

/** The rows the hub itself owns, which have no page of their own to push. */
enum class SettingsHubAction { BUG_FEEDBACK, DISCLAIMER, SEND_LOG, UNINSTALL }

/**
 * One searchable settings row.
 *
 * The index is the single source of truth for what the search can find, so a row added to a
 * category screen has to be registered here too. [titleRes] and [summaryRes] point at the very
 * strings the category screen draws, [icon] is the same icon, and [available] mirrors the
 * visibility condition the screen applies, so the search never offers a row the device cannot show.
 */
data class SettingsSearchEntry(
    val id: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val target: SettingsSearchTarget,
    val category: SettingsCategory? = null,
    @StringRes val summaryRes: Int = 0,
    @StringRes val parentTitleRes: Int = category?.titleRes ?: R.string.settings,
    val aliases: List<String> = emptyList(),
    val available: (SettingsUiState) -> Boolean = { true },
    /**
     * A row that only exists while the floating navigation bar is on screen with this form. The
     * form lives outside the settings state, so it is declared here and resolved by the caller
     * rather than read from inside the predicate.
     */
    val requiresFloatingBar: Boolean = false,
    val requiresFloatingGlass: Boolean = false,
)

/** A row on a category screen: tapping it opens that category. */
private fun SettingsCategory.row(
    id: String,
    @StringRes titleRes: Int,
    icon: ImageVector,
    @StringRes summaryRes: Int = 0,
    aliases: List<String> = emptyList(),
    available: (SettingsUiState) -> Boolean = { true },
    requiresFloatingBar: Boolean = false,
    requiresFloatingGlass: Boolean = false,
) = SettingsSearchEntry(
    id = id,
    titleRes = titleRes,
    icon = icon,
    target = SettingsSearchTarget.Category(key),
    category = this,
    summaryRes = summaryRes,
    aliases = aliases,
    available = available,
    requiresFloatingBar = requiresFloatingBar,
    requiresFloatingGlass = requiresFloatingGlass,
)

/** A row that opens a page of its own, filed under the category that links to it. */
private fun SettingsCategory.page(
    id: String,
    @StringRes titleRes: Int,
    icon: ImageVector,
    route: Route,
    @StringRes summaryRes: Int = 0,
    aliases: List<String> = emptyList(),
    available: (SettingsUiState) -> Boolean = { true },
) = SettingsSearchEntry(
    id = id,
    titleRes = titleRes,
    icon = icon,
    target = SettingsSearchTarget.Page(route),
    category = this,
    summaryRes = summaryRes,
    aliases = aliases,
    available = available,
)

/** A row the hub draws itself, so it is filed under the settings root. */
private fun hubRow(
    id: String,
    @StringRes titleRes: Int,
    icon: ImageVector,
    target: SettingsSearchTarget,
    @StringRes summaryRes: Int = 0,
    aliases: List<String> = emptyList(),
    available: (SettingsUiState) -> Boolean = { true },
) = SettingsSearchEntry(
    id = id,
    titleRes = titleRes,
    icon = icon,
    target = target,
    summaryRes = summaryRes,
    aliases = aliases,
    available = available,
)

/**
 * Every row the settings search can find, in the order the pages present them.
 *
 * Ordered by category so the result groups read the same way as the hub, and within a category so
 * equally scored rows keep the page's own order.
 */
val settingsSearchIndex: List<SettingsSearchEntry> = buildList {
    // General
    add(
        SettingsCategory.GENERAL.row(
            id = "general.check_update",
            titleRes = R.string.settings_check_update,
            summaryRes = R.string.settings_check_update_summary,
            icon = Icons.Filled.SystemUpdate,
            aliases = listOf("update", "ota", "release"),
        )
    )
    add(
        SettingsCategory.GENERAL.row(
            id = "general.check_module_update",
            titleRes = R.string.settings_module_check_update,
            summaryRes = R.string.settings_check_update_summary,
            icon = Icons.Filled.SystemUpdateAlt,
            aliases = listOf("update", "module", "repo"),
        )
    )
    add(
        SettingsCategory.GENERAL.page(
            id = "general.profile_template",
            titleRes = R.string.settings_profile_template,
            summaryRes = R.string.settings_profile_template_summary,
            icon = Icons.Filled.Description,
            route = Route.AppProfileTemplate,
            aliases = listOf("profile", "template", "per app"),
        )
    )
    add(
        SettingsCategory.GENERAL.page(
            id = "general.language",
            titleRes = R.string.settings_app_language,
            summaryRes = R.string.settings_app_language_summary,
            icon = Icons.Filled.Translate,
            route = Route.Language,
            aliases = listOf("language", "locale", "english", "chinese"),
        )
    )

    // Appearance
    add(
        SettingsCategory.APPEARANCE.page(
            id = "appearance.theme",
            titleRes = R.string.settings_theme,
            summaryRes = R.string.settings_theme_summary,
            icon = Icons.Filled.Palette,
            route = Route.ColorPalette,
            aliases = listOf("color", "colour", "palette", "monet", "dynamic color", "amoled", "dark"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.page(
            id = "appearance.wallpaper",
            titleRes = R.string.wallpaper_title,
            summaryRes = R.string.settings_wallpaper_summary,
            icon = Icons.Filled.Wallpaper,
            route = Route.Wallpaper,
            aliases = listOf("wallpaper", "background", "video"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.page(
            id = "appearance.font",
            titleRes = R.string.font_title,
            summaryRes = R.string.settings_font_summary,
            icon = Icons.Filled.FontDownload,
            route = Route.Font,
            aliases = listOf("font", "typeface", "typography"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.page(
            id = "appearance.theme_store",
            titleRes = R.string.theme_store_title,
            summaryRes = R.string.theme_store_subtitle,
            icon = Icons.Filled.ShoppingBag,
            route = Route.ThemeStore,
            aliases = listOf("theme", "store", "fpt", "import"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.theme_store_mode",
            titleRes = R.string.settings_theme_store_mode,
            summaryRes = R.string.settings_theme_store_mode_summary,
            icon = Icons.Filled.Language,
            aliases = listOf("browser", "compatibility", "compat", "webview"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.launcher_icon",
            titleRes = R.string.settings_launcher_icon,
            icon = Icons.Filled.Apps,
            aliases = listOf("launcher", "icon", "logo"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.home_layout",
            titleRes = R.string.settings_home_layout,
            summaryRes = R.string.settings_home_layout_summary,
            icon = Icons.Filled.GridView,
            aliases = listOf("home", "layout", "grid", "dashboard", "circle", "focus"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.stats_top_layout",
            titleRes = R.string.settings_stats_top_layout,
            icon = Icons.Rounded.SpaceDashboard,
            aliases = listOf("stats", "top", "layout", "list", "grid"),
            available = { it.homeLayoutStyle == HomeLayoutStyle.STATS },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.hide_status_tag",
            titleRes = R.string.settings_home_hide_status_tag,
            summaryRes = R.string.settings_home_hide_status_tag_summary,
            icon = Icons.AutoMirrored.Rounded.LabelOff,
            aliases = listOf("status", "tag", "badge", "mode", "hide"),
            available = {
                it.homeLayoutStyle == HomeLayoutStyle.CIRCLE ||
                    (it.homeLayoutStyle == HomeLayoutStyle.STATS &&
                        it.statsTopLayout != "grid")
            },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.layout_variant",
            titleRes = R.string.settings_layout_variant,
            summaryRes = R.string.settings_layout_variant_summary,
            icon = Icons.Rounded.SpaceDashboard,
            aliases = listOf("m3e", "expressive", "large title", "material 3", "traditional", "classic"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.support_links",
            titleRes = R.string.settings_support_links,
            summaryRes = R.string.settings_support_links_summary,
            icon = Icons.Filled.Info,
            aliases = listOf("support", "links", "card"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.support_links_title",
            titleRes = R.string.settings_support_links_custom_title,
            summaryRes = R.string.settings_support_links_custom_title_summary,
            icon = Icons.Rounded.TextFields,
            aliases = listOf("support", "title", "custom"),
            available = { it.supportLinksStyle == SupportLinksStyle.CUSTOM },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.support_links_subtitle",
            titleRes = R.string.settings_support_links_custom_subtitle,
            summaryRes = R.string.settings_support_links_custom_subtitle_summary,
            icon = Icons.AutoMirrored.Rounded.Notes,
            aliases = listOf("support", "subtitle", "custom"),
            available = { it.supportLinksStyle == SupportLinksStyle.CUSTOM },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.support_links_url",
            titleRes = R.string.settings_support_links_custom_url,
            summaryRes = R.string.settings_support_links_custom_url_summary,
            icon = Icons.Rounded.Link,
            aliases = listOf("support", "url", "link", "custom"),
            available = { it.supportLinksStyle == SupportLinksStyle.CUSTOM },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.title_text",
            titleRes = R.string.settings_home_title,
            summaryRes = R.string.settings_home_title_summary,
            icon = Icons.Rounded.TextFields,
            aliases = listOf("title", "text", "name", "header"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.title_image",
            titleRes = R.string.media_title_image,
            summaryRes = R.string.media_title_image_summary,
            icon = Icons.Rounded.Image,
            aliases = listOf("title", "image", "header", "photo"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.title_offset",
            titleRes = R.string.media_title_offset,
            icon = Icons.Rounded.SwapHoriz,
            aliases = listOf("title", "offset", "position"),
            available = { VisualMediaConfig.titleEnabled },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.hide_home_fingerprint",
            titleRes = R.string.settings_hide_home_fingerprint,
            summaryRes = R.string.settings_hide_home_fingerprint_summary,
            icon = Icons.Filled.Fingerprint,
            aliases = listOf("home", "fingerprint", "hide", "card"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.hide_home_zygisk",
            titleRes = R.string.settings_hide_home_zygisk,
            summaryRes = R.string.settings_hide_home_zygisk_summary,
            icon = Icons.Filled.Vaccines,
            aliases = listOf("home", "zygisk", "hide", "card"),
            available = { it.folkZygiskStatus?.providesImplementation() == true },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.hide_home_susfs",
            titleRes = R.string.settings_hide_home_susfs,
            summaryRes = R.string.settings_hide_home_susfs_summary,
            icon = Icons.Rounded.VisibilityOff,
            aliases = listOf("home", "susfs", "hide", "card"),
            available = { it.susfsDetected },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.hide_home_mount",
            titleRes = R.string.settings_hide_home_mount,
            summaryRes = R.string.settings_hide_home_mount_summary,
            icon = Icons.Filled.Layers,
            aliases = listOf("home", "mount", "metamodule", "vfs", "hide", "card"),
            available = { it.folkMountStatus != null && !it.folkMountReadError },
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.page_scale",
            titleRes = R.string.settings_page_scale,
            summaryRes = R.string.settings_page_scale_summary,
            icon = Icons.Rounded.AspectRatio,
            aliases = listOf("scale", "density", "size", "zoom"),
        )
    )
    add(
        SettingsCategory.APPEARANCE.row(
            id = "appearance.module_description_lines",
            titleRes = R.string.settings_module_description_max_lines,
            summaryRes = R.string.settings_module_description_max_lines_summary,
            icon = Icons.Rounded.Description,
            aliases = listOf("module", "description", "lines", "collapse"),
        )
    )

    // Behavior
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.navigation_badge",
            titleRes = R.string.settings_navigation_badge,
            summaryRes = R.string.settings_navigation_badge_summary,
            icon = Icons.Rounded.Pin,
            aliases = listOf("navigation", "badge", "dot"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.predictive_back",
            titleRes = R.string.settings_enable_predictive_back,
            summaryRes = R.string.settings_enable_predictive_back_summary,
            icon = Icons.AutoMirrored.Rounded.MenuOpen,
            aliases = listOf("predictive back", "gesture", "back"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.nav_custom_icons",
            titleRes = R.string.settings_nav_custom_icons,
            summaryRes = R.string.settings_nav_custom_icons_summary,
            icon = Icons.Rounded.Apps,
            aliases = listOf("navigation", "icon", "custom"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.nav_mode",
            titleRes = R.string.settings_nav_mode,
            summaryRes = R.string.settings_nav_mode_summary,
            icon = Icons.Rounded.SpaceDashboard,
            aliases = listOf("navigation", "bar", "rail", "bottom", "floating"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.nav_show_plugin",
            titleRes = R.string.settings_show_plugin,
            summaryRes = R.string.settings_nav_layout_title,
            icon = Icons.Filled.Widgets,
            aliases = listOf("navigation", "show", "hide", "plugin"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.nav_show_superuser",
            titleRes = R.string.settings_show_superuser,
            summaryRes = R.string.settings_nav_layout_title,
            icon = Icons.Filled.Shield,
            aliases = listOf("navigation", "show", "hide", "superuser", "root"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.nav_show_module",
            titleRes = R.string.settings_show_module,
            summaryRes = R.string.settings_nav_layout_title,
            icon = Icons.Filled.Extension,
            aliases = listOf("navigation", "show", "hide", "module"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.floating_bar_style",
            titleRes = R.string.settings_floating_bar_style,
            icon = Icons.Rounded.Style,
            aliases = listOf("floating", "bar", "style", "drawer"),
            requiresFloatingBar = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.floating_bar_glass",
            titleRes = R.string.settings_floating_bar_glass,
            summaryRes = R.string.settings_floating_bar_glass_summary,
            icon = Icons.Rounded.BlurOn,
            aliases = listOf("floating", "glass", "blur", "translucent"),
            requiresFloatingBar = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.glass_blur_strength",
            titleRes = R.string.settings_navbar_glass_blur_strength,
            icon = Icons.Rounded.BlurCircular,
            aliases = listOf("glass", "blur", "strength"),
            requiresFloatingGlass = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.glass_transparency",
            titleRes = R.string.settings_navbar_glass_transparency,
            icon = Icons.Rounded.Opacity,
            aliases = listOf("glass", "transparency", "alpha"),
            requiresFloatingGlass = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.glass_highlight",
            titleRes = R.string.settings_navbar_glass_highlight_strength,
            icon = Icons.Rounded.Brightness7,
            aliases = listOf("glass", "highlight", "brightness"),
            requiresFloatingGlass = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.glass_specular",
            titleRes = R.string.settings_navbar_glass_specular,
            summaryRes = R.string.settings_navbar_glass_specular_summary,
            icon = Icons.Rounded.FlashOn,
            aliases = listOf("glass", "specular", "shine"),
            requiresFloatingGlass = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.glass_inner_glow",
            titleRes = R.string.settings_navbar_glass_inner_glow,
            summaryRes = R.string.settings_navbar_glass_inner_glow_summary,
            icon = Icons.Rounded.WbTwilight,
            aliases = listOf("glass", "glow", "inner"),
            requiresFloatingGlass = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.glass_border",
            titleRes = R.string.settings_navbar_glass_border,
            summaryRes = R.string.settings_navbar_glass_border_summary,
            icon = Icons.Rounded.BorderOuter,
            aliases = listOf("glass", "border", "outline"),
            requiresFloatingGlass = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.floating_bar_compact",
            titleRes = R.string.settings_floating_bar_compact,
            icon = Icons.Rounded.RoundedCorner,
            aliases = listOf("floating", "compact", "compact bar"),
            requiresFloatingBar = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.floating_auto_hide",
            titleRes = R.string.settings_floating_auto_hide,
            summaryRes = R.string.settings_floating_auto_hide_summary,
            icon = Icons.Rounded.VisibilityOff,
            aliases = listOf("floating", "auto hide", "hide"),
            requiresFloatingBar = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.floating_swipe_hide",
            titleRes = R.string.settings_floating_swipe_hide,
            summaryRes = R.string.settings_floating_swipe_hide_summary,
            icon = Icons.Rounded.SwipeUp,
            aliases = listOf("floating", "swipe", "hide"),
            requiresFloatingBar = true,
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.sucompat",
            titleRes = R.string.settings_sucompat,
            summaryRes = R.string.settings_sucompat_summary,
            icon = Icons.Filled.AdminPanelSettings,
            aliases = listOf("su", "compat", "compatibility", "legacy"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.soft_reboot",
            titleRes = R.string.settings_soft_reboot,
            summaryRes = R.string.settings_soft_reboot_summary,
            icon = Icons.Filled.RestartAlt,
            aliases = listOf("reboot", "soft", "restart"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.web_debugging",
            titleRes = R.string.enable_web_debugging,
            summaryRes = R.string.enable_web_debugging_summary,
            icon = Icons.Filled.DeveloperMode,
            aliases = listOf("webui", "debug", "webview", "devtools"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.auto_jailbreak",
            titleRes = R.string.settings_auto_jailbreak,
            summaryRes = R.string.settings_auto_jailbreak_summary,
            icon = Icons.Filled.FlashOn,
            aliases = listOf("jailbreak", "auto", "late load"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_card_show_more",
            titleRes = R.string.module_card_show_more_info,
            summaryRes = R.string.module_card_show_more_info_summary,
            icon = Icons.Filled.Description,
            aliases = listOf("module", "card", "info"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_card_fold_system",
            titleRes = R.string.module_card_fold_system,
            summaryRes = R.string.module_card_fold_system_summary,
            icon = Icons.Filled.Layers,
            aliases = listOf("module", "card", "system", "fold"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_card_spliced",
            titleRes = R.string.module_card_spliced,
            summaryRes = R.string.module_card_spliced_summary,
            icon = Icons.Filled.GridView,
            aliases = listOf("module", "card", "spliced", "group"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_card_button_style",
            titleRes = R.string.module_card_button_style,
            summaryRes = R.string.module_card_button_style_summary,
            icon = Icons.Filled.Palette,
            aliases = listOf("module", "card", "button", "style"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_enabled",
            titleRes = R.string.module_banner_enabled,
            summaryRes = R.string.module_banner_enabled_summary,
            icon = Icons.Filled.Wallpaper,
            aliases = listOf("module", "banner", "cover", "image"),
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_folk",
            titleRes = R.string.module_banner_folk,
            summaryRes = R.string.module_banner_folk_summary,
            icon = Icons.Filled.Wallpaper,
            aliases = listOf("module", "banner", "folk"),
            // The banner owns its switches: with the banner off, this row is not on the page.
            available = { ModuleBannerConfig.enabled },
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_api_mode",
            titleRes = R.string.module_banner_api_mode,
            summaryRes = R.string.module_banner_api_mode_summary,
            icon = Icons.Outlined.Cloud,
            aliases = listOf("module", "banner", "api", "mode", "custom"),
            available = { ModuleBannerConfig.policy.active },
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_api_source",
            titleRes = R.string.module_banner_api_source,
            icon = Icons.Outlined.Cloud,
            aliases = listOf("module", "banner", "api", "source", "url"),
            available = { ModuleBannerConfig.policy.apiActive },
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_marketplace",
            titleRes = R.string.module_banner_marketplace,
            icon = Icons.Outlined.Cloud,
            aliases = listOf("module", "banner", "marketplace", "store"),
            available = { ModuleBannerConfig.policy.apiActive },
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_clear_cache",
            titleRes = R.string.module_banner_clear_cache,
            icon = Icons.Outlined.Cloud,
            aliases = listOf("module", "banner", "cache", "clear"),
            available = { ModuleBannerConfig.policy.apiActive },
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_opacity",
            titleRes = R.string.module_banner_opacity,
            icon = Icons.Filled.Wallpaper,
            aliases = listOf("module", "banner", "opacity"),
            available = { ModuleBannerConfig.enabled && ModuleBannerConfig.customOpacityEnabled },
        )
    )
    add(
        SettingsCategory.BEHAVIOR.row(
            id = "behavior.module_banner_custom_opacity",
            titleRes = R.string.module_banner_custom_opacity,
            summaryRes = R.string.module_banner_custom_opacity_summary,
            icon = Icons.Filled.Wallpaper,
            aliases = listOf("module", "banner", "opacity", "custom"),
            available = { ModuleBannerConfig.enabled },
        )
    )

    // Function
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.kernel_umount",
            titleRes = R.string.settings_kernel_umount,
            summaryRes = R.string.settings_kernel_umount_summary,
            icon = Icons.Filled.LayersClear,
            aliases = listOf("umount", "unmount", "module", "hide", "master switch", "总开关"),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.umount_modules_default",
            titleRes = R.string.settings_umount_modules_default,
            summaryRes = R.string.settings_umount_modules_default_summary,
            icon = Icons.AutoMirrored.Filled.Rule,
            aliases = listOf("umount", "unmount", "default", "app", "默认", "模块"),
        )
    )
    add(
        SettingsCategory.FUNCTION.page(
            id = "function.umount_paths",
            titleRes = R.string.umount_paths_title,
            summaryRes = R.string.umount_paths_summary,
            icon = Icons.Filled.LayersClear,
            route = Route.UmountPaths,
            aliases = listOf(
                "umount",
                "unmount",
                "mountpoint",
                "bind mount",
                "卸载",
                "挂载点",
                "自定义",
                "真实挂载",
                "文件",
            ),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.boot_hide",
            titleRes = R.string.settings_finasu_hide,
            summaryRes = R.string.settings_finasu_hide_summary,
            icon = Icons.Filled.PhonelinkLock,
            aliases = listOf("bootloader", "unlock", "hide", "vbmeta", "verifiedboot", "锁定", "引导"),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.avc_spoof",
            titleRes = R.string.settings_avc_spoof,
            summaryRes = R.string.settings_avc_spoof_summary,
            icon = Icons.Filled.Security,
            aliases = listOf("avc", "selinux", "spoof", "denial"),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.sulog",
            titleRes = R.string.settings_sulog,
            summaryRes = R.string.settings_sulog_summary,
            icon = Icons.AutoMirrored.Filled.Article,
            aliases = listOf("su", "log", "logging", "history"),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.dynamic_manager",
            titleRes = R.string.allow_any_dynamic_manager,
            summaryRes = R.string.allow_any_dynamic_manager_summary,
            icon = Icons.Filled.AdminPanelSettings,
            aliases = listOf("dynamic manager", "manager", "superkey"),
            available = { it.isDynamicManagerAvailable },
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.folk_zygisk",
            titleRes = R.string.settings_folk_zygisk,
            icon = Icons.Filled.Code,
            aliases = listOf("folkzygisk", "zygisk", "injection"),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.sui",
            titleRes = R.string.sui_title,
            icon = Icons.Filled.WaterDrop,
            aliases = listOf("sui", "shizuku", "adbd"),
        )
    )
    add(
        SettingsCategory.FUNCTION.page(
            id = "function.sui_manage",
            titleRes = R.string.sui_manage,
            summaryRes = R.string.sui_manage_summary,
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            route = Route.SuiPermissions,
            aliases = listOf("sui", "permission", "authorize"),
            // The row only exists once Sui is actually installed.
            available = { it.suiStatus?.installed == true },
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.folk_mount",
            titleRes = R.string.settings_folk_mount,
            summaryRes = R.string.settings_folk_mount_summary,
            icon = Icons.Filled.Layers,
            aliases = listOf("mount", "metamodule", "overlay", "builtin", "nomount"),
        )
    )
    add(
        SettingsCategory.FUNCTION.page(
            id = "function.nomount",
            titleRes = R.string.settings_nomount,
            icon = Icons.Filled.Layers,
            route = Route.Nomount,
            aliases = listOf("nomount", "no mount", "exclusion", "injection"),
        )
    )
    add(
        SettingsCategory.FUNCTION.page(
            id = "function.uts_view",
            titleRes = R.string.settings_uts_view,
            summaryRes = R.string.settings_uts_view_summary,
            icon = Icons.Rounded.TextFields,
            route = Route.UtsView,
            aliases = listOf("uts", "uname", "kernel version", "内核版本", "伪装"),
        )
    )
    add(
        SettingsCategory.FUNCTION.page(
            id = "function.netisolate",
            titleRes = R.string.settings_netisolate,
            summaryRes = R.string.settings_netisolate_summary,
            icon = Icons.Filled.WifiOff,
            route = Route.NetIsolate,
            aliases = listOf("network", "isolate", "offline", "uid", "网络隔离", "断网"),
        )
    )

    // Security
    add(
        SettingsCategory.SECURITY.row(
            id = "security.selinux_hide",
            titleRes = R.string.settings_selinux_hide,
            summaryRes = R.string.settings_selinux_hide_summary,
            icon = Icons.Filled.Security,
            aliases = listOf("selinux", "hide", "spoof"),
        )
    )
    add(
        SettingsCategory.FUNCTION.row(
            id = "function.mount_view_filter",
            titleRes = R.string.settings_mount_view_filter,
            summaryRes = R.string.settings_mount_view_filter_summary,
            icon = Icons.Rounded.VisibilityOff,
            aliases = listOf("mount", "hide", "proc", "mountinfo", "mounts", "挂载"),
        )
    )
    add(
        SettingsCategory.SECURITY.row(
            id = "security.selinux_mode",
            titleRes = R.string.settings_selinux_mode,
            icon = Icons.Filled.Lock,
            aliases = listOf("selinux", "enforcing", "permissive", "mode"),
        )
    )
    add(
        SettingsCategory.SECURITY.row(
            id = "security.adb_root",
            titleRes = R.string.settings_adb_root,
            summaryRes = R.string.settings_adb_root_summary,
            icon = Icons.Filled.Adb,
            aliases = listOf("adb", "root", "debug"),
        )
    )
    add(
        SettingsCategory.SECURITY.row(
            id = "security.shell_su_adb_only",
            titleRes = R.string.settings_shell_su_adb_only,
            summaryRes = R.string.settings_shell_su_adb_only_summary,
            icon = Icons.Filled.VerifiedUser,
            aliases = listOf("shell", "su", "adb", "terminal"),
        )
    )

    // Authentication
    add(
        SettingsCategory.AUTHENTICATION.row(
            id = "authentication.default_method",
            titleRes = R.string.security_default_method,
            summaryRes = R.string.security_default_method_summary,
            icon = Icons.Filled.Fingerprint,
            aliases = listOf("authentication", "biometric", "fingerprint", "method"),
        )
    )
    add(
        SettingsCategory.AUTHENTICATION.row(
            id = "authentication.pin",
            titleRes = R.string.security_pin_set,
            summaryRes = R.string.security_pin_configured,
            icon = Icons.Filled.Password,
            aliases = listOf("pin", "password", "code", "change pin", "set pin"),
        )
    )
    add(
        SettingsCategory.AUTHENTICATION.row(
            id = "authentication.pattern",
            titleRes = R.string.security_pattern_set,
            summaryRes = R.string.security_pattern_configured,
            icon = Icons.Filled.Gesture,
            aliases = listOf("pattern", "gesture", "change pattern", "set pattern"),
        )
    )
    SecurityTarget.entries.forEach { target ->
        add(
            SettingsCategory.AUTHENTICATION.row(
                id = "authentication.target.${target.key}",
                titleRes = target.titleRes,
                summaryRes = R.string.security_target_protected_summary,
                icon = Icons.Filled.Lock,
                aliases = listOf("authentication", "protect", target.key.replace('_', ' ')),
            )
        )
    }

    // Multimedia
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.vibration",
            titleRes = R.string.media_vibration,
            icon = Icons.Filled.Vibration,
            aliases = listOf("vibration", "haptic", "feedback"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.music",
            titleRes = R.string.media_music,
            icon = Icons.Filled.MusicNote,
            aliases = listOf("music", "audio", "background"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.autoplay",
            titleRes = R.string.media_autoplay,
            icon = Icons.Filled.PlayArrow,
            aliases = listOf("music", "autoplay", "play"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.loop",
            titleRes = R.string.media_loop,
            icon = Icons.Filled.Repeat,
            aliases = listOf("music", "loop", "repeat"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.startup_sound",
            titleRes = R.string.media_startup,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            aliases = listOf("startup", "boot", "sound", "effect"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.sound",
            titleRes = R.string.media_sound,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            aliases = listOf("sound", "effect", "click"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.sound_source",
            titleRes = R.string.media_source,
            icon = Icons.Filled.LibraryMusic,
            aliases = listOf("sound", "source", "local", "preset"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.sound_preset",
            titleRes = R.string.media_preset,
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            aliases = listOf("sound", "preset", "tone"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.sound_scope",
            titleRes = R.string.media_scope,
            icon = Icons.Filled.Tune,
            aliases = listOf("sound", "scope", "global", "navigation"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.select_file",
            titleRes = R.string.media_select_file,
            icon = Icons.Filled.FolderOpen,
            aliases = listOf("file", "select", "import", "choose"),
        )
    )
    add(
        SettingsCategory.MULTIMEDIA.row(
            id = "multimedia.clear_file",
            titleRes = R.string.media_clear,
            icon = Icons.Filled.Delete,
            aliases = listOf("clear", "remove", "delete"),
        )
    )

    // Backup
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.local_enabled",
            titleRes = R.string.backup_local_enabled,
            summaryRes = R.string.backup_local_summary,
            icon = Icons.Filled.Folder,
            aliases = listOf("backup", "local"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.webdav_enabled",
            titleRes = R.string.backup_webdav_enabled,
            icon = Icons.Filled.Cloud,
            aliases = listOf("backup", "webdav", "cloud", "server"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.open_folder",
            titleRes = R.string.backup_open_folder,
            icon = Icons.Filled.FolderOpen,
            aliases = listOf("backup", "folder", "downloads"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.before_flash",
            titleRes = R.string.backup_before_flash,
            summaryRes = R.string.backup_before_flash_summary,
            icon = Icons.Filled.FlashOn,
            aliases = listOf("backup", "flash", "zip"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.modules",
            titleRes = R.string.backup_modules,
            summaryRes = R.string.backup_modules_summary,
            icon = Icons.Filled.Extension,
            aliases = listOf("backup", "module"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.allowlist",
            titleRes = R.string.backup_allowlist,
            icon = Icons.AutoMirrored.Filled.Rule,
            aliases = listOf("backup", "allowlist", "superuser"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.now",
            titleRes = R.string.backup_now,
            icon = Icons.Filled.Backup,
            aliases = listOf("backup", "now", "run"),
        )
    )
    add(
        SettingsCategory.BACKUP.page(
            id = "backup.list",
            titleRes = R.string.backup_open_list,
            icon = Icons.AutoMirrored.Filled.List,
            route = Route.BackupList,
            aliases = listOf("backup", "list", "history", "restore"),
        )
    )
    add(
        SettingsCategory.BACKUP.row(
            id = "backup.webdav_test",
            titleRes = R.string.backup_webdav_test,
            icon = Icons.Filled.Cloud,
            aliases = listOf("webdav", "test", "connection"),
        )
    )
    add(
        SettingsCategory.BACKUP.page(
            id = "backup.webdav_browse",
            titleRes = R.string.backup_webdav_browse,
            icon = Icons.Filled.FolderOpen,
            route = Route.WebDavBrowser(),
            aliases = listOf("webdav", "browse", "folder", "remote"),
        )
    )

    // The rows the hub itself owns.
    add(
        hubRow(
            id = "hub.wallpaper_gallery",
            titleRes = R.string.settings_wallpaper_gallery,
            icon = Icons.Filled.Collections,
            target = SettingsSearchTarget.Page(Route.WallpaperGallery),
            aliases = listOf("wallpaper", "gallery", "photo"),
        )
    )
    add(
        hubRow(
            id = "hub.bug_feedback",
            titleRes = R.string.settings_bug_feedback,
            icon = Icons.Filled.BugReport,
            target = SettingsSearchTarget.Hub(SettingsHubAction.BUG_FEEDBACK),
            aliases = listOf("bug", "feedback", "issue", "report"),
        )
    )
    add(
        hubRow(
            id = "hub.disclaimer",
            titleRes = R.string.settings_disclaimer,
            icon = Icons.Filled.Policy,
            target = SettingsSearchTarget.Hub(SettingsHubAction.DISCLAIMER),
            aliases = listOf("disclaimer", "warning", "risk"),
        )
    )
    add(
        hubRow(
            id = "hub.send_log",
            titleRes = R.string.send_log,
            icon = Icons.Filled.Description,
            target = SettingsSearchTarget.Hub(SettingsHubAction.SEND_LOG),
            aliases = listOf("log", "send", "debug", "report"),
        )
    )
    add(
        hubRow(
            id = "hub.about",
            titleRes = R.string.about,
            icon = Icons.Filled.Info,
            target = SettingsSearchTarget.Page(Route.About),
            aliases = listOf("about", "version", "credits", "license"),
        )
    )
    add(
        hubRow(
            id = "hub.uninstall",
            titleRes = R.string.settings_uninstall,
            icon = Icons.Filled.Delete,
            target = SettingsSearchTarget.Hub(SettingsHubAction.UNINSTALL),
            aliases = listOf("uninstall", "remove"),
            available = { it.isLkmMode },
        )
    )
}

/**
 * Resolves the index in the current locale.
 *
 * The folded copies the matcher works on are built here, so typing only ever folds the query.
 */
@Composable
fun rememberSettingsSearchEntries(): List<ResolvedSettingsEntry> {
    val resources = LocalResources.current
    return remember(resources) {
        settingsSearchIndex.map { entry ->
            resolveEntry(
                entry = entry,
                title = resources.getString(entry.titleRes),
                summary = entry.summaryRes.takeIf { it != 0 }?.let(resources::getString).orEmpty(),
                parentTitle = resources.getString(entry.parentTitleRes),
            )
        }
    }
}

