package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H107b2Test {
    private val letters = setOf("A", "B", "C", "D")
    private val KEY = "B. Creates a parameterized input that can be overridden"

    @Test fun isNonAnswer() {
        assertTrue(OwnWordsJudge.isNonAnswer("again"))
        assertTrue(OwnWordsJudge.isNonAnswer("Again."))
        assertTrue(OwnWordsJudge.isNonAnswer("skip"))
        assertTrue(OwnWordsJudge.isNonAnswer("um"))
        assertTrue(OwnWordsJudge.isNonAnswer("good"))
        assertTrue(OwnWordsJudge.isNonAnswer("repeat that"))
        assertFalse(OwnWordsJudge.isNonAnswer("a widget creates a parameter"))
        assertFalse(OwnWordsJudge.isNonAnswer("the union of the two frames"))
    }

    @Test fun shouldJudge() {
        assertFalse(OwnWordsJudge.shouldJudge("again", letters))
        assertFalse(OwnWordsJudge.shouldJudge("good", letters))
        assertTrue(OwnWordsJudge.shouldJudge("the union of the two frames", letters))
    }

    @Test fun rubberStamp() {
        assertTrue(OwnWordsJudge.rubberStamp(KEY, KEY, "again"))
        assertFalse(OwnWordsJudge.rubberStamp(KEY, KEY, "it creates a parameterized input that can be overridden at run time"))
        assertFalse(OwnWordsJudge.rubberStamp("Names time travel on Delta.", "Delta Lake time travel", "time travel in delta"))
    }

    @Test fun guardVerdict() {
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.guardVerdict(OwnWordsJudge.Verdict.Graded(3, OwnWordsJudge.Rating.GOOD, KEY), KEY, "again"))
        assertEquals(OwnWordsJudge.Verdict.Graded(1, OwnWordsJudge.Rating.AGAIN, KEY), OwnWordsJudge.guardVerdict(OwnWordsJudge.Verdict.Graded(1, OwnWordsJudge.Rating.AGAIN, KEY), KEY, "again"))
        assertTrue(OwnWordsJudge.guardVerdict(OwnWordsJudge.Verdict.Graded(3, OwnWordsJudge.Rating.GOOD, "Names the widget."), KEY, "a widget") is OwnWordsJudge.Verdict.Graded)
    }
}
