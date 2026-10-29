package me.weishu.kernelsu.ui.navigation3

import androidx.navigation3.runtime.NavKey
import me.weishu.kernelsu.wallpaper.WallpaperConfig

/**
 * The wallpaper page owned by the current navigation destination.
 *
 * Routes without a section of their own inherit the nearest route below them, so editors and flash
 * flows keep their parent page's wallpaper.
 */
// shortcut: unmapped routes use the visible main page; add new section routes to the map below.
internal fun wallpaperPageFor(backStack: List<NavKey>, fallback: Int): Int =
    backStack.asReversed().firstNotNullOfOrNull { it.wallpaperPage } ?: fallback

private val NavKey.wallpaperPage: Int?
    get() = when (this) {
        Route.Susfs,
        Route.Install,
        -> WallpaperConfig.PAGE_HOME

        Route.PluginStore -> WallpaperConfig.PAGE_PLUGIN

        Route.Sulog,
        is Route.AppProfile,
        -> WallpaperConfig.PAGE_SUPERUSER

        Route.ModuleRepo,
        is Route.ModuleRepoDetail,
        Route.KernelModules,
        is Route.ExecuteModuleAction,
        -> WallpaperConfig.PAGE_MODULE

        Route.About,
        Route.UtsView,
        Route.NetIsolate,
        Route.Nomount,
        Route.UmountPaths,
        Route.SuiPermissions,
        Route.NomountExclusions,
        Route.ColorPalette,
        Route.Wallpaper,
        Route.WallpaperGallery,
        Route.Font,
        Route.ThemeStore,
        Route.MyThemes,
        Route.ApiMarketplace,
        is Route.SettingsCategory,
        is Route.WebDavBrowser,
        Route.BackupList,
        Route.Language,
        -> WallpaperConfig.PAGE_SETTINGS

        else -> null
    }
