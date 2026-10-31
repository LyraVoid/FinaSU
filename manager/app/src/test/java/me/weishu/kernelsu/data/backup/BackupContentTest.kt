package me.weishu.kernelsu.data.backup

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackupContentTest {

    @Test
    fun `bytes payload reports size hash and content`() {
        val bytes = "hello finasu".toByteArray()
        val content = BackupContent.Bytes(bytes)

        assertEquals(bytes.size.toLong(), content.sizeBytes)
        assertEquals(bytes.sha256(), content.sha256())
        assertArrayEquals(bytes, content.readBytes())
    }

    @Test
    fun `file payload streams identical content`() {
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val file = File.createTempFile("content", ".img").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        val content = BackupContent.FileRef(file)

        assertEquals(bytes.size.toLong(), content.sizeBytes)
        assertEquals(bytes.sha256(), content.sha256())
        assertArrayEquals(bytes, content.readBytes())
        assertSame(file, content.file())
    }

    @Test
    fun `artifact resolves a file-backed payload`() {
        val file = File.createTempFile("artifact", ".img").apply {
            writeBytes("payload".toByteArray())
            deleteOnExit()
        }
        val artifact = BackupArtifact(
            kind = BackupKind.BOOT,
            moduleId = null,
            fileName = "boot-stock-x.img",
            metaFileName = null,
            sha256 = "0".repeat(64),
            metaJson = null,
            file = file,
        )

        assertEquals(file.length(), artifact.content.sizeBytes)
    }
}
