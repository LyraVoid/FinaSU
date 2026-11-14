package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSelectionTest {

    private val assetBases = setOf(
        "background", "video_background", "title_image", "font", "home_layout",
        "background_home", "background_kernel", "background_superuser",
        "background_system_module", "background_settings", "grid_working_card_background",
    )

    @Test
    fun `payload bases map to their part`() {
        assertEquals(ThemePart.MAIN_BACKGROUND, themePartOf("background"))
        assertEquals(ThemePart.MAIN_BACKGROUND, themePartOf("video_background"))
        assertEquals(ThemePart.MAIN_BACKGROUND, themePartOf("title_image"))
        assertEquals(ThemePart.PAGE_BACKGROUNDS, themePartOf("background_home"))
        assertEquals(ThemePart.PAGE_BACKGROUNDS, themePartOf("background_settings"))
        assertEquals(ThemePart.FONT, themePartOf("font"))
        assertEquals(ThemePart.HOME_LAYOUT, themePartOf("home_layout"))
        assertEquals(ThemePart.SURFACES, themePartOf("grid_working_card_background"))
        assertEquals(ThemePart.SURFACES, themePartOf("page.home"))
    }

    @Test
    fun `configuration alone makes a part available`() {
        val parts = themePartsIn(JSONObject().put("fontMode", "custom").put("homeLayoutStyle", "grid"),
            emptySet(), assetBases)
        assertEquals(setOf(ThemePart.FONT, ThemePart.HOME_LAYOUT), parts)
    }

    @Test
    fun `media files named by json do not look like surfaces`() {
        val stems = setOf("nav_icon_Home", "theme_song", "sound_effect_custom")
        val parts = themePartsIn(JSONObject().put("musicFilename", "theme_song.mp3"),
            stems, assetBases)
        assertEquals(setOf(ThemePart.MUSIC), parts)
    }

    @Test
    fun `registered payload stems select their part`() {
        val parts = themePartsIn(JSONObject(), setOf("background", "grid_working_card_background"),
            assetBases)
        assertEquals(setOf(ThemePart.MAIN_BACKGROUND, ThemePart.SURFACES), parts)
    }

    @Test
    fun `colors interface and language are read from keys`() {
        val parts = themePartsIn(
            JSONObject().put("customColor", "indigo").put("nightModeEnabled", true)
                .put("appLanguage", "zh-CN"),
            emptySet(), assetBases,
        )
        assertEquals(setOf(ThemePart.COLORS, ThemePart.INTERFACE, ThemePart.LANGUAGE), parts)
    }

    @Test
    fun `selection defaults to everything and supports single parts`() {
        assertTrue(ThemePart.FONT in ThemeSelection.All)
        assertFalse(ThemePart.FONT in ThemeSelection(setOf(ThemePart.COLORS)))
        assertTrue(ThemeSelection(emptySet()).isEmpty)
    }
}
