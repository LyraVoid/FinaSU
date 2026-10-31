package me.weishu.kernelsu.data.susfs

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SusfsPresentationTest {
    @Test fun `receipt separates acknowledgements errors and reboot requests`() {
        val report = SusfsApplyReport.parse("""{"status":"partial","applied":["path:/a"],"failures":{"uname":"SUSFS returned error -22"},"skipped":{"avc_spoof":"Capability unavailable"},"deferred":["cmdline"],"pending_reboot":["kstat:/b"]}""")
        assertEquals(listOf("path:/a"), report.applied)
        assertEquals("SUSFS returned error -22", report.failures["uname"])
        assertEquals("Capability unavailable", report.skipped["avc_spoof"])
        assertEquals(listOf("cmdline"), report.deferred)
        assertEquals(listOf("kstat:/b"), report.pendingReboot)
        assertEquals("partial", report.status)
    }
    @Test fun `legacy receipt without skipped remains readable`() {
        val report = SusfsApplyReport.parse("""{"status":"applied","applied":[],"failures":{},"pending_reboot":[]}""")
        assertTrue(report.skipped.isEmpty())
    }
    @Test fun `paths and uname count UTF8 bytes not displayed characters`() {
        assertTrue(SusfsInputs.validPath("/data/local/tmp/test"))
        listOf("/", "relative", "/a/../b", "/a//b", "/a b", "/a\n").forEach { assertFalse(SusfsInputs.validPath(it)) }
        assertFalse(SusfsInputs.validPath("/" + "界".repeat(85)))
        assertTrue(SusfsInputs.validText("default", 65))
        assertFalse(SusfsInputs.validText("", 65))
        assertFalse(SusfsInputs.validText("界".repeat(22), 65))
    }
    @Test fun `cmdline accepts bootconfig lines but rejects other controls`() {
        assertTrue(SusfsInputs.validCmdline("androidboot.hardware=qcom\nandroidboot.memcg=1"))
        assertFalse(SusfsInputs.validCmdline("androidboot.memcg=1\u0000"))
        assertFalse(SusfsInputs.validCmdline("a".repeat(8192)))
    }
    @Test fun `editing fresh config preserves unrelated rules despite reordering`() {
        val original = """{"kind":"path","path":"/a","looping":false}"""
        val config = JSONObject("""{"rules":[{"kind":"map","path":"/b"},$original]}""")
        SusfsInputs.replaceRule(config, original, JSONObject("""{"kind":"path","path":"/a","looping":true}"""))
        assertEquals("/b", config.getJSONArray("rules").getJSONObject(0).getString("path"))
        assertTrue(config.getJSONArray("rules").getJSONObject(1).getBoolean("looping"))
    }
    @Test fun `stale edits cannot overwrite a concurrent change`() {
        val original = """{"kind":"path","path":"/a","looping":false}"""
        val config = JSONObject("""{"rules":[{"kind":"path","path":"/a","looping":true}]}""")
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.replaceRule(config, original, null) }
        assertEquals(1, config.getJSONArray("rules").length())
    }
    @Test fun `nested static fields are compared by value`() {
        val original = """{"kind":"static_kstat","path":"/a","fields":{"size":3}}"""
        val config = JSONObject("""{"rules":[$original]}""")
        SusfsInputs.replaceRule(config, original, JSONObject("""{"kind":"static_kstat","path":"/a","fields":{"size":4}}"""))
        assertEquals(4, config.getJSONArray("rules").getJSONObject(0).getJSONObject("fields").getInt("size"))
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.replaceRule(config, original, null) }
    }
    @Test fun `import preview never implies complete daemon validation`() {
        val json = """{"schema":1,"automatic":true,"settings":{},"rules":[],"lkm":{"hidden_modules":[],"mount_prefixes":[]},"unknown":1}"""
        assertTrue(SusfsInputs.preview(json).getBoolean("automatic"))
        assertEquals(2, SusfsInputs.preview(json.replace("\"schema\":1", "\"schema\":2")).getInt("schema"))
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.preview(json.replace("\"schema\":1", "\"schema\":3")) }
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.preview(" ".repeat(128 * 1024 + 1)) }
    }
    @Test fun `batch path rules ignore blanks and preserve shared options`() {
        val rules = SusfsInputs.batchRules("path", " /a \n\n/b ", looping = true)
        assertEquals(listOf("/a", "/b"), rules.map { it.getString("path") })
        assertTrue(rules.all { it.getBoolean("looping") })
    }
    @Test fun `batch redirect rules require source destination pairs`() {
        val rules = SusfsInputs.batchRules("redirect", "/a -> /b\n/c -> /d", uidScheme = 2)
        assertEquals(listOf("/a", "/c"), rules.map { it.getString("path") })
        assertEquals(listOf("/b", "/d"), rules.map { it.getString("destination") })
        assertTrue(rules.all { it.getInt("uid_scheme") == 2 })
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.batchRules("redirect", "/a") }
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.batchRules("redirect", "a -> /b") }
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.batchRules("redirect", "/a -> /a") }
    }
    @Test fun `batch additions are atomic and reject duplicates`() {
        val config = JSONObject("""{"rules":[{"kind":"path","path":"/a"}]}""")
        SusfsInputs.addRules(config, SusfsInputs.batchRules("path", "/b\n/c"))
        assertEquals(3, config.getJSONArray("rules").length())
        assertThrows(IllegalArgumentException::class.java) {
            SusfsInputs.addRules(config, SusfsInputs.batchRules("path", "/c\n/d"))
        }
        assertEquals(3, config.getJSONArray("rules").length())
    }
}
