package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFollowTest {
    private val follow = ChatFollow()

    @Test
    fun `at the bottom follows and timing waits until the bubble is visible`() {
        val asked = follow.onQuestion(ChatFollow.View(atBottom = true), 3)
        assertEquals(ChatFollow.Scroll.TO_LIVE, asked.scroll)
        assertTrue(asked.movedScroll)
        assertFalse(asked.showPill)
        assertFalse(asked.mayBegin)
        assertFalse(asked.mayStartTiming)
        val seen = follow.onVisible(asked.view, true)
        assertTrue(seen.mayBegin)
        assertTrue(seen.maySpeak)
        assertTrue(seen.mayStartTiming)
        assertFalse(seen.autoAdvance)
        assertFalse(seen.stoleFocus)
    }

    @Test
    fun `scrolled up holds the next question`() {
        val asked = follow.onQuestion(ChatFollow.View(atBottom = false), 4)
        assertEquals(ChatFollow.Scroll.NONE, asked.scroll)
        assertFalse(asked.movedScroll)
        assertTrue(asked.showPill)
        assertEquals(4, asked.view.holdingOrdinal)
        val seen = follow.onVisible(asked.view, true)
        assertTrue(seen.showPill)
        assertFalse(seen.mayBegin)
        assertFalse(seen.maySpeak)
        assertFalse(seen.mayStartTiming)
        assertFalse(seen.autoAdvance)
    }

    @Test
    fun `pill tap then visibility starts reading`() {
        val held = follow.onQuestion(ChatFollow.View(atBottom = false), 4)
        val pill = follow.onPill(held.view)
        assertEquals(ChatFollow.Scroll.TO_LIVE, pill.scroll)
        assertEquals(0, pill.view.holdingOrdinal)
        assertTrue(pill.view.atBottom)
        assertFalse(pill.showPill)
        assertFalse(pill.mayStartTiming)
        val seen = follow.onVisible(pill.view, true)
        assertTrue(seen.mayBegin)
        assertTrue(seen.maySpeak)
        assertTrue(seen.mayStartTiming)
        assertFalse(seen.autoAdvance)
    }

    @Test
    fun `scrolling back to the bottom releases the hold`() {
        val held = follow.onQuestion(ChatFollow.View(atBottom = false), 7)
        val away = follow.onScroll(held.view, false)
        assertEquals(7, away.view.holdingOrdinal)
        assertEquals(ChatFollow.Scroll.NONE, away.scroll)
        val back = follow.onScroll(away.view, true)
        assertEquals(0, back.view.holdingOrdinal)
        assertEquals(ChatFollow.Scroll.TO_LIVE, back.scroll)
        assertTrue(back.movedScroll)
        assertFalse(back.showPill)
        val seen = follow.onVisible(back.view, true)
        assertTrue(seen.mayStartTiming)
    }

    @Test
    fun `a late explanation does not scroll or steal focus`() {
        val live = follow.onVisible(
            follow.onQuestion(ChatFollow.View(atBottom = true), 1).view,
            true
        )
        val held = follow.onQuestion(ChatFollow.View(atBottom = false), 2)
        for (view in listOf(live.view, held.view)) {
            val expl = follow.onExplanation(view)
            assertEquals(ChatFollow.Scroll.NONE, expl.scroll)
            assertFalse(expl.movedScroll)
            assertFalse(expl.stoleFocus)
            assertEquals(view.holdingOrdinal, expl.view.holdingOrdinal)
            assertEquals(view.atBottom, expl.view.atBottom)
            assertFalse(expl.autoAdvance)
        }
    }

    @Test
    fun `chat pause blocks the card and does not clear a hold or advance`() {
        val visible = follow.onVisible(
            follow.onQuestion(ChatFollow.View(atBottom = true), 1).view,
            true
        )
        assertTrue(visible.mayBegin)
        val paused = follow.onPause(visible.view, true)
        assertFalse(paused.mayBegin)
        assertFalse(paused.maySpeak)
        assertFalse(paused.mayStartTiming)
        assertEquals(0, paused.view.holdingOrdinal)
        assertFalse(paused.autoAdvance)
        assertEquals(ChatFollow.Scroll.NONE, paused.scroll)

        val held = follow.onQuestion(ChatFollow.View(atBottom = false), 5)
        val heldPause = follow.onPause(held.view, true)
        assertEquals(5, heldPause.view.holdingOrdinal)
        assertTrue(heldPause.showPill)
        assertFalse(heldPause.mayBegin)
        assertFalse(heldPause.autoAdvance)
    }

    @Test
    fun `silent mode can show the card and still withholds speech and the timer`() {
        val visible = follow.onVisible(
            follow.onQuestion(ChatFollow.View(atBottom = true, silent = true), 2).view,
            true
        )
        assertTrue(visible.mayBegin)
        assertFalse(visible.maySpeak)
        assertFalse(visible.mayStartTiming)
        assertFalse(visible.autoAdvance)

        val held = follow.onVisible(
            follow.onQuestion(ChatFollow.View(atBottom = false, silent = true), 3).view,
            true
        )
        assertFalse(held.mayBegin)
        assertFalse(held.maySpeak)
        assertFalse(held.mayStartTiming)
        assertTrue(held.showPill)
        assertFalse(held.autoAdvance)
    }

    @Test
    fun `bottom slop is 48dp and a decision never auto advances`() {
        assertEquals(48, ChatFollow.BOTTOM_SLOP_DP)
        val idle = follow.onPill(ChatFollow.View())
        assertFalse(idle.autoAdvance)
        assertFalse(idle.stoleFocus)
    }
}
