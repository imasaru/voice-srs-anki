package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWordsJudgeLettersTest {
    private val abcd = setOf("A", "B", "C", "D")

    @Test fun spokenLetterWordsAreJudgedWhenLettersOff() {
        assertTrue(OwnWordsJudge.shouldJudge("see", abcd, letterListen = false))
        assertTrue(OwnWordsJudge.shouldJudge("bee", abcd, letterListen = false))
    }

    @Test fun tappedLetterIsNeverJudged() {
        assertFalse(OwnWordsJudge.shouldJudge("B", abcd, letterListen = false, tapped = "B"))
    }

    @Test fun defaultsKeepOldBehaviour() {
        assertFalse(OwnWordsJudge.shouldJudge("bee", abcd))
        assertTrue(OwnWordsJudge.shouldJudge("photosynthesis", abcd))
    }
}
