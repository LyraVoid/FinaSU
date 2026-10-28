package me.weishu.kernelsu.wallpaper

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FolkWallpaperTokensTest {
    private val material = WallpaperMaterial(
        tint = Color.DarkGray,
        alpha = 0.036f,
        contentColor = Color.White,
        supportingColor = Color.LightGray,
    )
    private val tokens = FolkWallpaperTokens(
        group = material,
        raised = material,
        overlay = material,
        chrome = material,
        selection = material,
    )

    @Test fun materialHonorsMinimumAlphaWithoutChangingItsTint() {
        val card = tokens.material(WallpaperSurfaceRole.Group, minimumAlpha = 0.35f)

        assertEquals(0.35f, card.alpha, 0f)
        assertEquals(material.tint, card.tint)
        assertEquals(material.contentColor, card.contentColor)
    }
}
