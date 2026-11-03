package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h104a: 2 s auto-accept countdown for a suggested grade. Pure, no Android. */
class H104aTest {
    @Test
    fun autoAcceptMs() {
        assertEquals(2000L, GradeConfirm.AUTO_ACCEPT_MS)
    }

    @Test
    fun secondsLeftRoundsUp() {
        assertEquals(2, GradeConfirm.secondsLeft(2000L))
        assertEquals(2, GradeConfirm.secondsLeft(1999L))
        assertEquals(2, GradeConfirm.secondsLeft(1001L))
        assertEquals(1, GradeConfirm.secondsLeft(1000L))
        assertEquals(1, GradeConfirm.secondsLeft(1L))
        assertEquals(0, GradeConfirm.secondsLeft(0L))
        assertEquals(0, GradeConfirm.secondsLeft(-50L))
    }

    @Test
    fun countdownStatus() {
        assertEquals("Rated Hard · moving on in 2s", GradeConfirm.countdownStatus(2, 2))
        assertEquals("Correct, rated Good · moving on in 1s", GradeConfirm.countdownStatus(3, 1))
    }

    @Test
    fun holdLoopUsesAutoAccept() {
        val src = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()
        assertTrue(src.contains("GradeConfirm.AUTO_ACCEPT_MS"))
        assertTrue(src.contains("GradeConfirm.countdownStatus(mapped, left)"))
        assertFalse(src.contains("SystemClock.elapsedRealtime() + GradeConfirm.CHIP_HOLD_MS"))
    }
}
