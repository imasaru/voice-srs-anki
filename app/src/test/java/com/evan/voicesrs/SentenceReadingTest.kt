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

    @Test
    fun h_firstWordMatch() {
        assertEquals(5..8, SentenceReading.spanIn("One. Two.", "Two."))
    }

    @Test
    fun i_anyWhitespaceBetweenWords() {
        assertEquals(5..15, SentenceReading.spanIn("One.\nTwo  words.", "Two words."))
    }

    @Test
    fun j_notFound() {
        assertEquals(null, SentenceReading.spanIn("One. Two.", "Three."))
    }

    @Test
    fun k_blankSentenceNull() {
        assertEquals(null, SentenceReading.spanIn("abc", ""))
        assertEquals(null, SentenceReading.spanIn("abc", "   "))
    }

    @Test
    fun l_regexCharactersEscaped() {
        assertEquals(3..8, SentenceReading.spanIn("Is (a+b)? yes", "(a+b)?"))
    }

    @Test
    fun m_firstMatch() {
        assertEquals(0..4, SentenceReading.spanIn("Same. Same.", "Same."))
    }
}
