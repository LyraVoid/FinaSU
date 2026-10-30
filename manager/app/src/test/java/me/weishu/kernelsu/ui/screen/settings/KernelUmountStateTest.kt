package me.weishu.kernelsu.ui.screen.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelUmountStateTest {
    @Test
    fun changesRequireAReadableUnmanagedValueAndNoWriteInProgress() {
        assertTrue(canChangeKernelUmount("supported", 0, false))
        assertTrue(canChangeKernelUmount("supported", 1, false))
        assertFalse(canChangeKernelUmount("supported", null, false))
        assertFalse(canChangeKernelUmount("supported", 1, true))
        for (status in listOf("managed", "forced", "unsupported", "")) {
            assertFalse(canChangeKernelUmount(status, 1, false))
        }
    }
}
