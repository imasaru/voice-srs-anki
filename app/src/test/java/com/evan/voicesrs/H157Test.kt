package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h157: ReplayPause pure helper (tracker 14B part 1). */
class H157Test {
    @Test
    fun startingWhileDrillingPausesAndNeverCountsMiss() {
        val s = ReplayPause.starting(drilling = true)
        assertTrue(s.pauseListening)
        assertTrue(s.stopMic)
        assertFalse(s.countsAsMiss)
    }

    @Test
    fun startingWhileIdleIsNoOp() {
        val s = ReplayPause.starting(drilling = false)
        assertFalse(s.pauseListening)
        assertFalse(s.stopMic)
        assertFalse(s.countsAsMiss)
    }

    @Test
    fun finishedAfterDrillReplayResumesAndResetsMisses() {
        val f = ReplayPause.finished(wasDrilling = true)
        assertTrue(f.resumeListening)
        assertTrue(f.resetMissCount)
    }

    @Test
    fun finishedAfterIdleReplayIsNoOp() {
        val f = ReplayPause.finished(wasDrilling = false)
        assertFalse(f.resumeListening)
        assertFalse(f.resetMissCount)
    }
}
