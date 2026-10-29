package me.weishu.kernelsu.ui.component.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BootSlotResourcesTest {
    @Test
    fun `every locale has the same keys and format placeholders`() {
        val root = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res"),
        ).first(File::isDirectory)
        val locales = listOf(
            "values",
            "values-ar",
            "values-es",
            "values-in",
            "values-ja",
            "values-ko",
            "values-mgl",
            "values-pl",
            "values-pt-rBR",
            "values-ru",
            "values-tr",
            "values-vi",
            "values-zh-rAG",
            "values-zh-rAT",
            "values-zh-rCK",
            "values-zh-rCN",
            "values-zh-rMC",
            "values-zh-rTW",
            "values-zh-rWC",
        )
        val expected = parseStrings(File(root, "values/boot_slot.xml"))
        assertTrue(expected.containsKey("boot_slot_source"))
        locales.forEach { locale ->
            val actual = parseStrings(File(root, "$locale/boot_slot.xml"))
            assertEquals("keys in $locale", expected.keys, actual.keys)
            expected.forEach { (key, text) ->
                assertEquals("placeholders in $locale/$key", placeholders(text), placeholders(actual.getValue(key)))
            }
        }
    }

    @Test
    fun `cat locale keeps its characteristic voice`() {
        val root = sequenceOf(File("src/main/res"), File("app/src/main/res")).first(File::isDirectory)
        val text = File(root, "values-zh-rAG/boot_slot.xml").readText()

        assertTrue(text.contains("喵"))
        assertTrue(text.contains("原味"))
    }

    private fun parseStrings(file: File): Map<String, String> {
        assertTrue("missing ${file.path}", file.isFile)
        return Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(value: String): List<String> =
        Regex("""%(?:\d+\$)?[a-zA-Z]""").findAll(value).map { it.value }.sorted().toList()
}
