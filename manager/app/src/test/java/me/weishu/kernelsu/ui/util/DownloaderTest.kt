package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Update detection reads the version straight out of the release asset name, so the parser must
 * keep working for the names the release pipeline actually produces.
 */
class DownloaderTest {

    @Test
    fun `parses the released FinaSU asset name`() {
        assertEquals(
            "0.1.0-dev1" to 32817,
            parseReleaseAssetName("FinaSU_v0.1.0-dev1_32817-release.apk"),
        )
    }

    @Test
    fun `parses an asset name without a build type suffix`() {
        assertEquals(
            "1.0.0" to 33000,
            parseReleaseAssetName("FinaSU_v1.0.0_33000.apk"),
        )
    }

    @Test
    fun `ignores non-apk release assets`() {
        assertNull(parseReleaseAssetName("folkzygisk-source-1.0.tar.gz"))
        assertNull(parseReleaseAssetName("lkm-6.6.118_kernelsu.ko"))
    }

    @Test
    fun `rejects apk names without a version code`() {
        assertNull(parseReleaseAssetName("FinaSU-release.apk"))
    }
}
