package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTargetTest {
    @Test
    fun `the main box targets the card on screen and not the previous turn`() {
        val graded = listOf(ChatTarget.Graded(turnId = 9L, cardId = 10L))
        val target = ChatTarget.composer(ChatTarget.Screen(cardId = 20L, ordinal = 2, revealed = false))
        assertTrue(target is ChatTarget.Target.Live)
        val live = target as ChatTarget.Target.Live
        assertEquals(20L, live.cardId)
        assertEquals(2, live.ordinal)
        assertFalse(live.revealed)
        assertFalse(live.cardId == graded.single().cardId)
        assertFalse(ChatTarget.countsAsGrade())
    }

    @Test
    fun `a revealed card stays the live target and an older card uses its thread`() {
        val revealed = ChatTarget.composer(ChatTarget.Screen(20L, 2, revealed = true))
        assertTrue((revealed as ChatTarget.Target.Live).revealed)
        val graded = listOf(ChatTarget.Graded(9L, 10L))
        val older = ChatTarget.thread(9L, graded)
        assertTrue(older is ChatTarget.Target.OlderThread)
        assertEquals(9L, (older as ChatTarget.Target.OlderThread).turnId)
        assertTrue(ChatTarget.thread(8L, graded) is ChatTarget.Target.None)
    }

    @Test
    fun `no card on screen sends nowhere`() {
        assertTrue(ChatTarget.composer(null) is ChatTarget.Target.None)
        assertTrue(ChatTarget.composer(ChatTarget.Screen(20L, 0, false)) is ChatTarget.Target.None)
        assertFalse(ChatTarget.countsAsGrade())
    }
}
