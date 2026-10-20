package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenHealthTrackerTest {

    @Test
    fun `nulls below threshold do not trip`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
    }

    @Test
    fun `third consecutive null trips`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
        assertTrue(t.recordHeard(null))
    }

    @Test
    fun `heard transcript resets the streak`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard("good"))
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
        assertTrue(t.recordHeard(null))
    }

    @Test
    fun `blank transcript counts as heard, not a failure`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(""))
        assertFalse(t.recordHeard(null))
    }

    @Test
    fun `reset clears the streak`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
        t.reset()
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
        assertTrue(t.recordHeard(null))
    }

    @Test
    fun `custom threshold is respected`() {
        val t = ListenHealthTracker(2)
        assertFalse(t.recordHeard(null))
        assertTrue(t.recordHeard(null))
    }
}
