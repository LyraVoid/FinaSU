package me.weishu.kernelsu.wallpaper

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the fill rule for a control sitting on the chrome platform, such as the top bar's back
 * button: it must stay translucent while the wallpaper is visible, and it must keep the tone the
 * caller asked for.
 */
class WallpaperChromeControlTest {
    private val scheme = lightColorScheme()
    private val tint = Color(0xFFE6E0E9)

    @Test
    fun controlFillTracksTheWallpaperOpacity() {
        val clear = resolveFolkWallpaperTokens(scheme, 0f).chromeControlFill(tint)
        val half = resolveFolkWallpaperTokens(scheme, 0.5f).chromeControlFill(tint)
        val solid = resolveFolkWallpaperTokens(scheme, 1f).chromeControlFill(tint)

        assertEquals(0.208f, clear.alpha, 0.005f)
        assertEquals(0.577f, half.alpha, 0.005f)
        assertEquals(1f, solid.alpha, 0.005f)
        assertTrue(clear.alpha < half.alpha)
        assertTrue(half.alpha < solid.alpha)
    }

    @Test
    fun fullyOpaqueControlFillKeepsTheRequestedTone() {
        val fill = resolveFolkWallpaperTokens(scheme, 1f).chromeControlFill(tint)

        assertEquals(tint, fill)
    }

    @Test
    fun clearControlFillStaysVisibleOnLightAndDarkWallpapers() {
        val lightFill = resolveFolkWallpaperTokens(scheme, 0f).chromeControlFill(tint)
            .compositeOver(Color.White)
        val darkFill = resolveFolkWallpaperTokens(
            androidx.compose.material3.darkColorScheme(),
            0f,
        ).chromeControlFill(tint).compositeOver(Color.Black)

        assertTrue(lightFill.luminance() < 0.90f)
        assertTrue(darkFill.luminance() > 0.01f)
    }
}
