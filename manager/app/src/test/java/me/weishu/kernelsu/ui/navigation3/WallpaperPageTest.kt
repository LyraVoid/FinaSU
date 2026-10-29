package me.weishu.kernelsu.ui.navigation3

import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperPageTest {
    @Test
    fun childRouteUsesItsOwnSectionInsteadOfTheVisibleMainPage() {
        assertEquals(
            WallpaperConfig.PAGE_SETTINGS,
            wallpaperPageFor(
                listOf(Route.Main, Route.SettingsCategory("appearance")),
                WallpaperConfig.PAGE_HOME,
            ),
        )
        assertEquals(
            WallpaperConfig.PAGE_MODULE,
            wallpaperPageFor(listOf(Route.Main, Route.KernelModules), WallpaperConfig.PAGE_HOME),
        )
    }

    @Test
    fun childWithoutASectionInheritsTheNearestParent() {
        val flash = Route.Flash(FlashIt.FlashModules(emptyList()))
        assertEquals(
            WallpaperConfig.PAGE_SETTINGS,
            wallpaperPageFor(
                listOf(Route.Main, Route.SettingsCategory("appearance"), flash),
                WallpaperConfig.PAGE_HOME,
            ),
        )
        assertEquals(
            WallpaperConfig.PAGE_HOME,
            wallpaperPageFor(listOf(Route.Main, flash), WallpaperConfig.PAGE_HOME),
        )
    }
}
