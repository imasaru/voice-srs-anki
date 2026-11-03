package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSpanTest {
    private val blocks = listOf("Key idea", "Masking hides a column. It runs at query time.", "SELECT 1")

    @Test
    fun `a matching sentence gives its block and range`() {
        val p = ReadingSpan.pick(blocks, "It runs at query time.")!!
        assertEquals(1, p.block)
        assertEquals("It runs at query time.", blocks[1].substring(p.range.first, p.range.last + 1))
    }

    @Test
    fun `b first matching block wins`() {
        assertEquals(0, ReadingSpan.pick(listOf("a b", "a b"), "a b")!!.block)
    }

    @Test
    fun `c no match gives null`() {
        assertNull(ReadingSpan.pick(blocks, "Not in the bubble."))
    }

    @Test
    fun `d blank gives null`() {
        assertNull(ReadingSpan.pick(blocks, ""))
        assertNull(ReadingSpan.pick(blocks, "   "))
    }

    private val reading = DrillUiState(drillRunning = true, cardId = 7L, frontHtml = "Q", readingSentence = "It runs.", readingIndex = 1)

    @Test
    fun `e only the current drill turn gets the sentence`() {
        assertEquals("It runs.", ReadingSpan.sentenceFor(7L, reading, highlightOn = true))
        assertEquals("", ReadingSpan.sentenceFor(8L, reading, highlightOn = true))
        assertEquals("", ReadingSpan.sentenceFor(7L, reading, highlightOn = false))
        assertEquals("", ReadingSpan.sentenceFor(7L, reading.copy(drillRunning = false), highlightOn = true))
        assertFalse(ReadingSpan.isCurrentTurn(7L, reading.copy(frontHtml = "")))
        assertTrue(ReadingSpan.isCurrentTurn(7L, reading))
    }

    @Test
    fun `f current turn position is the last row of that card`() {
        val ids = listOf(7L, null, 8L, 7L, null)
        assertEquals(3, ReadingSpan.currentTurnIndex(ids, reading))
        assertEquals(-1, ReadingSpan.currentTurnIndex(listOf(8L, null), reading))
        assertEquals(-1, ReadingSpan.currentTurnIndex(ids, reading.copy(drillRunning = false)))
    }

    private val choiceRows = listOf("A. Use repartition(8)", "B. Use coalesce(1)", "C. Increase spark.executor.memory", "D. Broadcast the small table")
    @Test fun `pickOption picks the spoken choice row`() {
        assertEquals(1, ReadingSpan.pickOption(choiceRows, "B. Use coalesce(1)"))
        assertEquals(2, ReadingSpan.pickOption(choiceRows, "C. Increase spark dot executor dot memory."))
        assertEquals(3, ReadingSpan.pickOption(choiceRows, "D. Broadcast the small table."))
        assertEquals(-1, ReadingSpan.pickOption(choiceRows, "Which fixes the skew?"))
        assertEquals(-1, ReadingSpan.pickOption(choiceRows, ""))
    }
    @Test fun `pick skips question and answer to reach the choice rows`() {
        assertEquals(3, ReadingSpan.pick(listOf("Which fixes the skew?", "") + choiceRows, "B. Use coalesce(1)")?.block)
    }
}
