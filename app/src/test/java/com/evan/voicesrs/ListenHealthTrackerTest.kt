package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenHealthTrackerTest {

    @Test
    fun `null transcripts never trip the microphone warning`() {
        val t = ListenHealthTracker(1)
        repeat(6) {
            assertFalse(t.recordHeard(null))
        }
        assertFalse(t.recordHeard(""))
        assertFalse(t.recordHeard("   "))
    }

    @Test
    fun `a real fault can warn and does not throw`() {
        val t = ListenHealthTracker(1)
        assertFalse(t.recordHeard(null))
        assertTrue(t.recordFault())
        assertTrue(t.recordFault())
    }

    @Test
    fun `heard transcript resets the fault streak`() {
        val t = ListenHealthTracker(2)
        assertFalse(t.recordFault())
        assertFalse(t.recordHeard("good"))
        assertFalse(t.recordFault())
        assertTrue(t.recordFault())
    }

    @Test
    fun `blank transcript does not clear or trip`() {
        val t = ListenHealthTracker(2)
        assertFalse(t.recordFault())
        assertFalse(t.recordHeard(""))
        assertTrue(t.recordFault())
    }

    @Test
    fun `reset clears the fault streak`() {
        val t = ListenHealthTracker(2)
        assertFalse(t.recordFault())
        t.reset()
        assertFalse(t.recordFault())
        assertTrue(t.recordFault())
    }

    @Test
    fun `custom fault threshold is respected`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordFault())
        assertFalse(t.recordFault())
        assertTrue(t.recordFault())
    }
}
