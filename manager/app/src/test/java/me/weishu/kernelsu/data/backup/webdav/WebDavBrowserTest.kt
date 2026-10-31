package me.weishu.kernelsu.data.backup.webdav

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBrowserTest {

    private val baseUrl = "https://example.com/dav/FinaSU/"
    private val basePath = "/dav/FinaSU"

    private fun browser(transport: TestWebDavTransport) = WebDavBrowser(transport, baseUrl)

    @Test
    fun `list reports collections before files and hides the directory itself`() {
        val transport = TestWebDavTransport(basePath)
        transport.addCollection("/dav/FinaSU/boot")
        transport.files["/dav/FinaSU/manifest.json"] = "{}".toByteArray()

        val entries = runBlocking { browser(transport).list("") }.getOrThrow()

        assertEquals(listOf("boot", "manifest.json"), entries.map { it.name })
        assertTrue(entries.first().isCollection)
    }

    @Test
    fun `list of a subdirectory returns only its direct children`() {
        val transport = TestWebDavTransport(basePath)
        transport.files["/dav/FinaSU/boot/boot-stock-abc.img"] = ByteArray(16)
        transport.files["/dav/FinaSU/boot/boot-stock-abc.meta.json"] = "{}".toByteArray()
        transport.files["/dav/FinaSU/allowlist/a.json"] = "{}".toByteArray()

        val entries = runBlocking { browser(transport).list("boot") }.getOrThrow()

        assertEquals(
            listOf("boot/boot-stock-abc.img", "boot/boot-stock-abc.meta.json"),
            entries.map { it.relativePath },
        )
        assertEquals(16L, entries.first { it.name == "boot-stock-abc.img" }.sizeBytes)
    }

    @Test
    fun `list of a missing directory is empty`() {
        val entries = runBlocking { browser(TestWebDavTransport(basePath)).list("boot") }.getOrThrow()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `read bounds the preview to the requested number of bytes`() {
        val transport = TestWebDavTransport(basePath)
        transport.files["/dav/FinaSU/manifest.json"] = ByteArray(1000) { 'a'.code.toByte() }

        val preview = runBlocking { browser(transport).read("manifest.json", maxBytes = 32) }.getOrThrow()

        assertEquals(32, preview.size)
    }

    @Test
    fun `downloadTo streams the payload into the target file`() {
        val transport = TestWebDavTransport(basePath)
        val payload = ByteArray(32 * 1024) { (it % 97).toByte() }
        transport.files["/dav/FinaSU/boot/big.img"] = payload
        val target = File.createTempFile("finasu-browser-", ".img")

        runBlocking { browser(transport).downloadTo("boot/big.img", target) }.getOrThrow()

        assertArrayEquals(payload, target.readBytes())
        target.delete()
    }

    @Test
    fun `delete removes the remote file`() {
        val transport = TestWebDavTransport(basePath)
        transport.files["/dav/FinaSU/boot/x.img"] = "data".toByteArray()

        runBlocking { browser(transport).delete("boot/x.img") }.getOrThrow()

        assertFalse(transport.files.containsKey("/dav/FinaSU/boot/x.img"))
    }
}
