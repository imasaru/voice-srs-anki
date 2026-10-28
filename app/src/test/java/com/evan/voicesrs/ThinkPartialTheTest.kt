package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** h79: a partial "the" must not end the listen; the own-words final reaches the judge. */
class ThinkPartialTheTest {
    private val letters = setOf("A", "B", "C", "D")

    @Test
    fun `a partial the then an own-words final reveals and judges`() {
        val early = SpokenLetter.decide(emptyList(), listOf("the"), letters, speechDetected = true)
        assertFalse(SpokenLetter.stopsForMatch(early))
        val final = SpokenLetter.decide(
            listOf("the user who created the table"), listOf("the"), letters, speechDetected = true
        )
        val heard = (final as SpokenLetter.Decision.Unmatched).heard
        assertEquals(ThinkTime.UnmatchedAction.REVEAL_JUDGE, ThinkTime.unmatchedAction(heard, ownWordsOn = true))
    }

    @Test
    fun `own-words off still counts a miss`() {
        val final = SpokenLetter.decide(
            listOf("the user who created the table"), listOf("the"), letters, speechDetected = true
        )
        val heard = (final as SpokenLetter.Decision.Unmatched).heard
        assertEquals(ThinkTime.UnmatchedAction.COUNT_MISS, ThinkTime.unmatchedAction(heard, ownWordsOn = false))
    }
}
