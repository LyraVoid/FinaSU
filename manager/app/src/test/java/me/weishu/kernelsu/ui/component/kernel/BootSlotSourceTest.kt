package me.weishu.kernelsu.ui.component.kernel

import me.weishu.kernelsu.data.boot.testKernelFacts
import me.weishu.kernelsu.data.boot.testKernelSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootSlotSourceTest {
    @Test
    fun `different slots remain distinct`() {
        val values = distinctKernelValues(
            testKernelFacts(
                testKernelSlot("a", "6.6.30-a", "#1 A", "hash-a", current = true),
                testKernelSlot("b", "6.6.30-b", "#1 B", "hash-b"),
            ),
        )

        assertEquals(2, values.size)
    }

    @Test
    fun `same slots collapse and incomplete slots are omitted`() {
        val values = distinctKernelValues(
            testKernelFacts(
                testKernelSlot("a", "6.6.30", "#1", "hash-a", current = true),
                testKernelSlot("b", "6.6.30", "#1", "hash-b"),
                testKernelSlot("c", null, "#1", "hash-c", exists = false, readable = false),
            ),
        )

        assertEquals(listOf("6.6.30" to "#1"), values)
    }

    @Test
    fun `only one complete slot is still offered`() {
        val values = distinctKernelValues(
            testKernelFacts(
                testKernelSlot("a", "6.6.30-a", "#1 A", "hash-a", current = true),
                testKernelSlot("b", null, null, "hash-b", readable = false, reason = "unreadable"),
            ),
        )

        assertEquals(listOf("6.6.30-a" to "#1 A"), values)
    }

    @Test
    fun `source identity changes when either image hash changes`() {
        val old = testKernelSlot("a", "6.6.30", "#1", "old", current = true)
        val new = testKernelSlot("a", "6.6.30", "#1", "new", current = true)

        assertTrue(old.sourceIdentity != new.sourceIdentity)
    }

    @Test
    fun `current slot change selects the new current slot but same refresh preserves choice`() {
        val slots = listOf("a", "b")

        assertEquals("b", nextSlotSelection("b", "a", "a", slots))
        assertEquals("b", nextSlotSelection("a", "a", "b", slots))
        assertEquals("a", nextSlotSelection("a", null, null, slots))
    }
}
