package me.weishu.kernelsu.ui.util

import me.weishu.kernelsu.data.model.FeatureChange
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureChangesTest {
    @Test
    fun `parses external feature changes and ignores malformed output`() {
        val output = """
            {
              "boot_id": "boot",
              "stage": "post-fs-data",
              "changes": [
                {"id": 1, "name": "kernel_umount", "expected": 1, "actual": 0}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(FeatureChange(1, "kernel_umount", 1, 0)),
            parseFeatureChanges(output),
        )
        assertEquals(emptyList<FeatureChange>(), parseFeatureChanges("not json"))
    }
}
