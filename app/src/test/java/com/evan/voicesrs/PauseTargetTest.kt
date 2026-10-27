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
    fun `manual trigger pauses and drops a pending auto-resume`() {
        assertTrue(ChatPause.pauses(drillRunning = true, ChatPause.Trigger.MANUAL))
        assertTrue(ChatPause.triggerReengages(ChatPause.Trigger.MANUAL))
    }

    @Test
    fun `idle paused drill targets drill resume and shows Resume`() {
        val idle = PlaybackControl.Snapshot(state = PlaybackControl.State.IDLE)
        assertEquals(
            PlaybackControl.PauseTarget.DRILL_RESUME,
            PlaybackControl.pauseTarget(idle, drilling = true, drillPaused = true)
        )
        assertTrue(PlaybackControl.pauseEnabled(idle, true, true))
        assertTrue(PlaybackControl.pauseShowsResume(idle, true, true))
        assertFalse(PlaybackControl.pauseShowsResume(idle, true, false))
        assertFalse(PlaybackControl.pauseShowsResume(idle, false, true))
        assertEquals(
            PlaybackControl.PauseTarget.NONE,
            PlaybackControl.pauseTarget(idle, drilling = false, drillPaused = true)
        )
    }

    @Test
    fun `read-aloud wins over a paused drill`() {
        val reading = PlaybackControl.Snapshot(state = PlaybackControl.State.READING)
        assertEquals(
            PlaybackControl.PauseTarget.PLAYBACK,
            PlaybackControl.pauseTarget(reading, drilling = true, drillPaused = true)
        )
        assertFalse(PlaybackControl.pauseShowsResume(reading, true, true))
        val paused = PlaybackControl.Snapshot(state = PlaybackControl.State.PAUSED)
        assertTrue(PlaybackControl.pauseShowsResume(paused, false, false))
    }

    @Test
    fun `idle running drill is icon-only and a paused drill resumes`() {
        val idle = PlaybackControl.Snapshot(state = PlaybackControl.State.IDLE)
        assertTrue(PlaybackControl.pauseShowsDrillPause(idle, drilling = true, drillPaused = false))
        assertFalse(PlaybackControl.pauseShowsDrillPause(idle, drilling = true, drillPaused = true))
        assertFalse(PlaybackControl.pauseShowsDrillPause(idle, drilling = false, drillPaused = false))
        val reading = PlaybackControl.Snapshot(state = PlaybackControl.State.READING)
        assertFalse(PlaybackControl.pauseShowsDrillPause(reading, drilling = true, drillPaused = false))
        val paused = PlaybackControl.Snapshot(state = PlaybackControl.State.PAUSED)
        assertFalse(PlaybackControl.pauseShowsDrillPause(paused, drilling = true, drillPaused = false))
    }
}
