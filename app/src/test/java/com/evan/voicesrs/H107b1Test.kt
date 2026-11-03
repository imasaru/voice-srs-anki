package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** h107b1: in the answer listen a bare "again" asks to hear the card again (Repeat), not a grade. */
class H107b1Test {

    @Test
    fun `bare again classifies as Repeat in the answer listen`() {
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("again"))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("Again."))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("again please"))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("  AGAIN  "))
    }

    @Test
    fun `thinkHear treats bare again as Repeat both letter modes`() {
        assertEquals(ThinkTime.Hear.REPEAT, OwnWordsMode.thinkHear("again", true))
        assertEquals(ThinkTime.Hear.REPEAT, OwnWordsMode.thinkHear("again", false))
    }

    @Test
    fun `again is only Repeat when bare longer phrases stay grades`() {
        assertEquals(ThinkTime.Hear.ANSWER, ThinkTime.classify("again and again the cache fills"))
        assertEquals(ThinkTime.Hear.ANSWER, ThinkTime.classify("say that again"))
        assertEquals(ThinkTime.Hear.ANSWER, ThinkTime.classify("good"))
    }

    @Test
    fun `explicit repeat commands still classify as Repeat`() {
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("say again"))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("repeat that"))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("one more time"))
    }

    @Test
    fun `grade listen keeps bare again as a grade`() {
        assertEquals(1, GradeMapper.wordToEase("again"))
        assertTrue(GradeMapper.parse("again") is VoiceAction.Grade)
    }
}
