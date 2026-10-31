package me.weishu.kernelsu.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolkMountTest {
    @Test
    fun `warning code is parsed and old payloads remain readable`() {
        val current = FolkMountStatus.fromJson(
            JSONObject(
                """
                {
                  "schema_version": 2,
                  "configured_mode": "nomount",
                  "boot_warning": "diagnostic text",
                  "boot_warning_code": "nomount_ui_fallback"
                }
                """.trimIndent()
            )
        )
        val legacy = FolkMountStatus.fromJson(
            JSONObject("""{"schema_version":2,"configured_mode":"nomount"}""")
        )

        assertEquals("nomount_ui_fallback", current.bootWarningCode)
        assertNull(legacy.bootWarningCode)
    }
}
