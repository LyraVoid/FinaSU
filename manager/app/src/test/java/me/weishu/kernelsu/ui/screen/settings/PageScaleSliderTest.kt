package me.weishu.kernelsu.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PageScaleSliderTest {
    private val step =
        (PAGE_SCALE_MAX - PAGE_SCALE_MIN) / (PAGE_SCALE_DISCRETE_STEPS + 1)

    @Test
    fun discreteScaleUsesTwoPercentSteps() {
        assertEquals(0.02f, step, 0.0001f)
    }

    @Test
    fun hundredPercentIsReachable() {
        assertEquals(1f, PAGE_SCALE_MIN + 10 * step, 0.0001f)
    }
}
