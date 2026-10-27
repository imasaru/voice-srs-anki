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
        assertTrue(asked.showPill)
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
    fun `advance always pins the next question even if scrolled up`() {
        val asked = follow.onQuestion(ChatFollow.View(atBottom = false), 4)
        assertEquals(ChatFollow.Scroll.TO_LIVE, asked.scroll)
        assertTrue(asked.movedScroll)
        assertTrue(asked.showPill)
        assertEquals(0, asked.view.holdingOrdinal)
        val seen = follow.onVisible(asked.view.copy(liveVisible = true), true)
        assertTrue(seen.mayBegin)
        assertFalse(seen.autoAdvance)
    }

    @Test
    fun `pill tap then visibility starts reading`() {
        val held = ChatFollow.View(atBottom = false, liveOrdinal = 4, holdingOrdinal = 4)
        val pill = follow.onPill(held)
        assertEquals(ChatFollow.Scroll.TO_LIVE, pill.scroll)
        assertEquals(0, pill.view.holdingOrdinal)
        assertTrue(pill.view.atBottom)
        assertTrue(pill.showPill)
        assertFalse(pill.mayStartTiming)
        val seen = follow.onVisible(pill.view, true)
        assertTrue(seen.mayBegin)
        assertTrue(seen.maySpeak)
        assertTrue(seen.mayStartTiming)
        assertFalse(seen.autoAdvance)
    }

    @Test
    fun `scrolling back to the bottom releases the hold`() {
        val held = ChatFollow.View(atBottom = false, liveOrdinal = 7, holdingOrdinal = 7)
        val away = follow.onScroll(held, false)
        assertEquals(7, away.view.holdingOrdinal)
        assertEquals(ChatFollow.Scroll.NONE, away.scroll)
        val back = follow.onScroll(away.view, true)
        assertEquals(0, back.view.holdingOrdinal)
        assertEquals(ChatFollow.Scroll.TO_LIVE, back.scroll)
        assertTrue(back.movedScroll)
        assertTrue(back.showPill)
        val seen = follow.onVisible(back.view, true)
        assertTrue(seen.mayStartTiming)
    }

    @Test
    fun `a late explanation does not scroll or steal focus`() {
        val live = follow.onVisible(
            follow.onQuestion(ChatFollow.View(atBottom = true), 1).view,
            true
        )
        val held = ChatFollow.View(atBottom = false, liveOrdinal = 2, holdingOrdinal = 2)
        for (view in listOf(live.view, held)) {
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

        val held = ChatFollow.View(atBottom = false, liveOrdinal = 5, holdingOrdinal = 5)
        val heldPause = follow.onPause(held, true)
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
        assertTrue(held.mayBegin)
        assertFalse(held.maySpeak)
        assertFalse(held.mayStartTiming)
        assertFalse(held.showPill)
        assertFalse(held.autoAdvance)
    }

    @Test
    fun `bottom slop is 48dp and a decision never auto advances`() {
        assertEquals(48, ChatFollow.BOTTOM_SLOP_DP)
        val idle = follow.onPill(ChatFollow.View())
        assertFalse(idle.autoAdvance)
        assertFalse(idle.stoleFocus)
    }

    @Test
    fun `a live question off-screen shows the pill and the pill reveals it`() {
        val asked = follow.onQuestion(ChatFollow.View(atBottom = true), 3)
        val notSeen = follow.onVisible(asked.view, false)
        assertTrue(notSeen.showPill)
        assertFalse(notSeen.mayBegin)
        val revealed = follow.onPill(notSeen.view)
        assertEquals(ChatFollow.Scroll.TO_LIVE, revealed.scroll)
        assertEquals(0, revealed.view.holdingOrdinal)
        val seen = follow.onVisible(revealed.view, true)
        assertFalse(seen.showPill)
        assertTrue(seen.mayBegin)

        val none = follow.onVisible(ChatFollow.View(liveOrdinal = 0, liveVisible = false), false)
        assertFalse(none.showPill)
    }
}
