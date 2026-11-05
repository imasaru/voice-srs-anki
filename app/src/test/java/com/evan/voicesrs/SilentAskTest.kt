package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentAskTest {
    @Test
    fun a_yesVariants() {
        for (s in listOf("yes", "Yes.", "yeah", "yep", "sure", "okay", "OK", "yes please", "silent mode"))
            assertEquals(s, SilentAsk.Reply.YES, SilentAsk.parse(s))
    }

    @Test
    fun b_noVariants() {
        for (s in listOf("no", "No.", "nope", "nah", "no thanks", "No, keep voice", "not now", "keep voice"))
            assertEquals(s, SilentAsk.Reply.NO, SilentAsk.parse(s))
    }

    @Test
    fun c_nothingOrUnclearIsNone() {
        for (s in listOf(null, "", "   ", "banana", "I don't know"))
            assertEquals("$s", SilentAsk.Reply.NONE, SilentAsk.parse(s))
    }

    @Test
    fun d_onlyNoKeepsVoice() {
        assertTrue(SilentAsk.goSilent(SilentAsk.Reply.YES))
        assertTrue(SilentAsk.goSilent(SilentAsk.Reply.NONE))
        assertFalse(SilentAsk.goSilent(SilentAsk.Reply.NO))
        assertFalse(SilentAsk.goSilent(SilentAsk.Reply.RATE_AGAIN))
    }

    @Test
    fun e_questionText() {
        assertEquals("No rating heard. Go silent, or say rate again?", SilentAsk.QUESTION)
    }

    @Test
    fun f_moreNoVariants() {
        for (s in listOf("not really", "Not really.", "stay in voice"))
            assertEquals(s, SilentAsk.Reply.NO, SilentAsk.parse(s))
    }

    @Test
    fun g_keepVoiceText() {
        assertEquals("Okay, staying in voice. Your rating?", SilentAsk.KEEP_VOICE)
    }

    @Test
    fun h_rateAgainVariants() {
        for (s in listOf("rate again", "Rate again.", "mark again", "again", "rated again"))
            assertEquals(s, SilentAsk.Reply.RATE_AGAIN, SilentAsk.parse(s))
    }

    @Test
    fun i_markedAgainText() {
        assertEquals("No rating, marked Again.", SilentAsk.MARKED_AGAIN)
    }

    @Test
    fun j_actionMapping() {
        assertEquals(SilentAsk.Action.GO_SILENT, SilentAsk.action(SilentAsk.Reply.YES))
        assertEquals(SilentAsk.Action.GO_SILENT, SilentAsk.action(SilentAsk.Reply.NONE))
        assertEquals(SilentAsk.Action.KEEP_VOICE, SilentAsk.action(SilentAsk.Reply.NO))
        assertEquals(SilentAsk.Action.RATE_AGAIN, SilentAsk.action(SilentAsk.Reply.RATE_AGAIN))
    }
}
