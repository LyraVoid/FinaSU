package me.weishu.kernelsu.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SusfsResourcesTest {
    @Test
    fun `every locale has the same unique keys and format placeholders`() {
        val root = resourceRoot()
        val expected = parseStrings(File(root, "values/susfs.xml"))
        listOf(
            "susfs_acknowledged_boot",
            "susfs_partial_acknowledged",
            "susfs_config_none",
            "susfs_lkm_ack_hint",
            "susfs_provider_label",
            "susfs_provider_builtin",
            "susfs_provider_lkm",
            "susfs_provider_not_detected",
            "susfs_provider_unknown",
        ).forEach { assertTrue("missing $it", expected.containsKey(it)) }

        localeFiles(root).forEach { file ->
            val actual = parseStrings(file)
            assertEquals("keys in ${file.parentFile?.name ?: file.path}", expected.keys, actual.keys)
            expected.forEach { (key, text) ->
                assertEquals(
                    "placeholders in ${file.parentFile?.name ?: file.path}/$key",
                    placeholders(text),
                    placeholders(actual.getValue(key)),
                )
            }
        }
    }

    @Test
    fun `generic provider text does not contain lkm lifecycle guidance`() {
        localeFiles(resourceRoot()).forEach { file ->
            val strings = parseStrings(file)
            val locale = file.parentFile?.name ?: file.path
            listOf(
                "susfs_ack_hint",
                "susfs_clone",
                "susfs_lkm",
                "susfs_lkm_only",
                "susfs_lkm_only_hint",
            ).forEach { key ->
                assertFalse("$locale/$key", "LKM" in strings.getValue(key))
            }
            assertEquals("Built-In", strings.getValue("susfs_provider_builtin"))
            assertEquals("LKM", strings.getValue("susfs_provider_lkm"))
            assertTrue(
                "$locale/susfs_lkm_ack_hint",
                "LKM" in strings.getValue("susfs_lkm_ack_hint"),
            )
        }
    }

    private fun resourceRoot(): File =
        sequenceOf(File("src/main/res"), File("app/src/main/res")).first(File::isDirectory)

    private fun localeFiles(root: File): List<File> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .map { File(it, "susfs.xml") }
        .filter(File::isFile)

    private fun parseStrings(file: File): Map<String, String> {
        assertTrue("missing ${file.path}", file.isFile)
        val entries = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .toList()
        assertEquals("duplicate keys in ${file.path}", entries.size, entries.map { it.groupValues[1] }.toSet().size)
        return entries.associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(value: String): List<String> =
        Regex("""%(?:\d+\$)?[a-zA-Z]""").findAll(value).map { it.value }.sorted().toList()
}
