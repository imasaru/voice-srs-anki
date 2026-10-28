package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTimelineTest {

    private val T = "One two. Three four."
    private val M = listOf(
        ReadingTimeline.Mark(0, 300, "One"),
        ReadingTimeline.Mark(300, 300, "two"),
        ReadingTimeline.Mark(700, 300, "Three"),
        ReadingTimeline.Mark(1000, 300, "four")
    )
    private val TT = ReadingTimeline.align(T, M)

    @Test
    fun a_alignInOrder() {
        assertEquals(listOf(0..2, 4..6, 9..13, 15..18), ReadingTimeline.align(T, M).map { it.chars })
        assertEquals(0L, TT.first().startMs)
        assertEquals(300L, TT.first().endMs)
    }

    @Test
    fun b_alignCaseAndPunctuation() {
        val r = ReadingTimeline.align(
            "Hello, world!",
            listOf(ReadingTimeline.Mark(0, 100, "hello"), ReadingTimeline.Mark(100, 100, "world"))
        ).map { it.chars }
        assertEquals(listOf(0..4, 7..11), r)
    }

    @Test
    fun c_alignSkipsMissing() {
        val r = ReadingTimeline.align(
            "One two.",
            listOf(ReadingTimeline.Mark(0, 1, "One"), ReadingTimeline.Mark(1, 1, "zzz"), ReadingTimeline.Mark(2, 1, "two"))
        ).map { it.chars }
        assertEquals(listOf(0..2, 4..6), r)
    }

    @Test
    fun d_alignRepeatedWordsInOrder() {
        val r = ReadingTimeline.align(
            "Same same same.",
            listOf(ReadingTimeline.Mark(0, 1, "Same"), ReadingTimeline.Mark(1, 1, "same"))
        ).map { it.chars }
        assertEquals(listOf(0..3, 5..8), r)
    }

    @Test
    fun e_sentenceAtWithMarks() {
        assertEquals(0, ReadingTimeline.sentenceAt(T, TT, 1300, 0))
        assertEquals(0, ReadingTimeline.sentenceAt(T, TT, 1300, 650))
        assertEquals(1, ReadingTimeline.sentenceAt(T, TT, 1300, 700))
        assertEquals(1, ReadingTimeline.sentenceAt(T, TT, 1300, 5000))
    }

    @Test
    fun sentenceAtBeforeFirstWord() {
        assertEquals(0, ReadingTimeline.sentenceAt(T, TT, 1300, -5))
    }

    @Test
    fun sentenceAtProportional() {
        assertEquals(0, ReadingTimeline.sentenceAt(T, emptyList(), 2000, 0))
        assertEquals(0, ReadingTimeline.sentenceAt(T, emptyList(), 2000, 400))
        assertEquals(1, ReadingTimeline.sentenceAt(T, emptyList(), 2000, 1000))
        assertEquals(1, ReadingTimeline.sentenceAt(T, emptyList(), 2000, 3000))
    }

    @Test
    fun sentenceAtZeroTotal() {
        assertEquals(0, ReadingTimeline.sentenceAt(T, emptyList(), 0, 500))
    }

    @Test
    fun sentenceAtBlank() {
        assertEquals(-1, ReadingTimeline.sentenceAt("   ", TT, 1000, 0))
    }

    @Test
    fun wordAt() {
        assertEquals(4..6, ReadingTimeline.wordAt(TT, 350))
        assertNull(ReadingTimeline.wordAt(TT, -1))
        assertEquals(15..18, ReadingTimeline.wordAt(TT, 5000))
        assertNull(ReadingTimeline.wordAt(emptyList(), 0))
    }
}
