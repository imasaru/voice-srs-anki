package com.evan.voicesrs

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Covers the render gate that lets a still-render() early-out when nothing
 * the chat shows has changed, so the think-countdown ticks do not rebuild the
 * list while the drill waits for an answer.
 */
class ChatRenderGateTest {

    private val live = ChatRow.Live(
        front = "Q",
        back = "A",
        showBack = true,
        heard = "",
        showAnswer = true,
        rating = true,
        selectedEase = null
    )

    @Test
    fun firstRenderWithNoPrevRowsIsNotUnchanged() {
        val got = ChatRenderGate.unchanged(
            prevRows = null,
            rows = listOf(live),
            prevPaused = null,
            paused = false,
            prevSilent = null,
            silent = false
        )
        assertEquals(false, got)
    }

    @Test
    fun identicalRowsAndFlagsAreUnchanged() {
        val got = ChatRenderGate.unchanged(
            prevRows = listOf(live),
            rows = listOf(live),
            prevPaused = false,
            paused = false,
            prevSilent = false,
            silent = false
        )
        assertEquals(true, got)
    }

    @Test
    fun aChangedRowIsNotUnchanged() {
        val other = live.copy(heard = "hey")
        val got = ChatRenderGate.unchanged(
            prevRows = listOf(live),
            rows = listOf(other),
            prevPaused = false,
            paused = false,
            prevSilent = false,
            silent = false
        )
        assertEquals(false, got)
    }

    @Test
    fun sameRowsButPausedChangedIsNotUnchanged() {
        val got = ChatRenderGate.unchanged(
            prevRows = listOf(live),
            rows = listOf(live),
            prevPaused = false,
            paused = true,
            prevSilent = false,
            silent = false
        )
        assertEquals(false, got)
    }

    @Test
    fun sameRowsButSilentChangedIsNotUnchanged() {
        val got = ChatRenderGate.unchanged(
            prevRows = listOf(live),
            rows = listOf(live),
            prevPaused = false,
            paused = false,
            prevSilent = false,
            silent = true
        )
        assertEquals(false, got)
    }
}
