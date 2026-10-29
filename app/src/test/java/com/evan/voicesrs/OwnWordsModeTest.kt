package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWordsModeTest {
    private val abcd = setOf("A", "B", "C", "D")

    @Test fun ownWordsWithoutLetterListenMatchesNoLetters() {
        assertNull(OwnWordsMode.spokenLetters(abcd, ownWords = true, letterListen = false))
        assertFalse(OwnWordsMode.listensForLetters(ownWords = true, letterListen = false))
    }

    @Test fun lettersStayWhenOwnWordsOffOrLetterListenOn() {
        assertEquals(abcd, OwnWordsMode.spokenLetters(abcd, ownWords = false, letterListen = false))
        assertEquals(abcd, OwnWordsMode.spokenLetters(abcd, ownWords = true, letterListen = true))
        assertNull(OwnWordsMode.spokenLetters(null, ownWords = false, letterListen = true))
    }

    @Test fun nextAndPassAreAnswersWhenLettersOff() {
        assertEquals(VoiceAction.None, OwnWordsMode.answerAction("next", lettersOn = false))
        assertEquals(VoiceAction.None, OwnWordsMode.answerAction("pass", lettersOn = false))
        assertEquals(VoiceAction.None, OwnWordsMode.answerAction("next card", lettersOn = false))
        assertEquals(ThinkTime.Hear.ANSWER, OwnWordsMode.thinkHear("next", lettersOn = false))
    }

    @Test fun strictSkipStillSkips() {
        assertEquals(VoiceAction.Skip, OwnWordsMode.answerAction("skip", lettersOn = false))
        assertEquals(VoiceAction.Skip, OwnWordsMode.answerAction("Skip card.", lettersOn = false))
        assertEquals(ThinkTime.Hear.SKIP, OwnWordsMode.thinkHear("skip", lettersOn = false))
    }

    @Test fun repeatAndPauseUnchanged() {
        assertEquals(VoiceAction.Repeat, OwnWordsMode.answerAction("say it again", lettersOn = false))
        assertEquals(VoiceAction.Pause, OwnWordsMode.answerAction("hold on", lettersOn = false))
        assertEquals(ThinkTime.classify("repeat"), OwnWordsMode.thinkHear("repeat", lettersOn = false))
    }

    @Test fun lettersOnIsTodaysBehaviour() {
        assertEquals(GradeMapper.parse("next"), OwnWordsMode.answerAction("next", lettersOn = true))
        assertEquals(ThinkTime.classify("pass"), OwnWordsMode.thinkHear("pass", lettersOn = true))
        assertTrue(OwnWordsMode.isStrictSkip("  SKIP! "))
    }

    @Test fun answerBPicksOnFinalOnly() {
        assertEquals("B", OwnWordsMode.prefixedLetter("answer b", abcd, partial = false))
        assertEquals("B", OwnWordsMode.prefixedLetter("Option B.", abcd, partial = false))
        assertEquals("C", OwnWordsMode.prefixedLetter("answer see", abcd, partial = false))
        assertEquals("D", OwnWordsMode.prefixedLetter("answer dee", abcd, partial = false))
        assertNull(OwnWordsMode.prefixedLetter("answer b", abcd, partial = true))
    }

    @Test fun onlyTheExplicitPrefixedFormPicks() {
        assertNull(OwnWordsMode.prefixedLetter("b", abcd, partial = false))
        assertNull(OwnWordsMode.prefixedLetter("bee", abcd, partial = false))
        assertNull(OwnWordsMode.prefixedLetter("the", abcd, partial = false))
        assertNull(OwnWordsMode.prefixedLetter("answer the", abcd, partial = false))
        assertNull(OwnWordsMode.prefixedLetter("the answer is basically b", abcd, partial = false))
        assertNull(OwnWordsMode.prefixedLetter("answer b because", abcd, partial = false))
    }

    @Test fun answerBIsAnAnswerInBothWindows() {
        assertEquals(VoiceAction.None, OwnWordsMode.answerAction("answer b", lettersOn = false))
        assertEquals(ThinkTime.Hear.ANSWER, OwnWordsMode.thinkHear("answer b", lettersOn = false))
        assertEquals(ThinkTime.Hear.ANSWER, OwnWordsMode.thinkHear("option b", lettersOn = false))
    }
}
