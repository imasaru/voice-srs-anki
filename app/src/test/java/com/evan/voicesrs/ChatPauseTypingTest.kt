package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPauseTypingTest {

    @Test
    fun `typing pauses the drill while it is running`() {
        assertTrue(ChatPause.pauses(drillRunning = true, ChatPause.Trigger.TYPING))
        assertFalse(ChatPause.pauses(drillRunning = false, ChatPause.Trigger.TYPING))
        // A typing pause holds the card; it does not advance to the next card.
        assertFalse(ChatPause.advances(paused = true))
    }

    @Test
    fun `clearing the composer after a typing pause does not resume`() {
        val plan = ChatPause.afterTextCleared()
        assertFalse(plan.autoResume)
        assertEquals(0L, plan.delayMs)
        assertTrue(plan.stayPausedUntilTimer)
        assertTrue(plan.showResume)
        assertFalse(plan.pulse)
    }

    @Test
    fun `only the resume action or a rating resumes a typing pause`() {
        assertTrue(ChatPause.accepts(ChatPause.Action.RESUME))
        assertTrue(ChatPause.accepts(ChatPause.Action.RATING))
        assertFalse(ChatPause.accepts(ChatPause.Action.SHOW_ANSWER))
        assertFalse(ChatPause.accepts(ChatPause.Action.DISMISS))
        assertFalse(ChatPause.accepts(ChatPause.Action.OTHER))
    }
}
