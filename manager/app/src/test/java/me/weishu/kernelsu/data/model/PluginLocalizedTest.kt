package me.weishu.kernelsu.data.model

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PluginLocalizedTest {

    private lateinit var original: Locale

    @Before
    fun saveLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun fullTagWins() {
        Locale.setDefault(Locale.forLanguageTag("zh-CN"))
        val labels = mapOf("zh-CN" to "完整", "zh" to "基础")
        assertEquals("完整", labels.pluginLocalizedOrNull())
    }

    @Test
    fun otherRegionFallsBackToLanguage() {
        Locale.setDefault(Locale.forLanguageTag("zh-TW"))
        val labels = mapOf("zh-CN" to "完整", "zh" to "基础")
        assertEquals("基础", labels.pluginLocalizedOrNull())
    }

    @Test
    fun underscoreKeyIsAccepted() {
        Locale.setDefault(Locale.forLanguageTag("zh-CN"))
        assertEquals("下划线", mapOf("zh_CN" to "下划线").pluginLocalizedOrNull())
    }

    @Test
    fun missingLanguageAndEmptyMapReturnNull() {
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
        assertNull(mapOf("zh-CN" to "完整").pluginLocalizedOrNull())
        assertNull(emptyMap<String, String>().pluginLocalizedOrNull())
        assertNull(mapOf("zh" to " ").pluginLocalizedOrNull())
    }
}
