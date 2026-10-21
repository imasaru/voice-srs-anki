package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsRateTest {

    @Test
    fun `default is 0_9`() {
        assertEquals(0.9f, TtsRate.DEFAULT, 0.001f)
        assertEquals(4, TtsRate.toProgress(TtsRate.DEFAULT))
        assertEquals(0.9f, TtsRate.fromProgress(4), 0.001f)
        assertFalse(TtsRate.isCustom(TtsRate.DEFAULT))
    }

    @Test
    fun `slider runs from 0_5 to 2_0 and pins above that`() {
        assertEquals(0.5f, TtsRate.SLIDER_MAX - TtsRate.STEPS * TtsRate.STEP, 0.001f)
        assertEquals(0.5f, TtsRate.fromProgress(0), 0.001f)
        assertEquals(2.0f, TtsRate.fromProgress(TtsRate.STEPS), 0.001f)
        assertEquals(1.5f, TtsRate.fromProgress(10), 0.001f)
        assertEquals(0, TtsRate.toProgress(0.5f))
        assertEquals(10, TtsRate.toProgress(1.5f))
        assertEquals(TtsRate.STEPS, TtsRate.toProgress(2.0f))
        assertEquals(TtsRate.STEPS, TtsRate.toProgress(2.6f))
        assertEquals(TtsRate.STEPS, TtsRate.toProgress(4.0f))
        assertEquals(0, TtsRate.toProgress(-1f))
        assertEquals(TtsRate.STEPS, TtsRate.toProgress(9f))
        assertEquals(0.5f, TtsRate.fromProgress(-4), 0.001f)
        assertEquals(2.0f, TtsRate.fromProgress(40), 0.001f)
        assertFalse(TtsRate.isCustom(2.0f))
        assertTrue(TtsRate.isCustom(2.1f))
        assertTrue(TtsRate.isCustom(2.6f))
    }

    @Test
    fun `every slider step round-trips inside the slider range`() {
        for (progress in 0..TtsRate.STEPS) {
            val rate = TtsRate.fromProgress(progress)
            assertEquals(progress, TtsRate.toProgress(rate))
            assertTrue(rate in TtsRate.MIN..TtsRate.SLIDER_MAX)
            assertFalse(TtsRate.isCustom(rate))
        }
    }

    @Test
    fun `stored rate clamps to 0_5 through 4_0`() {
        assertEquals(4.0f, TtsRate.MAX, 0.001f)
        assertEquals(TtsRate.MIN, TtsRate.clamp(0.1f), 0.001f)
        assertEquals(3.0f, TtsRate.clamp(3f), 0.001f)
        assertEquals(TtsRate.MAX, TtsRate.clamp(4.2f), 0.001f)
        assertEquals(TtsRate.DEFAULT, TtsRate.clamp(Float.NaN), 0.001f)
        assertEquals(TtsRate.DEFAULT, TtsRate.clamp(Float.POSITIVE_INFINITY), 0.001f)
        assertTrue(TtsRate.isCustom(TtsRate.clamp(9f)))
    }

    @Test
    fun `parse accepts 0_5 to 4_0 and rejects the rest`() {
        assertEquals(2.6f, TtsRate.parse("2.6")!!, 0.001f)
        assertEquals(2.6f, TtsRate.parse("2,6")!!, 0.001f)
        assertEquals(0.5f, TtsRate.parse("0.5")!!, 0.001f)
        assertEquals(0.5f, TtsRate.parse(".5")!!, 0.001f)
        assertEquals(4.0f, TtsRate.parse("4")!!, 0.001f)
        assertEquals(4.0f, TtsRate.parse("4.0")!!, 0.001f)
        assertEquals(1.3f, TtsRate.parse("1.25")!!, 0.001f)
        assertEquals(2.0f, TtsRate.parse("+2")!!, 0.001f)
        assertEquals("2.6", TtsRate.format(2.64f))
        assertEquals("0.9", TtsRate.format(TtsRate.DEFAULT))
        assertNull(TtsRate.parse(""))
        assertNull(TtsRate.parse("  "))
        assertNull(TtsRate.parse("abc"))
        assertNull(TtsRate.parse("2.6x"))
        assertNull(TtsRate.parse("0.4"))
        assertNull(TtsRate.parse("0.44"))
        assertNull(TtsRate.parse("4.1"))
        assertNull(TtsRate.parse("4.04"))
        assertNull(TtsRate.parse("4.06"))
        assertNull(TtsRate.parse("-1"))
        assertNull(TtsRate.parse("1e1"))
    }
}
