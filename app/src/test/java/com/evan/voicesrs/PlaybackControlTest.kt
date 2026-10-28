package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlTest {
    private val doc = SectionFormat.parse(
        """
        Core concept
        Delta Lake is the ACID table format.
        Why B
        The transaction log records every commit.
        Example
        ```
        SELECT * FROM t
        ```
        """.trimIndent()
    )

    @Test
    fun `speakable sections skip the footer and keep headings`() {
        val sections = PlaybackControl.speakable(doc)
        assertTrue(sections.size >= 4)
        assertTrue(sections.any { it.text.contains("Delta Lake") })
        assertTrue(sections.none { it.kind == SectionFormat.Kind.FOOTER })
    }

    @Test
    fun `play then started is reading and pause is always visible`() {
        val idle = PlaybackControl.Snapshot()
        assertTrue(PlaybackControl.pauseVisible(PlaybackControl.Snapshot(), drilling = true))
        assertFalse(PlaybackControl.pauseEnabled(idle))
        val loading = PlaybackControl.play(idle, turnId = 9L, section = 1)
        assertEquals(PlaybackControl.State.LOADING, loading.state)
        assertEquals(1, loading.section)
        val reading = PlaybackControl.started(loading)
        assertEquals(PlaybackControl.State.READING, reading.state)
        assertTrue(PlaybackControl.pauseEnabled(reading))
        assertEquals("Pause", PlaybackControl.pauseLabel(reading))
        val paused = PlaybackControl.pause(reading)
        assertEquals(PlaybackControl.State.PAUSED, paused.state)
        assertEquals("Resume", PlaybackControl.pauseLabel(paused))
        val again = PlaybackControl.resume(paused)
        assertEquals(PlaybackControl.State.LOADING, again.state)
        assertEquals(PlaybackControl.State.IDLE, PlaybackControl.stop(again).state)
    }

    @Test
    fun `highlight follows the section being read when the setting is on`() {
        val reading = PlaybackControl.started(PlaybackControl.play(PlaybackControl.Snapshot(), 4L, 2))
        assertTrue(PlaybackControl.highlight(reading, 4L, 2, settingOn = true))
        assertFalse(PlaybackControl.highlight(reading, 4L, 1, settingOn = true))
        assertFalse(PlaybackControl.highlight(reading, 4L, 2, settingOn = false))
        assertFalse(PlaybackControl.highlight(reading, 5L, 2, settingOn = true))
        assertEquals(PlaybackControl.State.LOADING, PlaybackControl.sectionState(reading.copy(state = PlaybackControl.State.LOADING), 4L, 2))
        assertEquals(PlaybackControl.State.IDLE, PlaybackControl.sectionState(reading, 4L, 0))
    }
}
