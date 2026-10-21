package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadReceiptTrackerTest {
    private class Clock {
        var now = 0L
    }

    private fun tracker(clock: Clock) = ReadReceiptTracker({ clock.now })

    @Test
    fun `half visible for 1500 ms becomes read`() {
        val clock = Clock()
        val tracker = tracker(clock)
        clock.now = 10_000
        assertTrue(tracker.onSample(mapOf(1L to 0.5f), resumed = true, expanded = emptySet()).isEmpty())
        clock.now = 11_499
        assertTrue(tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet()).isEmpty())
        clock.now = 11_500
        assertEquals(setOf(1L), tracker.onSample(mapOf(1L to 0.5f), resumed = true, expanded = emptySet()))
    }

    @Test
    fun `forty nine percent never counts`() {
        val clock = Clock()
        val tracker = tracker(clock)
        clock.now = 0
        tracker.onSample(mapOf(1L to 0.49f), resumed = true, expanded = emptySet())
        clock.now = 10_000
        assertTrue(tracker.onSample(mapOf(1L to 0.49f), resumed = true, expanded = emptySet()).isEmpty())
    }

    @Test
    fun `scrolling away resets the dwell`() {
        val clock = Clock()
        val tracker = tracker(clock)
        clock.now = 0
        tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet())
        clock.now = 1_000
        tracker.onSample(mapOf(1L to 0.1f), resumed = true, expanded = emptySet())
        clock.now = 2_400
        assertTrue(tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet()).isEmpty())
        clock.now = 2_400 + 1_499
        assertTrue(tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet()).isEmpty())
        clock.now = 2_400 + 1_500
        assertEquals(setOf(1L), tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet()))
    }

    @Test
    fun `leaving the sample resets the dwell`() {
        val clock = Clock()
        val tracker = tracker(clock)
        tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet())
        clock.now = 1_000
        tracker.onSample(emptyMap(), resumed = true, expanded = emptySet())
        clock.now = 2_000
        assertTrue(tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet()).isEmpty())
    }

    @Test
    fun `expand marks read immediately`() {
        val tracker = tracker(Clock())
        assertEquals(setOf(7L), tracker.onSample(emptyMap(), resumed = true, expanded = setOf(7L)))
        assertTrue(tracker.onSample(emptyMap(), resumed = true, expanded = setOf(7L)).isEmpty())
    }

    @Test
    fun `a paused activity does not mark read and clears the timer`() {
        val clock = Clock()
        val tracker = tracker(clock)
        tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet())
        clock.now = 1_000
        assertTrue(tracker.onSample(mapOf(1L to 1f), resumed = false, expanded = setOf(1L)).isEmpty())
        clock.now = 1_000 + 1_500
        assertTrue(tracker.onSample(mapOf(1L to 1f), resumed = true, expanded = emptySet()).isEmpty())
    }
}
