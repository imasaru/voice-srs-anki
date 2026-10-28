package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapperTest {
    @Test
    fun `parser table`() {
        val grades = listOf(
            "again" to 1,
            "hard" to 2,
            "good" to 3,
            "easy" to 4,
            "AGAIN" to 1,
            "  Good  " to 3,
            "goo" to 3,
            "gud" to 3,
            "that was good" to 3,
            "pretty easy" to 4,
            "pretty hard" to 2,
            "that was hard" to 2,
            "too easy" to 4,
            "three" to 3,
            "3" to 3,
            "one" to 1,
            "two" to 2,
            "four" to 4,
            "ichi" to 1,
            "ni" to 2,
            "san" to 3,
            "yon" to 4,
            "shi" to 4,
            "いち" to 1,
            "に" to 2,
            "さん" to 3,
            "よん" to 4,
            "し" to 4,
            "repeat it wrong" to 1,
            "wrong" to 1,
            "no" to 1,
            "nope" to 1,
            "say that again please" to 1,
            "ez" to 4,
            "okay" to 3,
            "difficult" to 2,
            "mark it good" to 3,
            "I think easy" to 4,
            "eas" to 4,
            "har" to 2,
            "fail" to 1,
            "simple" to 4,
            "４" to 4,
            "very good" to 3,
            "got it wrong" to 1
        )
        assertTrue("need 40+ grade rows, have ${grades.size}", grades.size >= 40)
        for ((spoken, ease) in grades) {
            assertEquals(spoken, ease, GradeMapper.wordToEase(spoken))
            assertTrue(spoken, GradeMapper.parse(spoken) is VoiceAction.Grade)
        }
    }

    @Test
    fun `commands`() {
        val repeats = listOf("repeat", "repeat that", "repeat the question", "repeat the answer", "one more time")
        for (spoken in repeats) {
            assertTrue(spoken, GradeMapper.isRepeat(spoken))
            assertNull(spoken, GradeMapper.wordToEase(spoken))
        }
        val skips = listOf("skip", "skip this card", "next card", "pass", "next")
        for (spoken in skips) {
            assertTrue(spoken, GradeMapper.isSkip(spoken))
            assertNull(GradeMapper.wordToEase(spoken))
        }
        val pauses = listOf("pause", "stop", "stop drill", "hold on", "quit")
        for (spoken in pauses) {
            assertTrue(spoken, GradeMapper.isPause(spoken))
            assertNull(GradeMapper.wordToEase(spoken))
        }
    }

    @Test
    fun `negatives and ambiguity`() {
        val rejected = listOf(
            "",
            "hello",
            "goodbye",
            "sandwich",
            "know",
            "hardly",
            "food",
            "gold",
            "good or easy",
            "not good",
            "night",
            "whatever",
            "note",
            "yes",
            "the",
            "goodness",
            "good morning",
            "banana",
            "1 2",
            "skip pause"
        )
        assertTrue(rejected.size >= 15)
        for (spoken in rejected) {
            assertEquals(spoken, VoiceAction.None, GradeMapper.parse(spoken))
            assertNull(spoken, GradeMapper.wordToEase(spoken))
        }
        assertEquals(VoiceAction.None, GradeMapper.parse(null))
    }

    @Test
    fun `a grade wins over a command word`() {
        assertEquals(3, GradeMapper.wordToEase("skip good"))
        assertEquals(1, GradeMapper.wordToEase("repeat it wrong"))
        assertTrue(!GradeMapper.isRepeat("repeat it wrong"))
    }

    @Test
    fun `canonical grade word is an exact match`() {
        assertEquals("again", GradeMapper.canonicalGradeWord("again"))
        assertEquals("hard", GradeMapper.canonicalGradeWord(" HARD "))
        assertEquals("good", GradeMapper.canonicalGradeWord("Good"))
        assertEquals("easy", GradeMapper.canonicalGradeWord("easy"))
        assertNull(GradeMapper.canonicalGradeWord("goodbye"))
        assertNull(GradeMapper.canonicalGradeWord("repeat"))
        assertNull(GradeMapper.canonicalGradeWord("that was good"))
        assertNull(GradeMapper.canonicalGradeWord("gud"))
        assertNull(GradeMapper.canonicalGradeWord(""))
        assertNull(GradeMapper.canonicalGradeWord(null))
    }

    @Test
    fun `ease passes through when offered`() {
        assertEquals(4, GradeMapper.toAvailableEase(4, listOf(1, 2, 3, 4)))
        assertEquals(1, GradeMapper.toAvailableEase(1, listOf(1, 2, 3)))
    }

    @Test
    fun `easy falls back to closest button`() {
        assertEquals(3, GradeMapper.toAvailableEase(4, listOf(1, 2, 3)))
    }

    @Test
    fun `again falls back to closest button`() {
        assertEquals(2, GradeMapper.toAvailableEase(1, listOf(2, 3, 4)))
    }

    @Test
    fun `empty buttons returns clamped ease`() {
        assertEquals(3, GradeMapper.toAvailableEase(3, emptyList()))
    }

    @Test
    fun `v017 whole phrase grades`() {
        assertEquals(1, GradeMapper.wordToEase("I forgot"))
        assertEquals(1, GradeMapper.wordToEase("no idea"))
        assertEquals(1, GradeMapper.wordToEase("I don't know"))
        assertEquals(3, GradeMapper.wordToEase("got it"))
        assertEquals(4, GradeMapper.wordToEase("easy peasy"))
        assertEquals(4, GradeMapper.wordToEase("nailed it!"))
        assertNull(GradeMapper.wordToEase("goodbye"))
        assertNull(GradeMapper.wordToEase("not easy peasy"))
    }

    @Test
    fun `skip it please is skip and skip good is a grade`() {
        assertTrue(GradeMapper.parse("skip it please") is VoiceAction.Skip)
        assertTrue(GradeMapper.parse("skip good") is VoiceAction.Grade)
        assertEquals(3, GradeMapper.wordToEase("skip good"))
    }
}
