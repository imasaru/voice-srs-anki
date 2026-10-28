package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkOwnWordsTest {

    @Test
    fun `a free sentence with own-words on reveals and judges`() {
        assertEquals(
            ThinkTime.UnmatchedAction.REVEAL_JUDGE,
            ThinkTime.unmatchedAction("the mitochondria makes energy", ownWordsOn = true)
        )
    }

    @Test
    fun `b the same sentence with own-words off counts a miss`() {
        assertEquals(
            ThinkTime.UnmatchedAction.COUNT_MISS,
            ThinkTime.unmatchedAction("the mitochondria makes energy", ownWordsOn = false)
        )
    }

    @Test
    fun `c blank heard counts a miss even with own-words on`() {
        assertEquals(ThinkTime.UnmatchedAction.COUNT_MISS, ThinkTime.unmatchedAction("", ownWordsOn = true))
        assertEquals(ThinkTime.UnmatchedAction.COUNT_MISS, ThinkTime.unmatchedAction("   ", ownWordsOn = true))
        assertEquals(ThinkTime.UnmatchedAction.COUNT_MISS, ThinkTime.unmatchedAction(null, ownWordsOn = true))
    }

    @Test
    fun `d a spoken letter still matches via SpokenLetter`() {
        val letters = setOf("A", "B", "C", "D")
        val decision = SpokenLetter.decide(listOf("bravo"), emptyList(), letters, speechDetected = true)
        assertTrue(decision is SpokenLetter.Decision.Match)
        assertEquals("B", (decision as SpokenLetter.Decision.Match).letter)
        val free = SpokenLetter.decide(listOf("the mitochondria makes energy"), emptyList(), letters, speechDetected = true)
        assertTrue(free is SpokenLetter.Decision.Unmatched)
    }
}
