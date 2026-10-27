package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeLayoutDecisionTest {

    private val visible = ImeLayoutDecision.decide(imeVisible = true, timerRowWanted = true)

    @Test
    fun `ime visible hides header meta, timer row, and bottom bar`() {
        assertEquals(false, visible.headerMeta)
        assertEquals(false, visible.timerRow)
        assertEquals(false, visible.bottomBar)
    }

    @Test
    fun `ime hidden restores header meta and bottom bar`() {
        val hidden = ImeLayoutDecision.decide(imeVisible = false, timerRowWanted = true)
        assertEquals(true, hidden.headerMeta)
        assertEquals(true, hidden.bottomBar)
        assertEquals(true, hidden.timerRow)
    }

    @Test
    fun `ime hidden with timer row not wanted keeps timer hidden`() {
        val hidden = ImeLayoutDecision.decide(imeVisible = false, timerRowWanted = false)
        assertEquals(true, hidden.headerMeta)
        assertEquals(true, hidden.bottomBar)
        assertEquals(false, hidden.timerRow)
    }

    @Test
    fun `wide ime visible hides toolbar and session cue`() {
        val w = ImeLayoutDecision.decideWide(imeVisible = true, cueWanted = true)
        assertEquals(false, w.toolbar)
        assertEquals(false, w.sessionCue)
    }

    @Test
    fun `wide ime hidden keeps toolbar and restores cue when wanted`() {
        val w = ImeLayoutDecision.decideWide(imeVisible = false, cueWanted = true)
        assertEquals(true, w.toolbar)
        assertEquals(true, w.sessionCue)
    }

    @Test
    fun `wide ime hidden keeps toolbar and hides cue when not wanted`() {
        val w = ImeLayoutDecision.decideWide(imeVisible = false, cueWanted = false)
        assertEquals(true, w.toolbar)
        assertEquals(false, w.sessionCue)
    }
}
