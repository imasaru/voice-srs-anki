package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** h103: confirmation strings for a judge auto-grade. Pure, no Android. */
class H103Test {

    @Test
    fun labels() {
        assertEquals("Again", GradeConfirm.label(1))
        assertEquals("Hard", GradeConfirm.label(2))
        assertEquals("Good", GradeConfirm.label(3))
        assertEquals("Easy", GradeConfirm.label(4))
    }

    @Test
    fun ratedStatus() {
        assertEquals("Correct, rated Good", GradeConfirm.ratedStatus(3))
        assertEquals("Correct, rated Easy", GradeConfirm.ratedStatus(4))
        assertEquals("Rated Hard", GradeConfirm.ratedStatus(2))
        assertEquals("Rated Again", GradeConfirm.ratedStatus(1))
        // status must come from the ease, never a hardcoded word
        assertEquals(
            "Correct, rated Good",
            GradeConfirm.ratedStatus(GradeMapper.toAvailableEase(4, listOf(1, 2, 3)))
        )
    }

    @Test
    fun queuedStatus() {
        assertEquals("Queued, 1 pending sync", GradeConfirm.queuedStatus(1))
        assertEquals("Queued, 5 pending sync", GradeConfirm.queuedStatus(5))
    }

    @Test
    fun chipHoldInRange() {
        assertTrue(GradeConfirm.CHIP_HOLD_MS in 800..1500)
    }
}
