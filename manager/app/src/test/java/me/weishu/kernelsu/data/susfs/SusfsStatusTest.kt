package me.weishu.kernelsu.data.susfs

import org.junit.Assert.*
import org.junit.Test

class SusfsStatusTest {
    @Test fun `version alone and unknown protocol never enable mutations`() {
        assertFalse(SusfsStatus(version = "v2.3.0").detected)
        val unknown = SusfsStatus(protocol = "unknown_protocol", version = "v9", features = listOf("CONFIG_KSU_SUSFS_SUS_PATH"))
        assertTrue(unknown.detected)
        assertFalse(unknown.supports("SUS_PATH"))
    }
    @Test fun `GKI is not an LKM extension marker`() {
        val builtin = SusfsStatus(protocol = "compatible", implementation = "builtin", variant = "GKI", procNodes = listOf("susfs_kstat"))
        assertFalse(builtin.proc("susfs_kstat"))
    }
    @Test fun `LKM without exposed proc controls disables extensions`() {
        val lkm = SusfsStatus(protocol = "compatible", implementation = "lkm", features = listOf("CONFIG_KSU_SUSFS_SUS_KSTAT"))
        assertTrue(lkm.supports("SUS_KSTAT"))
        assertFalse(lkm.proc("susfs_kstat"))
        assertTrue(lkm.copy(procNodes = listOf("susfs_kstat")).proc("susfs_kstat"))
    }
    @Test fun `root loss disables stale capabilities and recovery enables fresh ones`() {
        val ready = SusfsStatus(protocol = "compatible", features = listOf("CONFIG_KSU_SUSFS_SUS_PATH"))
        assertTrue(ready.supports("SUS_PATH"))
        assertFalse(ready.copy(protocol = "root_unavailable").supports("SUS_PATH"))
        assertFalse(ready.copy(error = "Invalid config").writable)
        assertTrue(ready.copy(ownership = "builtin", error = "Invalid config").canImport)
        assertFalse(ready.copy(ownership = "external", error = "Invalid config").canImport)
    }
    @Test fun `CLI status preserves saved versus applied`() {
        val status = SusfsStatus.parse("""{"detection":{"status":"compatible","version":"v2.3.0","variant":"GKI","implementation":"builtin","features":[],"proc_nodes":[]},"management":"saved","automatic":false,"config_error":null}""")
        assertEquals("saved", status.management)
        assertFalse(status.automatic)
    }
    @Test fun `unknown external and switching ownership never enable writes`() {
        val ready = SusfsStatus(protocol = "compatible", ownership = "builtin")
        assertTrue(ready.writable)
        assertFalse(ready.copy(ownership = "unknown").writable)
        assertFalse(ready.copy(ownership = "external").writable)
        assertFalse(ready.copy(pendingOwner = "external").writable)
    }
    @Test fun `malformed ownership response is read only`() {
        val base = """{"detection":{"status":"compatible","version":"v2.3.0","variant":"GKI","implementation":"builtin","features":[],"proc_nodes":[]},"automatic":false,"config_error":null,"management":"saved","ownership":%s}"""
        for (ownership in listOf("{}", """{"mode":"builtin"}""", """{"mode":"builtin","pending":{}}""")) {
            assertFalse(SusfsStatus.parse(base.format(ownership)).writable)
        }
        assertTrue(SusfsStatus.parse(base.format("""{"mode":"builtin","pending":null}""")).writable)
    }
    @Test fun `CLI status preserves idle and deferred management states`() {
        val base = """{"detection":{"status":"compatible","version":"v2.3.0","variant":"GKI","implementation":"builtin","features":[],"proc_nodes":[]},"automatic":false,"config_error":null,"management":"%s"}"""
        assertEquals("idle", SusfsStatus.parse(base.format("idle")).management)
        assertEquals("deferred", SusfsStatus.parse(base.format("deferred")).management)
        assertEquals("partial", SusfsStatus.parse(base.format("partial")).management)
        assertEquals("skipped", SusfsStatus.parse(base.format("skipped")).management)
    }

    @Test fun `capability profile drives verified controls`() {
        val status = SusfsStatus.parse("""
            {
              "detection":{"status":"compatible","version":"v2.3.0","variant":"GKI-LKM",
                "implementation":"lkm","features":["SUSFS_LKM_MODULES"],"proc_nodes":["susfs_kstat"]},
              "automatic":false,"config_error":null,"management":"saved",
              "ownership":{"mode":"builtin","pending":null},
              "receipt":{"status":"partial","applied":["path:/a"],
                "failures":{"static_kstat:/b":"provider unavailable"},
                "skipped":{"map:/c":"unsupported"},"deferred":["uname"],
                "pending_reboot":["kstat:/d"]},
              "schema1_backup_available":true,
              "capabilities":{
                "supported":["path","dynamic_kstat","full_clone","static_kstat","redirect","mount_prefixes"],
                "static_kstat_fields":["size","ctime_sec"],
                "redirect_uid_schemes":[0,1,2]
              }
            }
        """)
        assertTrue(status.capabilities.path)
        assertFalse(status.capabilities.map)
        assertTrue(status.capabilities.fullClone)
        assertTrue(status.staticKstatSupported)
        assertEquals(listOf("size", "ctime_sec"), status.capabilities.staticKstatFields)
        assertEquals(listOf(0, 1, 2), status.capabilities.redirectUidSchemes)
        assertFalse(status.capabilities.hiddenModules)
        assertTrue(status.capabilities.mountPrefixes)
        assertEquals("partial", status.receipt?.status)
        assertEquals(listOf("path:/a"), status.receipt?.applied)
        assertEquals(listOf("kstat:/d"), status.receipt?.pendingReboot)
        assertTrue(status.schema1BackupAvailable)
    }

    @Test fun `legacy status derives the previously verified static fields`() {
        val status = SusfsStatus.parse("""
            {
              "detection":{"status":"compatible","version":"v2.3.0","variant":"GKI-LKM",
                "implementation":"lkm","features":["CONFIG_KSU_SUSFS_SUS_PATH"],
                "proc_nodes":["susfs_kstat"]},
              "automatic":false,"config_error":null,"management":"saved",
              "ownership":{"mode":"builtin","pending":null},
              "static_kstat_supported":true
            }
        """)
        assertTrue(status.capabilities.path)
        assertTrue(status.staticKstatSupported)
        assertEquals(listOf("size", "ctime_sec"), status.capabilities.staticKstatFields)
    }
}
