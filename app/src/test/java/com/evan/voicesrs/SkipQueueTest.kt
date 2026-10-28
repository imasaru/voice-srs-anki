package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipQueueTest {
    @Test
    fun `a insert mid deck`() {
        assertEquals(7, SkipQueue.insertAt(2, 20))
    }

    @Test
    fun `b insert near end clamps`() {
        assertEquals(20, SkipQueue.insertAt(18, 20))
    }

    @Test
    fun `c empty tail`() {
        assertEquals(0, SkipQueue.insertAt(0, 0))
    }

    @Test
    fun `d third skip goes to end`() {
        assertTrue(SkipQueue.requeue(2))
        assertFalse(SkipQueue.requeue(3))
        assertEquals(SkipQueue.SAY_LAST, SkipQueue.say(3, false))
    }

    @Test
    fun `e return due after gap`() {
        assertFalse(SkipQueue.dueForReturn(3, 7))
        assertTrue(SkipQueue.dueForReturn(3, 8))
    }

    @Test
    fun `f speed mode says short`() {
        assertEquals(SkipQueue.SAY_SHORT, SkipQueue.say(1, true))
        assertEquals(SkipQueue.SAY, SkipQueue.say(1, false))
    }
}
