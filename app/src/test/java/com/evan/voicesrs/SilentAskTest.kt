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
    }

    @Test
    fun e_questionText() {
        assertEquals("Do you want to go into silent mode?", SilentAsk.QUESTION)
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
}
