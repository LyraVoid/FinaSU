package me.weishu.kernelsu.ui.theme

import me.weishu.kernelsu.media.TitleTextMode
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemePreviewTest {
    @Test
    fun `nav icons map every themed destination without touching installed icons`() {
        val icons = BottomBarDestination.entries.associate { destination ->
            "nav_icon_${destination.themeKey}.png" to File("${destination.name}.png")
        }
        val json = JSONObject()
            .put("navIconCustomEnabled", true)
            .put("navIcons", JSONObject().also { values ->
                BottomBarDestination.entries.forEach { destination ->
                    values.put(destination.themeKey, "nav_icon_${destination.themeKey}.png")
                }
            })

        val preview = previewNavIcons(json, icons)

        assertEquals(BottomBarDestination.entries.toSet(), preview.keys)
        assertTrue(preview.values.all { it.name.endsWith(".png") })
    }

    @Test
    fun `title media comes from the package values`() {
        val image = File("title_image.webp")
        val media = previewVisualMedia(
            JSONObject()
                .put("isAdvancedTitleStyleEnabled", true)
                .put("titleImageDayOpacity", 0.8)
                .put("titleImageNightOpacity", 0.6)
                .put("titleImageDim", 0.2)
                .put("titleImageOffsetX", -0.5)
                .put("titleTextMode", "custom")
                .put("titleCustomText", "Preview Title"),
            mapOf(image.name to image),
        )

        assertEquals(image, media.titleImage)
        assertEquals(0.8f, media.titleDayOpacity, 0.0001f)
        assertEquals(0.6f, media.titleNightOpacity, 0.0001f)
        assertEquals(0.2f, media.titleDim, 0.0001f)
        assertEquals(-0.5f, media.titleOffsetX, 0.0001f)
        assertEquals(TitleTextMode.CUSTOM, media.titleTextMode)
        assertEquals("Preview Title", media.customTitleText)
    }
}
