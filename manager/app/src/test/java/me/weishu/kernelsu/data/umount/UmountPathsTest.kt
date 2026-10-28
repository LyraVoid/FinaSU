package me.weishu.kernelsu.data.umount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import me.weishu.kernelsu.ui.viewmodel.UmountPathsUiState

class UmountPathsTest {
    private val config = listOf(
        UmountPathEntry("/data/local/tmp/custom", 2),
    )
    private val kernel = listOf(
        UmountPathEntry("/data/local/tmp/module", 2),
        UmountPathEntry("/data/local/tmp/custom", 2),
    )

    @Test
    fun `json parser reads both list fields`() {
        assertEquals(
            listOf(UmountPathEntry("/a", 2), UmountPathEntry("/b", 0)),
            UmountPathsData.parse("""[{"path":"/a","flags":2},{"path":"/b","flags":0}]"""),
        )
    }

    @Test
    fun `merge groups custom and module entries`() {
        val snapshot = UmountPathsData.merge(config, kernel)
        assertTrue(snapshot.configReadable)
        assertTrue(snapshot.kernelSupported)
        assertFalse(snapshot.readError)
        assertEquals(listOf("/data/local/tmp/custom"), snapshot.custom.map { it.path })
        assertEquals(listOf("/data/local/tmp/module"), snapshot.kernelEntries.map { it.path })
        assertTrue(snapshot.unknown.isEmpty())
    }

    @Test
    fun `old kernel keeps configured entries but is visibly unsupported`() {
        val snapshot = UmountPathsData.merge(config, null)
        assertFalse(snapshot.kernelSupported)
        assertTrue(snapshot.configReadable)
        assertFalse(snapshot.readError)
        assertEquals(listOf("/data/local/tmp/custom"), snapshot.custom.map { it.path })
    }

    @Test
    fun `unreadable config makes kernel entries unknown and read only`() {
        val snapshot = UmountPathsData.merge(null, kernel)
        assertFalse(snapshot.configReadable)
        assertTrue(snapshot.custom.isEmpty())
        assertTrue(snapshot.kernelEntries.isEmpty())
        assertEquals(kernel.map { it.path }.toSet(), snapshot.unknown.map { it.path }.toSet())
    }

    @Test
    fun `failed refresh keeps previous data`() {
        val previous = listOf(
            UmountPath("/data/local/tmp/custom", 2, UmountPathSource.CUSTOM),
        )
        val snapshot = UmountPathsData.merge(null, null, previous)
        assertTrue(snapshot.readError)
        assertEquals(previous.map { it.path }, snapshot.paths.map { it.path })
        assertTrue(snapshot.paths.all { it.source == UmountPathSource.UNKNOWN })
    }

    @Test
    fun `path validation covers every boundary`() {
        assertNull(UmountPathsData.validatePath("/system/etc/security/cacerts/ca.0"))
        assertEquals(
            UmountPathError.NOT_ABSOLUTE,
            UmountPathsData.validatePath("relative"),
        )
        assertEquals(UmountPathError.ROOT, UmountPathsData.validatePath("/"))
        assertEquals(UmountPathError.FORBIDDEN, UmountPathsData.validatePath("/apex"))
        assertEquals(
            UmountPathError.TRAILING_SLASH,
            UmountPathsData.validatePath("/a/"),
        )
        assertEquals(
            UmountPathError.EMPTY_SEGMENT,
            UmountPathsData.validatePath("/a//b"),
        )
        assertEquals(UmountPathError.DOT_SEGMENT, UmountPathsData.validatePath("/a/../b"))
        assertEquals(
            UmountPathError.CONTROL_CHARACTER,
            UmountPathsData.validatePath("/a/\n/b"),
        )
        assertEquals(
            UmountPathError.TOO_LONG,
            UmountPathsData.validatePath("/" + "a".repeat(255)),
        )
    }

    @Test
    fun `commands quote shell metacharacters`() {
        assertEquals(
            "umount-config add '/data/local/tmp/a'\"'\"'b' --flags 2",
            UmountPathsData.addCommand("/data/local/tmp/a'b"),
        )
        assertEquals(
            "umount-config del '/data/local/tmp/\$(reboot)'",
            UmountPathsData.deleteCommand("/data/local/tmp/\$(reboot)"),
        )
    }

    @Test
    fun `writes stay disabled before load and when config is unreadable`() {
        assertFalse(UmountPathsUiState().canWrite)
        assertFalse(
            UmountPathsUiState(
                hasLoaded = true,
                configReadable = false,
            ).canWrite
        )
        assertTrue(
            UmountPathsUiState(
                hasLoaded = true,
                configReadable = true,
                kernelSupported = false,
            ).canWrite
        )
    }
}
