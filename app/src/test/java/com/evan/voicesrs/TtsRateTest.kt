package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsRateTest {

    @Test
    fun `default is 0_9`() {
        assertEquals(0.9f, TtsRate.DEFAULT, 0.001f)
        assertEquals(4, TtsRate.toProgress(TtsRate.DEFAULT))
        assertEquals(0.9f, TtsRate.fromProgress(4), 0.001f)
    }

    @Test
    fun `ends of the slider are 0_5 and 1_5`() {
        assertEquals(0.5f, TtsRate.fromProgress(0), 0.001f)
        assertEquals(1.5f, TtsRate.fromProgress(TtsRate.STEPS), 0.001f)
        assertEquals(0, TtsRate.toProgress(0.5f))
        assertEquals(10, TtsRate.toProgress(1.5f))
    }

    @Test
    fun `every step round-trips inside the range`() {
        for (progress in 0..TtsRate.STEPS) {
            val rate = TtsRate.fromProgress(progress)
            assertEquals(progress, TtsRate.toProgress(rate))
            assertTrue(rate in TtsRate.MIN..TtsRate.MAX)
        }
    }

    @Test
    fun `out of range values clamp`() {
        assertEquals(TtsRate.MIN, TtsRate.clamp(0.1f), 0.001f)
        assertEquals(TtsRate.MAX, TtsRate.clamp(3f), 0.001f)
        assertEquals(TtsRate.DEFAULT, TtsRate.clamp(Float.NaN), 0.001f)
        assertEquals(0, TtsRate.toProgress(-1f))
        assertEquals(10, TtsRate.toProgress(9f))
        assertEquals(0.5f, TtsRate.fromProgress(-4), 0.001f)
        assertEquals(1.5f, TtsRate.fromProgress(40), 0.001f)
    }
}
