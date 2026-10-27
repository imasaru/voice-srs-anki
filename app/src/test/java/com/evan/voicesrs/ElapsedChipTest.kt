package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElapsedChipTest {

    @Test
    fun `format renders minutes and seconds`() {
        assertEquals("Elapsed 0:00", ElapsedChip.format(0))
        assertEquals("Elapsed 1:05", ElapsedChip.format(65_000))
        assertEquals("Elapsed 59:59", ElapsedChip.format(3_599_000))
    }

    @Test
    fun `format switches to hours past an hour`() {
        assertEquals("Elapsed 1:00:00", ElapsedChip.format(3_600_000))
        assertEquals("Elapsed 2:03:04", ElapsedChip.format(7_384_000))
    }

    @Test
    fun `format never shows a negative duration`() {
        assertEquals("Elapsed 0:00", ElapsedChip.format(-1_000))
    }

    @Test
    fun `visible follows the drill running flag`() {
        assertTrue(ElapsedChip.visible(drillRunning = true))
        assertFalse(ElapsedChip.visible(drillRunning = false))
    }

    @Test
    fun `elapsed counts up while running`() {
        assertEquals(4_000L, ElapsedChip.elapsed(startMs = 1_000, pausedTotalMs = 0, pausedSinceMs = 0, nowMs = 5_000))
    }

    @Test
    fun `elapsed freezes while paused`() {
        // 4s of wall time since start, but 1s of it is the paused span, so 3s stays.
        assertEquals(3_000L, ElapsedChip.elapsed(startMs = 1_000, pausedTotalMs = 0, pausedSinceMs = 4_000, nowMs = 5_000))
    }

    @Test
    fun `elapsed keeps the paused span subtracted after resume`() {
        // pausedTotal already holds the 1s pause; 1s more of running since resume = 4s total.
        assertEquals(4_000L, ElapsedChip.elapsed(startMs = 1_000, pausedTotalMs = 1_000, pausedSinceMs = 0, nowMs = 6_000))
    }

    @Test
    fun `elapsed is zero before the drill starts`() {
        assertEquals(0L, ElapsedChip.elapsed(startMs = 0, pausedTotalMs = 0, pausedSinceMs = 0, nowMs = 999_999))
    }

    @Test
    fun `elapsed never goes negative`() {
        assertEquals(0L, ElapsedChip.elapsed(startMs = 5_000, pausedTotalMs = 0, pausedSinceMs = 0, nowMs = 1_000))
    }
}
