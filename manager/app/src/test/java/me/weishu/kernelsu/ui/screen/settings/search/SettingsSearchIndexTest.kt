package me.weishu.kernelsu.ui.screen.settings.search

import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.settings.SettingsCategory
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The index is the only list of searchable rows, so it is also the only place a mistake can hide a
 * setting from the search.
 */
class SettingsSearchIndexTest {

    @Test
    fun `ids are unique`() {
        val ids = settingsSearchIndex.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every row can be drawn and titled`() {
        assertTrue(settingsSearchIndex.all { it.titleRes != 0 })
        assertTrue(settingsSearchIndex.all { it.id.isNotBlank() })
    }

    @Test
    fun `every category is searchable`() {
        SettingsCategory.entries.forEach { category ->
            assertNotEquals(0, settingsSearchIndex.count { it.category == category })
        }
    }

    @Test
    fun `rows the hub owns are filed under the settings root`() {
        val hubRows = settingsSearchIndex.filter { it.category == null }
        assertTrue(hubRows.isNotEmpty())
        assertTrue(hubRows.all { it.parentTitleRes == R.string.settings })
    }

    @Test
    fun `a category row points back at its own category`() {
        settingsSearchIndex
            .filter { it.target is SettingsSearchTarget.Category }
            .forEach { entry ->
                val target = entry.target as SettingsSearchTarget.Category
                assertNotEquals(null, entry.category)
                assertEquals(entry.category?.key, target.key)
            }
    }

    @Test
    fun `UTS View is searchable from functions`() {
        val entry = settingsSearchIndex.single {
            it.target == SettingsSearchTarget.Page(Route.UtsView)
        }
        assertEquals(SettingsCategory.FUNCTION, entry.category)
    }

    @Test
    fun `network isolation is searchable from functions`() {
        val entry = settingsSearchIndex.single {
            it.target == SettingsSearchTarget.Page(Route.NetIsolate)
        }
        assertEquals(SettingsCategory.FUNCTION, entry.category)
    }

    @Test
    fun `kernel build hide row is searchable only when the kernel reports it`() {
        val entry = settingsSearchIndex.single { it.id == "appearance.hide_home_kernel_full_version" }
        assertFalse(entry.available(SettingsUiState(kernelFullVersionAvailable = false)))
        assertTrue(entry.available(SettingsUiState(kernelFullVersionAvailable = true)))
    }
}
