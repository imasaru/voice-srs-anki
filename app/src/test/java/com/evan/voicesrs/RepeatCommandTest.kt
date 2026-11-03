package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 27: "repeat" and "say again" re-read the question without changing drill state. */
class RepeatCommandTest {
    @Test
    fun `repeat phrases map to Repeat`() {
        val repeats = listOf("repeat", "say again", "Repeat.", "say it again", "repeat the question")
        for (spoken in repeats) {
            assertEquals(spoken, VoiceAction.Repeat, GradeMapper.parse(spoken))
            assertTrue(spoken, GradeMapper.isRepeat(spoken))
            assertNull("repeat is not a grade", GradeMapper.wordToEase(spoken))
        }
    }

    @Test
    fun `again and grades are unchanged`() {
        // "again" stays the Again grade (ease 1).
        assertEquals(1, GradeMapper.wordToEase("again"))
        assertTrue(GradeMapper.parse("again") is VoiceAction.Grade)
        // "say that again" keeps its exact-phrase Again grade (that is a filler -> "again").
        assertEquals(1, GradeMapper.wordToEase("say that again"))
        assertTrue(GradeMapper.parse("say that again") is VoiceAction.Grade)
        // A lone "say it" is not a repeat command.
        assertNull(GradeMapper.wordToEase("say it"))
    }

    @Test
    fun `ThinkTime classifies say again as Repeat`() {
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("say again"))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("say it again"))
        // h107b1: bare "again" = Repeat in the answer listen; other grade phrases still classify as ANSWER.
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("again"))
        assertEquals(ThinkTime.Hear.ANSWER, ThinkTime.classify("say that again"))
    }
}
