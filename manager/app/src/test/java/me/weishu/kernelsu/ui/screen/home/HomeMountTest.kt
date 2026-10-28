package me.weishu.kernelsu.ui.screen.home

import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.data.model.FolkMountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeMountTest {

    @Test
    fun `boot provider wins over next provider`() {
        val status = status(
            nextProvider = FolkMountProvider.NOMOUNT,
            bootProvider = FolkMountProvider.BUILTIN,
        )

        assertEquals(FolkMountProvider.BUILTIN, resolveHomeMountProvider(status))
    }

    @Test
    fun `missing boot provider falls back to next provider`() {
        val status = status(
            nextProvider = FolkMountProvider.METAMODULE,
            bootProvider = null,
        )

        assertEquals(FolkMountProvider.METAMODULE, resolveHomeMountProvider(status))
    }

    @Test
    fun `skipped boot reports mounting off`() {
        val status = status(
            nextProvider = FolkMountProvider.BUILTIN,
            bootProvider = null,
            bootResult = "skipped",
        )

        assertEquals(FolkMountProvider.NONE, resolveHomeMountProvider(status))
    }

    @Test
    fun `missing status hides the row`() {
        assertNull(resolveHomeMountProvider(null))
    }

    @Test
    fun `reselecting NoMount retries a fallback but not a healthy boot`() {
        val fallback = status(
            configuredMode = FolkMountMode.NOMOUNT,
            nextProvider = FolkMountProvider.NOMOUNT,
            bootProvider = FolkMountProvider.BUILTIN,
        )
        assert(fallback.shouldWrite(FolkMountMode.NOMOUNT))

        val healthy = status(
            configuredMode = FolkMountMode.NOMOUNT,
            nextProvider = FolkMountProvider.NOMOUNT,
            bootProvider = FolkMountProvider.NOMOUNT,
        )
        assert(!healthy.shouldWrite(FolkMountMode.NOMOUNT))
    }

    @Test
    fun `changing the configured mode still writes`() {
        val status = status(
            configuredMode = FolkMountMode.BUILTIN,
            nextProvider = FolkMountProvider.BUILTIN,
            bootProvider = FolkMountProvider.BUILTIN,
        )
        assert(status.shouldWrite(FolkMountMode.NOMOUNT))
    }

    private fun status(
        configuredMode: FolkMountMode = FolkMountMode.AUTO,
        nextProvider: FolkMountProvider?,
        bootProvider: FolkMountProvider?,
        bootResult: String = "success",
    ) = FolkMountStatus(
        configuredMode = configuredMode,
        nextProvider = nextProvider,
        metamoduleId = null,
        metamoduleEnabled = false,
        bootId = "boot",
        bootProvider = bootProvider,
        bootResult = bootResult,
        targetCount = null,
        bootWarning = null,
        error = null,
    )
}
