package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeExtTest {

    @Test
    fun semanticErrorMatchesTheMaterialBaseline() {
        val light = lightColorScheme(primary = Color(0xFF123456)).withSemanticError(false)
        val dark = darkColorScheme(primary = Color(0xFF123456)).withSemanticError(true)

        assertEquals(lightColorScheme().errorContainer, light.errorContainer)
        assertEquals(lightColorScheme().onErrorContainer, light.onErrorContainer)
        assertEquals(darkColorScheme().errorContainer, dark.errorContainer)
        assertEquals(darkColorScheme().onErrorContainer, dark.onErrorContainer)
    }

    @Test
    fun amoledKeepsRaisedSurfacesDistinguishable() {
        val amoled = darkColorScheme().amoledBackground(true)

        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertTrue(amoled.surfaceContainerHigh.luminance() > amoled.surfaceContainer.luminance())
        assertTrue(amoled.surfaceBright.luminance() > amoled.surfaceContainer.luminance())
    }
}
