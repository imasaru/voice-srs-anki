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
}
