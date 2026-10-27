package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatFeed.rows() must keep turns and asks in wall-clock order by [at], so a
 * pinned study-report Ask does not get dragged below every live turn.
 */
class ChatFeedOrderTest {

    private fun turn(id: Long, at: Long) = ReviewTurn(
        turnId = id,
        cardId = 0L,
        noteId = null,
        frontText = "Q$id",
        backText = "",
        contentHash = "",
        transcript = "",
        ease = 3,
        explanation = ExplState.None,
        at = at
    )

    private fun ask(id: Long, at: Long) = ChatRow.Ask(id, "A$id", ExplState.Pending, "Deck", at = at)

    @Test
    fun `turns and asks order by at with turns before asks on equal at`() {
        val rows = ChatFeed.rows(
            listOf(turn(1L, 100L), turn(2L, 300L)),
            DrillUiState(drillRunning = false),
            asks = listOf(ask(1L, 200L))
        )
        // turn@100, ask@200, turn@300
        assertEquals(3, rows.size)
        assertTrue(rows[0] is ChatRow.Past)
        assertTrue(rows[1] is ChatRow.Ask)
        assertTrue(rows[2] is ChatRow.Past)
    }

    @Test
    fun `all at zero keeps old behaviour turns then asks`() {
        val rows = ChatFeed.rows(
            listOf(turn(1L, 0L), turn(2L, 0L)),
            DrillUiState(drillRunning = false),
            asks = listOf(ask(1L, 0L), ask(2L, 0L))
        )
        assertEquals(4, rows.size)
        assertTrue(rows[0] is ChatRow.Past)
        assertTrue(rows[1] is ChatRow.Past)
        assertTrue(rows[2] is ChatRow.Ask)
        assertTrue(rows[3] is ChatRow.Ask)
    }

    @Test
    fun `notice still comes after the merged turns and asks`() {
        val rows = ChatFeed.rows(
            listOf(turn(1L, 100L)),
            DrillUiState(drillRunning = false),
            asks = listOf(ask(1L, 200L)),
            notice = "Type a question first."
        )
        assertEquals(3, rows.size)
        assertTrue(rows[0] is ChatRow.Past)
        assertTrue(rows[1] is ChatRow.Ask)
        assertTrue(rows[2] is ChatRow.Notice)
    }

    @Test
    fun `equal at puts the turn before the ask`() {
        val rows = ChatFeed.rows(
            listOf(turn(1L, 100L)),
            DrillUiState(drillRunning = false),
            asks = listOf(ask(1L, 100L))
        )
        assertEquals(2, rows.size)
        assertTrue(rows[0] is ChatRow.Past)
        assertTrue(rows[1] is ChatRow.Ask)
    }
}
