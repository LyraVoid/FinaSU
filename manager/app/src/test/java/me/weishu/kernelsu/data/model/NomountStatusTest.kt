package me.weishu.kernelsu.data.model

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NomountStatusTest {

    @Test
    fun `injected requires a verified data path`() {
        val base = JSONObject()
            .put("schema_version", 2)
            .put("driver_state", "ready")
            .put("api_ok", true)
            .put("rules_expected", 1)
            .put("rules_applied", 1)
            .put("data_verified", "/system/etc/hosts -> /data/adb/modules/m/hosts")

        assertTrue(NomountStatus.fromJson(base).injected)
        assertFalse(NomountStatus.fromJson(JSONObject(base.toString()).apply {
            put("data_verified", "no")
            put("data_error", "contents differ")
        }).injected)
    }
}
