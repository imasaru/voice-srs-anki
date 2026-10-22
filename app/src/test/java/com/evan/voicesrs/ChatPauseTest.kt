package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPauseTest {

    @Test
    fun `focus thread typing and dictation pause the drill`() {
        val triggers = ChatPause.Trigger.entries
        assertEquals(4, triggers.size)
        assertTrue(triggers.contains(ChatPause.Trigger.FOCUS_INPUT))
        assertTrue(triggers.contains(ChatPause.Trigger.OPEN_THREAD))
        assertTrue(triggers.contains(ChatPause.Trigger.TYPING))
        assertTrue(triggers.contains(ChatPause.Trigger.DICTATION))
        for (trigger in triggers) {
            assertTrue(ChatPause.pauses(drillRunning = true, trigger))
            assertFalse(ChatPause.pauses(drillRunning = false, trigger))
        }
        assertEquals("Paused, asking AI", ChatPause.LABEL)
        assertEquals("Resume", ChatPause.RESUME)
    }

    @Test
    fun `paused drill does not advance and resumes only on resume or a rating`() {
        assertFalse(ChatPause.advances(paused = true))
        assertTrue(ChatPause.advances(paused = false))
        assertTrue(ChatPause.accepts(ChatPause.Action.RESUME))
        assertTrue(ChatPause.accepts(ChatPause.Action.RATING))
        assertFalse(ChatPause.accepts(ChatPause.Action.SHOW_ANSWER))
        assertFalse(ChatPause.accepts(ChatPause.Action.DISMISS))
        assertFalse(ChatPause.accepts(ChatPause.Action.OTHER))
    }

    @Test
    fun `think timer freezes and a voice question is the only spoken reply`() {
        assertEquals(12_000L, ChatPause.freeze(nowMs = 8_000L, deadlineMs = 20_000L))
        assertEquals(0L, ChatPause.freeze(nowMs = 25_000L, deadlineMs = 20_000L))
        assertEquals(30_000L, ChatPause.resumeDeadline(nowMs = 18_000L, frozenMs = 12_000L))
        assertTrue(ChatPause.speakReply(paused = true, silent = false, askedByVoice = true))
        assertFalse(ChatPause.speakReply(paused = true, silent = true, askedByVoice = true))
        assertFalse(ChatPause.speakReply(paused = true, silent = false, askedByVoice = false))
        assertFalse(ChatPause.speakReply(paused = false, silent = false, askedByVoice = true))
    }
}
