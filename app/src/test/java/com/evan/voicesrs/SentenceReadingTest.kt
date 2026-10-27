package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceReadingTest {

    @Test
    fun a_emptyAndWhitespace() {
        assertEquals(emptyList<String>(), SentenceReading.parts(""))
        assertEquals(emptyList<String>(), SentenceReading.parts("   "))
    }

    @Test
    fun b_basicSentences() {
        assertEquals(listOf("One.", "Two."), SentenceReading.parts("One. Two."))
    }

    @Test
    fun c_noEndPunctuation() {
        assertEquals(listOf("No end punctuation"), SentenceReading.parts("No end punctuation"))
    }

    @Test
    fun d_abbreviationNotSplit() {
        assertEquals(listOf("Use e.g. Spark.", "Done."), SentenceReading.parts("Use e.g. Spark. Done."))
    }

    @Test
    fun e_atPublishesSentence() {
        val s = SentenceReading.at(1, "Two.")(DrillUiState())
        assertEquals("Two.", s.readingSentence)
        assertEquals(1, s.readingIndex)
        assertEquals(DrillUiState(), s.copy(readingSentence = "", readingIndex = -1))
    }

    @Test
    fun f_clearedResets() {
        assertEquals(DrillUiState(), SentenceReading.cleared()(DrillUiState(readingSentence = "X", readingIndex = 2)))
    }

    @Test
    fun g_defaults() {
        val s = DrillUiState()
        assertEquals("", s.readingSentence)
        assertEquals(-1, s.readingIndex)
    }
}
