package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitTest {

    @Test
    fun a_emptyAndWhitespace() {
        assertEquals(emptyList<IntRange>(), SentenceSplit.ranges(""))
        assertEquals(emptyList<IntRange>(), SentenceSplit.ranges("   "))
    }

    @Test
    fun b_basicRangesAndSentences() {
        assertEquals(listOf("One.", "Two."), SentenceSplit.sentences("One. Two."))
        assertEquals(listOf(0..3, 5..8), SentenceSplit.ranges("One. Two."))
    }

    @Test
    fun c_questionExclaim() {
        assertEquals(listOf("Is it?", "Yes!", "Ok."), SentenceSplit.sentences("Is it? Yes! Ok."))
    }

    @Test
    fun d_decimalNotSplit() {
        assertEquals(listOf("Pi is 3.14 today."), SentenceSplit.sentences("Pi is 3.14 today."))
    }

    @Test
    fun e_abbreviationNotSplit() {
        assertEquals(listOf("Use e.g. Spark.", "Done."), SentenceSplit.sentences("Use e.g. Spark. Done."))
    }

    @Test
    fun f_newlineSplits() {
        assertEquals(listOf("Line one", "Line two"), SentenceSplit.sentences("Line one\nLine two"))
    }

    @Test
    fun g_cjkPunctuation() {
        assertEquals(listOf("日本語です。", "次です。"), SentenceSplit.sentences("日本語です。次です。"))
    }

    @Test
    fun h_noEndPunctuation() {
        assertEquals(listOf(0..17), SentenceSplit.ranges("No end punctuation"))
    }

    @Test
    fun i_leadingAndTrailingWhitespace() {
        assertEquals(listOf(2..6), SentenceSplit.ranges("  Lead. "))
    }

    @Test
    fun j_doublePunctuation() {
        assertEquals(listOf("Really?!", "Yes."), SentenceSplit.sentences("Really?! Yes."))
    }

    @Test
    fun k_indexAt() {
        val r = SentenceSplit.ranges("One. Two.")
        assertEquals(0, SentenceSplit.indexAt(r, 0))
        assertEquals(0, SentenceSplit.indexAt(r, 3))
        assertEquals(1, SentenceSplit.indexAt(r, 4))
        assertEquals(1, SentenceSplit.indexAt(r, 8))
        assertEquals(-1, SentenceSplit.indexAt(r, 9))
        assertEquals(-1, SentenceSplit.indexAt(r, -1))
    }
}
