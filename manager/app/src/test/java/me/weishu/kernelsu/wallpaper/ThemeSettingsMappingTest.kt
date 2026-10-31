package me.weishu.kernelsu.wallpaper

import me.weishu.kernelsu.data.repository.SettingsRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class ThemeSettingsMappingTest {
    private fun repository(values: MutableMap<String, Any>): SettingsRepository =
        Proxy.newProxyInstance(SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java)) { _, method, args ->
            val property = method.name.drop(3).replaceFirstChar { it.lowercase() }
            if (method.name.startsWith("set")) { values[property] = args!![0]; null }
            else values[property]
        } as SettingsRepository

    @Test fun fpConfigurationUsesExistingRepositoryProperties() {
        val values = mutableMapOf<String, Any>("themeMode" to 0)
        ThemeSettingsMapping.apply(JSONObject()
            .put("customColor", "sakura").put("nightModeEnabled", true)
            .put("nightModeFollowSys", false).put("useSystemDynamicColor", false)
            .put("colorGenerationMode", "classic").put("colorStandard", "MD3_2021")
            .put("colorStyle", "EXPRESSIVE").put("colorContrast", "HIGH")
            .put("statsTopLayout", "grid").put("isListWorkingCardModeHidden", true), repository(values))
        assertEquals(0xFF9B404F.toInt(), values["keyColor"])
        assertEquals(2, values["themeMode"])
        assertEquals(false, values["useSystemDynamicColor"])
        assertEquals("classic", values["colorGenerationMode"])
        assertEquals("SPEC_2021", values["colorSpec"])
        assertEquals("Expressive", values["colorStyle"])
        assertEquals("HIGH", values["colorContrast"])
        assertEquals("grid", values["statsTopLayout"])
        assertEquals(true, values["listWorkingCardModeHidden"])
    }

    @Test fun resetRestoresEveryThemeOwnedSetting() {
        val values = mutableMapOf<String, Any>(
            "themeMode" to 6, "keyColor" to 1234, "useSystemDynamicColor" to false,
            "colorStyle" to "Expressive", "colorSpec" to "SPEC_2021",
            "colorGenerationMode" to "classic", "colorContrast" to "HIGH",
            "statsTopLayout" to "grid", "listWorkingCardModeHidden" to true,
        )
        ThemeSettingsMapping.reset(repository(values))
        assertEquals(mapOf(
            "themeMode" to 0, "keyColor" to 0, "useSystemDynamicColor" to true,
            "colorStyle" to "TonalSpot", "colorSpec" to "SPEC_2025",
            "colorGenerationMode" to "custom", "colorContrast" to "STANDARD",
            "statsTopLayout" to "list", "listWorkingCardModeHidden" to false,
        ), values)
    }

    @Test fun absentFieldsPreserveCurrentSettingsAndExtensionKeepsExactSeed() {
        val values = mutableMapOf<String, Any>("themeMode" to 6, "keyColor" to 1234)
        ThemeSettingsMapping.apply(JSONObject(), repository(values))
        assertEquals(mapOf("themeMode" to 6, "keyColor" to 1234), values)
        ThemeSettingsMapping.apply(JSONObject().put("customColor", "blue")
            .put(ThemeSettingsMapping.KEY_COLOR, -12345), repository(values))
        assertEquals(-12345, values["keyColor"])
        ThemeSettingsMapping.apply(
            JSONObject().put(ThemeSettingsMapping.LEGACY_KEY_COLOR, -54321),
            repository(values),
        )
        assertEquals(-54321, values["keyColor"])
    }
}
