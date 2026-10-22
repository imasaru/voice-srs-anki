package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionShuffleTest {
    private val options = listOf(
        QuestionCard.Option("A", "alpha"),
        QuestionCard.Option("B", "beta"),
        QuestionCard.Option("C", "gamma"),
        QuestionCard.Option("D", "delta")
    )

    @Test
    fun `same card and session keep one order and the original letters`() {
        val session = sessionThatMoves()
        val first = OptionShuffle.order(options, 42L, session, enabled = true)
        val second = OptionShuffle.order(options, 42L, session, enabled = true)
        assertEquals(first.map { it.letter }, second.map { it.letter })
        assertEquals(options.map { it.letter }.toSet(), first.map { it.letter }.toSet())
        assertNotEquals(listOf("A", "B", "C", "D"), first.map { it.letter })
        for (opt in first) {
            assertEquals(options.first { it.letter == opt.letter }.text, opt.text)
        }
        assertEquals(
            OptionShuffle.seed(42L, session),
            OptionShuffle.seed(42L, session)
        )
    }

    @Test
    fun `meta choices and letter references stay in written order`() {
        val session = sessionThatMoves()
        for (phrase in listOf(
            "all of the above",
            "ALL OF THE ABOVE",
            "None of the above",
            "both of the above",
            "A and B",
            "A & B",
            "c and d"
        )) {
            val rows = options.dropLast(1) + QuestionCard.Option("D", phrase)
            val ordered = OptionShuffle.order(rows, 42L, session, enabled = true)
            assertEquals(listOf("A", "B", "C", "D"), ordered.map { it.letter })
            assertTrue(OptionShuffle.skips(phrase))
        }
    }

    @Test
    fun `ordinary text is not a skip`() {
        assertFalse(OptionShuffle.skips("bandwidth"))
        assertFalse(OptionShuffle.skips("a view over a table"))
    }

    @Test
    fun `setting off keeps the written order`() {
        val session = sessionThatMoves()
        val ordered = OptionShuffle.order(options, 42L, session, enabled = false)
        assertEquals(listOf("A", "B", "C", "D"), ordered.map { it.letter })
    }

    @Test
    fun `spoken rows use the given order and the original letters`() {
        val ordered = listOf(options[2], options[0], options[3], options[1])
        assertEquals(
            "Stem C. gamma A. alpha D. delta B. beta",
            OptionShuffle.spoken("Stem", ordered)
        )
    }

    private fun sessionThatMoves(): Long {
        var session = 1L
        while (session < 200L) {
            val letters = OptionShuffle.order(options, 42L, session, enabled = true).map { it.letter }
            if (letters != listOf("A", "B", "C", "D")) return session
            session++
        }
        throw AssertionError("expected some session seed to shuffle four options")
    }
}
