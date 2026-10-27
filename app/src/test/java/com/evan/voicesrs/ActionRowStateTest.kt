package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure action-row decision logic for the single action row above the
 * chat input (item 54A). No UI here — layouts and binding are item 54B.
 */
class ActionRowStateTest {

    @Test
    fun `idle input empty no ime shows start pill and report chip`() {
        val s = ActionRowState.decide(DrillPhase.IDLE, inputEmpty = true, imeVisible = false, q = 0, total = 0)
        assertTrue(s.rowVisible)
        assertEquals(ActionPill.START_DRILL, s.pill)
        assertFalse(s.showPauseButton)
        assertFalse(s.showStopButton)
        assertNull(s.progressText)
        assertTrue(s.showReportChip)
        assertEquals(ActionRowState.HINT_IDLE, s.inputHint)
    }

    @Test
    fun `idle input not empty hides the report chip but keeps the start pill`() {
        val s = ActionRowState.decide(DrillPhase.IDLE, inputEmpty = false, imeVisible = false, q = 0, total = 0)
        assertFalse(s.showReportChip)
        assertEquals(ActionPill.START_DRILL, s.pill)
    }

    @Test
    fun `drilling shows pause stop and progress with no pill`() {
        val s = ActionRowState.decide(DrillPhase.DRILLING, inputEmpty = true, imeVisible = false, q = 6, total = 44)
        assertEquals(ActionPill.NONE, s.pill)
        assertTrue(s.showPauseButton)
        assertTrue(s.showStopButton)
        assertEquals("Q6 of 44", s.progressText)
        assertFalse(s.showReportChip)
        assertEquals(ActionRowState.HINT_DRILLING, s.inputHint)
    }

    @Test
    fun `paused shows resume stop no pause and progress`() {
        val s = ActionRowState.decide(DrillPhase.PAUSED, inputEmpty = true, imeVisible = false, q = 6, total = 44)
        assertEquals(ActionPill.RESUME, s.pill)
        assertFalse(s.showPauseButton)
        assertTrue(s.showStopButton)
        assertEquals("Q6 of 44", s.progressText)
        assertFalse(s.showReportChip)
        assertEquals(ActionRowState.HINT_DRILLING, s.inputHint)
    }

    @Test
    fun `ime visible hides the row in every phase but keeps pill and hint`() {
        val idle = ActionRowState.decide(DrillPhase.IDLE, inputEmpty = true, imeVisible = true, q = 0, total = 0)
        val drill = ActionRowState.decide(DrillPhase.DRILLING, inputEmpty = true, imeVisible = true, q = 6, total = 44)
        val paused = ActionRowState.decide(DrillPhase.PAUSED, inputEmpty = true, imeVisible = true, q = 6, total = 44)

        assertFalse(idle.rowVisible)
        assertFalse(drill.rowVisible)
        assertFalse(paused.rowVisible)

        // pill / hint / progress / chips computed the same way as with the IME down
        assertEquals(ActionPill.START_DRILL, idle.pill)
        assertEquals(ActionRowState.HINT_IDLE, idle.inputHint)
        assertEquals(ActionPill.NONE, drill.pill)
        assertEquals("Q6 of 44", drill.progressText)
        assertEquals(ActionPill.RESUME, paused.pill)
        assertEquals("Q6 of 44", paused.progressText)
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
