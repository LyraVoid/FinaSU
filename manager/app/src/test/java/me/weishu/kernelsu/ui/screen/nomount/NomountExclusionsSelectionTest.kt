package me.weishu.kernelsu.ui.screen.nomount

import me.weishu.kernelsu.ui.viewmodel.AppSortConfig
import me.weishu.kernelsu.ui.viewmodel.AppSortType
import me.weishu.kernelsu.ui.viewmodel.ExclusionApp
import org.junit.Assert.assertEquals
import org.junit.Test

class NomountExclusionsSelectionTest {
    @Test
    fun inversionChangesOnlyVisibleUids() {
        assertEquals(setOf(2, 9), invertVisibleExclusions(setOf(1, 9), setOf(1, 2)))
    }

    @Test
    fun inversionWithNoResultsPreservesSelection() {
        assertEquals(setOf(9), invertVisibleExclusions(setOf(9), emptySet()))
    }

    @Test
    fun nameSortUsesUidAsStableTieBreaker() {
        val apps = listOf(app(3, "Same", "same"), app(2, "Same", "same"), app(1, "Same", "same"))

        assertEquals(
            listOf(1, 2, 3),
            sortExclusionApps(apps, AppSortConfig(sortType = AppSortType.NAME), emptySet()).map { it.uid },
        )
    }

    @Test
    fun activeExclusionsStayAheadEvenWhenSortIsReversed() {
        val apps = listOf(
            app(1, "Alpha", "alpha"),
            app(2, "Beta", "beta"),
            app(3, "Gamma", "gamma"),
        )

        assertEquals(
            listOf(2, 3, 1),
            sortExclusionApps(
                apps,
                AppSortConfig(sortType = AppSortType.NAME, reversed = true),
                setOf(2),
            ).map { it.uid },
        )
    }

    @Test
    fun selectedOnlyStillMatchesQueryAndUid() {
        val apps = listOf(app(1, "Alpha", "one"), app(2, "Beta", "two"))

        assertEquals(
            listOf(1),
            filterExclusionApps(apps, "alp", showSystemApps = false, selectedOnly = true, selected = setOf(1))
                .map { it.uid },
        )
    }

    private fun app(
        uid: Int,
        label: String,
        packageName: String,
    ) = ExclusionApp(
        uid = uid,
        label = label,
        packageName = packageName,
        isSystem = false,
        packageInfo = null,
    )
}
