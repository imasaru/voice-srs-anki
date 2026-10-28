package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenLetterTest {
    private val letters = setOf("A", "B", "C", "D", "E", "F")

    @Test
    fun `each homophone maps to its letter`() {
        val heard = listOf(
            "a" to "A", "A" to "A", "ay" to "A", "eh" to "A", "alpha" to "A", "first" to "A",
            "b" to "B", "B" to "B", "be" to "B", "bee" to "B", "bea" to "B", "b." to "B",
            "bravo" to "B", "second" to "B",
            "c" to "C", "see" to "C", "sea" to "C", "si" to "C", "she" to "C", "cee" to "C",
            "charlie" to "C", "third" to "C",
            "d" to "D", "dee" to "D", "delta" to "D", "fourth" to "D",
            "e" to "E", "ee" to "E", "echo" to "E", "fifth" to "E",
            "f" to "F", "ef" to "F", "eff" to "F", "foxtrot" to "F", "sixth" to "F"
        )
        for ((raw, letter) in heard) {
            assertEquals(raw, letter, SpokenLetter.normalise(raw, letters))
        }
    }

    @Test
    fun `prefixes apply to every letter word`() {
        assertEquals("A", SpokenLetter.normalise("option a", letters))
        assertEquals("A", SpokenLetter.normalise("answer a", letters))
        assertEquals("A", SpokenLetter.normalise("letter a", letters))
        assertEquals("A", SpokenLetter.normalise("hey a", letters))
        assertEquals("B", SpokenLetter.normalise("option b", letters))
        assertEquals("B", SpokenLetter.normalise("Option b.", letters))
        assertEquals("B", SpokenLetter.normalise("answer is bee", letters))
        assertEquals("C", SpokenLetter.normalise("letter see", letters))
        assertEquals("D", SpokenLetter.normalise("option dee", letters))
        assertEquals("D", SpokenLetter.normalise("letter the", letters))
        assertEquals("E", SpokenLetter.normalise("answer echo", letters))
        assertEquals("F", SpokenLetter.normalise("option foxtrot", letters))
        assertEquals("B", SpokenLetter.normalise("choice b", letters))
        assertEquals("A", SpokenLetter.normalise("option first", letters))
        assertEquals("C", SpokenLetter.normalise("hey charlie", letters))
    }

    @Test
    fun `a letter that is not on the card is rejected`() {
        val short = setOf("A", "C")
        assertNull(SpokenLetter.normalise("B", short))
        assertNull(SpokenLetter.normalise("bee", short))
        assertNull(SpokenLetter.normalise("option b", short))
        assertNull(SpokenLetter.normalise("bravo", short))
        assertNull(SpokenLetter.normalise("second", short))
        assertNull(SpokenLetter.normalise("first", setOf("B", "C")))
        assertNull(SpokenLetter.normalise("sixth", setOf("A", "B")))
        assertNull(SpokenLetter.normalise("foxtrot", setOf("A", "B")))
        assertEquals("B", SpokenLetter.normalise("second", setOf("B", "C")))
        assertEquals("A", SpokenLetter.normalise("first", letters))
    }

    @Test
    fun `the alone is D and the inside a sentence is not`() {
        assertEquals("D", SpokenLetter.normalise("the", letters))
        assertEquals("D", SpokenLetter.normalise("The.", letters))
        assertEquals("B", SpokenLetter.normalise("the bee", letters))
        assertNull(SpokenLetter.normalise("the", setOf("A", "B", "C")))
        assertNull(SpokenLetter.normalise("the answer is on the table", letters))
        assertNull(SpokenLetter.normalise("see the sea", letters))
        assertNull(SpokenLetter.normalise("pick the one", letters))
        assertNull(SpokenLetter.normalise("I think the", letters))
        // "tea" is a normal word, not the letter D.
        assertNull(SpokenLetter.normalise("tea", letters))
        assertNull(SpokenLetter.normalise("option tea", letters))
    }

    @Test
    fun `a lower n-best or a partial can be the only match`() {
        assertEquals(
            "B",
            SpokenLetter.match(listOf("umm", "bee"), emptyList(), letters)
        )
        assertEquals(
            "B",
            SpokenLetter.match(listOf("", "b"), emptyList(), letters)
        )
        assertEquals(
            "C",
            SpokenLetter.match(listOf("huh"), listOf("see"), letters)
        )
        assertEquals(
            "B",
            SpokenLetter.match(emptyList(), listOf("noise", "option b"), letters)
        )
        val matched = SpokenLetter.decide(listOf("hey", "bravo"), emptyList(), letters)
        assertTrue(matched is SpokenLetter.Decision.Match)
        assertEquals("B", (matched as SpokenLetter.Decision.Match).letter)
    }

    @Test
    fun `a match stops the loop and does not count an attempt`() {
        val decision = SpokenLetter.decide(listOf("noise", "bee"), emptyList(), letters)
        assertTrue(SpokenLetter.stopsForMatch(decision))
        assertFalse(SpokenLetter.countsAttempt(decision))
        assertFalse(SpokenLetter.maySilenceRestart(decision))
        val step = SpokenLetter.advance(decision, completed = 1, maxAttempts = 3)
        assertTrue(step.stop)
        assertFalse(step.giveUp)
        assertEquals(1, step.attempts)
        assertEquals("B", step.letter)
        assertNull(step.status)
    }

    @Test
    fun `a non match counts an attempt and does not silence restart`() {
        val decision = SpokenLetter.decide(listOf("hello there"), emptyList(), letters)
        assertTrue(decision is SpokenLetter.Decision.Unmatched)
        assertFalse(SpokenLetter.stopsForMatch(decision))
        assertTrue(SpokenLetter.countsAttempt(decision))
        assertFalse(SpokenLetter.maySilenceRestart(decision))
        val step = SpokenLetter.advance(decision, completed = 0, maxAttempts = 3)
        assertFalse(step.stop)
        assertFalse(step.giveUp)
        assertEquals(1, step.attempts)
        assertNull(step.letter)
        assertEquals("Heard: \"hello there\"", step.status)

        val last = SpokenLetter.advance(decision, completed = 2, maxAttempts = 3)
        assertTrue(last.stop)
        assertTrue(last.giveUp)
        assertEquals(3, last.attempts)
    }

    @Test
    fun `speech with no text counts an attempt and silence does not`() {
        val spoke = SpokenLetter.decide(emptyList(), emptyList(), letters, speechDetected = true)
        assertTrue(SpokenLetter.countsAttempt(spoke))
        assertFalse(SpokenLetter.maySilenceRestart(spoke))
        val step = SpokenLetter.advance(spoke, 0, 3)
        assertEquals(1, step.attempts)
        assertNull(step.status)

        val quiet = SpokenLetter.decide(listOf("  "), listOf(null, ""), letters, speechDetected = false)
        assertTrue(quiet is SpokenLetter.Decision.Empty)
        assertTrue(SpokenLetter.maySilenceRestart(quiet))
        assertFalse(SpokenLetter.countsAttempt(quiet))
        val stayed = SpokenLetter.advance(quiet, 0, 3)
        assertFalse(stayed.stop)
        assertEquals(0, stayed.attempts)
        assertFalse(stayed.giveUp)
    }

    @Test
    fun `recognizer extras prefer online en-US with five results`() {
        assertEquals("en-US", SpokenLetter.LANGUAGE)
        assertEquals(5, SpokenLetter.MAX_RESULTS)
        assertFalse(SpokenLetter.PREFER_OFFLINE)
    }

    @Test
    fun `h79a a partial the alone is not a letter`() {
        assertNull(SpokenLetter.match(emptyList(), listOf("the"), letters))
        val d = SpokenLetter.decide(emptyList(), listOf("the"), letters)
        assertFalse(d is SpokenLetter.Decision.Match)
        assertFalse(SpokenLetter.stopsForMatch(d))
    }

    @Test
    fun `h79b a final the alone is still D`() {
        assertEquals("D", SpokenLetter.match(listOf("the"), emptyList(), letters))
        assertEquals(SpokenLetter.Decision.Match("D"), SpokenLetter.decide(listOf("the"), emptyList(), letters))
    }

    @Test
    fun `h79c own-words final after a partial the is unmatched`() {
        val d = SpokenLetter.decide(listOf("the user who created the table"), listOf("the"), letters, speechDetected = true)
        assertEquals(SpokenLetter.Decision.Unmatched("the user who created the table"), d)
    }

    @Test
    fun `h79d other partial letters still match`() {
        assertEquals("D", SpokenLetter.match(emptyList(), listOf("dee"), letters))
        assertEquals("C", SpokenLetter.match(emptyList(), listOf("see"), letters))
        assertEquals("B", SpokenLetter.match(emptyList(), listOf("the bee"), letters))
    }

    @Test
    fun `h79e normalise partial the is null and final the is D`() {
        assertNull(SpokenLetter.normalise("the", setOf("A", "B", "C"), partial = true))
        assertNull(SpokenLetter.normalise("the", letters, partial = true))
        assertNull(SpokenLetter.normalise("The.", letters, partial = true))
        assertEquals("D", SpokenLetter.normalise("the", letters))
        assertEquals("B", SpokenLetter.normalise("the bee", letters, partial = true))
    }
}
