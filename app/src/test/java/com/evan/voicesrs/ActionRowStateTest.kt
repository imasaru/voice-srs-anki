package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/**
 * Action-row decision logic (item 54A), with the maintainer's fixed-controls rule:
 * Start drill / Stop drill never move, change or hide; only Pause toggles ⏸/▶.
 */
class ActionRowStateTest {

    private fun assertFixedButtons(s: ActionRowState) {
        assertEquals(ActionPill.START_DRILL, s.pill)
        assertTrue(s.showStopButton)
    }

    @Test
    fun `idle input empty no ime shows start pill and report chip`() {
        val s = ActionRowState.decide(DrillPhase.IDLE, inputEmpty = true, imeVisible = false, q = 0, total = 0)
        assertTrue(s.rowVisible)
        assertFixedButtons(s)
        assertFalse(s.showPauseButton)
        assertNull(s.progressText)
        assertTrue(s.showReportChip)
        assertEquals(ActionRowState.HINT_IDLE, s.inputHint)
    }

    @Test
    fun `idle input not empty hides the report chip but keeps the start pill`() {
        val s = ActionRowState.decide(DrillPhase.IDLE, inputEmpty = false, imeVisible = false, q = 0, total = 0)
        assertFalse(s.showReportChip)
        assertFixedButtons(s)
    }

    @Test
    fun `drilling shows pause as pause icon and keeps start and stop`() {
        val s = ActionRowState.decide(DrillPhase.DRILLING, inputEmpty = true, imeVisible = false, q = 6, total = 44)
        assertFixedButtons(s)
        assertTrue(s.showPauseButton)
        assertFalse(s.pauseShowsResume)
        assertEquals("Q6 of 44", s.progressText)
        assertFalse(s.showReportChip)
        assertEquals(ActionRowState.HINT_DRILLING, s.inputHint)
    }

    @Test
    fun `paused shows pause as play icon and keeps start and stop`() {
        val s = ActionRowState.decide(DrillPhase.PAUSED, inputEmpty = true, imeVisible = false, q = 6, total = 44)
        assertFixedButtons(s)
        assertTrue(s.showPauseButton)
        assertTrue(s.pauseShowsResume)
        assertEquals("Q6 of 44", s.progressText)
        assertFalse(s.showReportChip)
        assertEquals(ActionRowState.HINT_DRILLING, s.inputHint)
    }

    @Test
    fun `ime visible hides the row in every phase but keeps pill and hint`() {
        for (p in DrillPhase.values()) {
            val up = ActionRowState.decide(p, inputEmpty = true, imeVisible = true, q = 6, total = 44)
            val down = ActionRowState.decide(p, inputEmpty = true, imeVisible = false, q = 6, total = 44)
            assertFalse(up.rowVisible)
            assertEquals(down.copy(rowVisible = false), up)
        }
    }

    @Test
    fun `start and stop never change across phases`() {
        val states = DrillPhase.values().map {
            ActionRowState.decide(it, inputEmpty = true, imeVisible = false, q = 1, total = 5)
        }
        assertEquals(1, states.map { it.pill to it.showStopButton }.toSet().size)
    }

    @Test
    fun `progress formats the question count`() {
        assertEquals("Q1 of 44", ActionRowState.progress(1, 44))
        assertEquals("Q44 of 44", ActionRowState.progress(44, 44))
        assertNull(ActionRowState.progress(0, 44))
        assertNull(ActionRowState.progress(45, 44))
        assertNull(ActionRowState.progress(3, 0))
        assertNull(ActionRowState.progress(-1, 5))
    }
}
