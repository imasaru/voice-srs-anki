package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTargetTest {

    @Test
    fun `read-aloud states target playback whether or not drilling`() {
        val s = listOf(
            PlaybackControl.State.LOADING,
            PlaybackControl.State.READING,
            PlaybackControl.State.PAUSED
        )
        for (state in s) {
            val snap = PlaybackControl.Snapshot(state = state)
            assertEquals(
                "LOADING/READING/PAUSED should be PLAYBACK even while drilling",
                PlaybackControl.PauseTarget.PLAYBACK,
                PlaybackControl.pauseTarget(snap, drilling = true)
            )
            assertEquals(
                "LOADING/READING/PAUSED should be PLAYBACK while not drilling",
                PlaybackControl.PauseTarget.PLAYBACK,
                PlaybackControl.pauseTarget(snap, drilling = false)
            )
        }
    }

    @Test
    fun `idle while drilling targets the drill`() {
        val snap = PlaybackControl.Snapshot(state = PlaybackControl.State.IDLE)
        assertEquals(
            PlaybackControl.PauseTarget.DRILL,
            PlaybackControl.pauseTarget(snap, drilling = true)
        )
    }

    @Test
    fun `idle while not drilling targets nothing`() {
        val snap = PlaybackControl.Snapshot(state = PlaybackControl.State.IDLE)
        assertEquals(
            PlaybackControl.PauseTarget.NONE,
            PlaybackControl.pauseTarget(snap, drilling = false)
        )
    }

    @Test
    fun `pauseEnabled is true only when a target other than none is chosen`() {
        // Read-aloud states are always enabled regardless of drilling.
        for (state in listOf(
            PlaybackControl.State.LOADING,
            PlaybackControl.State.READING,
            PlaybackControl.State.PAUSED
        )) {
            val snap = PlaybackControl.Snapshot(state = state)
            assertTrue("state=$state drilling=true", PlaybackControl.pauseEnabled(snap, true))
            assertTrue("state=$state drilling=false", PlaybackControl.pauseEnabled(snap, false))
        }
        // Idle: enabled only while drilling.
        val idle = PlaybackControl.Snapshot(state = PlaybackControl.State.IDLE)
        assertTrue(PlaybackControl.pauseEnabled(idle, true))
        assertFalse(PlaybackControl.pauseEnabled(idle, false))
    }

    @Test
    fun `manual trigger pauses but does not reengage`() {
        assertTrue(ChatPause.pauses(drillRunning = true, ChatPause.Trigger.MANUAL))
        assertFalse(ChatPause.triggerReengages(ChatPause.Trigger.MANUAL))
    }
}
