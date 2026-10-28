package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Count how many "Resume" controls are on screen at once: the bottom
 * [PlaybackControl] toggle plus the inline chat-row resume. Exactly one should
 * ever show, and none when the screen is idle.
 */
class ResumeCountTest {

    /**
     * Bottom Resume (when the bottom toggle is visible and reads Resume) plus the
     * inline chat-row Resume. A value > 1 is the phantom-Resume bug; 0 on an idle
     * screen is the idle bug.
     */
    private fun resumes(
        snap: PlaybackControl.Snapshot,
        drilling: Boolean,
        chatPaused: Boolean,
        manual: Boolean,
        chatShowResume: Boolean
    ): Int {
        val bottomIsResume = PlaybackControl.bottomShowsDrillResume(snap, drilling, chatPaused)
        val bottom = if (PlaybackControl.pauseVisible(snap, drilling) &&
            (bottomIsResume || PlaybackControl.pauseShowsResume(snap, drilling, chatPaused))
        ) 1 else 0
        val inline = if (drilling && chatPaused &&
            ChatPause.inlineResumeVisible(manual, chatShowResume, bottomIsResume)
        ) 1 else 0
        return bottom + inline
    }

    private val idle = PlaybackControl.Snapshot(state = PlaybackControl.State.IDLE)
    private val reading = PlaybackControl.Snapshot(state = PlaybackControl.State.READING)
    private val paused = PlaybackControl.Snapshot(state = PlaybackControl.State.PAUSED)

    @Test
    fun `a chat pause shows one Resume`() {
        // REPRO: a chat freeze must not show two Resumes.
        assertEquals(1, resumes(idle, drilling = true, chatPaused = true, manual = false, chatShowResume = true))
    }

    @Test
    fun `b manual pause shows one Resume`() {
        assertEquals(1, resumes(idle, drilling = true, chatPaused = true, manual = true, chatShowResume = true))
    }

    @Test
    fun `c reply read aloud while paused keeps the inline Resume only`() {
        // The reply is spoken aloud: the bottom toggle is a playback Pause, so only
        // the inline chat resume shows.
        assertFalse(PlaybackControl.pauseShowsResume(reading, drilling = true, drillPaused = true))
        assertEquals(1, resumes(reading, drilling = true, chatPaused = true, manual = false, chatShowResume = true))
    }

    @Test
    fun `d idle drill shows no Pause or Resume`() {
        assertFalse(PlaybackControl.pauseVisible(idle, drilling = false))
        assertEquals(0, resumes(idle, drilling = false, chatPaused = false, manual = false, chatShowResume = false))
    }

    @Test
    fun `e after stop no Resume`() {
        // chatPaused/chatShowResume can be stale after Stop; the bottom toggle is
        // invisible while not drilling, so nothing reads Resume.
        assertEquals(0, resumes(idle, drilling = false, chatPaused = true, manual = false, chatShowResume = true))
    }

    @Test
    fun `f playback paused when idle still shows Resume reading`() {
        assertTrue(PlaybackControl.pauseVisible(paused, drilling = false))
        assertEquals(1, resumes(paused, drilling = false, chatPaused = false, manual = false, chatShowResume = false))
    }
}
