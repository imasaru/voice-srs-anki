package com.evan.voicesrs

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkTimeTest {

    @Test
    fun `think time choices default to 20 seconds and stay off in silent mode`() {
        assertEquals(intArrayOf(0, 5, 10, 15, 20, 30, 45, 60).toList(), ThinkTime.CHOICES_SEC.toList())
        assertEquals(20, ThinkTime.DEFAULT_SEC)
        assertEquals(ThinkTime.LABEL, "Think time")
        assertEquals(ThinkTime.HELP, "How long to give you to think before the answer is read.")
        assertEquals("Off", ThinkTime.label(0))
        assertEquals("20 s", ThinkTime.label(20))
        assertEquals("60 s", ThinkTime.label(60))
        assertEquals(0, ThinkTime.fromLabel("Off"))
        assertEquals(20, ThinkTime.fromLabel("20 s"))
        assertEquals(5, ThinkTime.fromLabel("5 s"))
        assertEquals(20, ThinkTime.fromLabel("7 s"))
        assertEquals(20, ThinkTime.fromLabel("nope"))
        assertEquals(20, ThinkTime.normalize(7))
        assertEquals(0, ThinkTime.normalize(0))
        assertTrue(ThinkTime.armed(20, silent = false))
        assertFalse(ThinkTime.armed(20, silent = true))
        assertFalse(ThinkTime.armed(0, silent = false))
        assertEquals(20_000L, ThinkTime.durationMs(20))
        assertEquals(0L, ThinkTime.durationMs(0))
    }

    @Test
    fun `answer duration is end of question to grade and unknown when unarmed`() {
        assertEquals(4_200L, ThinkTime.answerDurationMs(1_000L, 5_200L))
        assertEquals(1L, ThinkTime.answerDurationMs(10L, 11L))
        assertEquals(0L, ThinkTime.answerDurationMs(0L, 5_200L))
        assertEquals(0L, ThinkTime.answerDurationMs(-1L, 5_200L))
        assertEquals(0L, ThinkTime.answerDurationMs(5_200L, 5_200L))
        assertEquals(0L, ThinkTime.answerDurationMs(5_201L, 5_200L))
        assertEquals(0L, ThinkTime.answerDurationMs(1_000L, 0L))
        assertEquals(ThinkTime.UNKNOWN_DURATION_MS, ThinkTime.answerDurationMs(0L, 0L))
    }

    @Test
    fun `countdown is app side and expires on its own clock`() {
        assertEquals(20, ThinkTime.remainingSec(0L, 20_000L))
        assertEquals(1, ThinkTime.remainingSec(19_001L, 20_000L))
        assertEquals(0, ThinkTime.remainingSec(20_000L, 20_000L))
        assertFalse(ThinkTime.expired(19_999L, 20_000L))
        assertTrue(ThinkTime.expired(20_000L, 20_000L))
        assertEquals("19s", ThinkTime.countdownText(19))
        assertEquals("0s", ThinkTime.countdownText(0))
    }

    @Test
    fun `silence restarts during think time do not charge a listen attempt`() {
        assertTrue(ThinkTime.silentRestart(ListenWindow.ERROR_NO_MATCH))
        assertTrue(ThinkTime.silentRestart(ListenWindow.ERROR_SPEECH_TIMEOUT))
        assertFalse(ThinkTime.silentRestart(SpeechRecognizer.ERROR_AUDIO))
        assertFalse(ThinkTime.silentRestart(SpeechRecognizer.ERROR_CLIENT))
        assertFalse(ThinkTime.silentRestart(0))
        assertEquals(0, ThinkTime.attemptsCharged(0))
        assertEquals(0, ThinkTime.attemptsCharged(1))
        assertEquals(0, ThinkTime.attemptsCharged(12))
    }

    @Test
    fun `think slice ignores the grade wait setting`() {
        assertEquals(8_000L, ThinkTime.listenSliceMs(20_000L, gradeWaitMs = 15_000L))
        assertEquals(8_000L, ThinkTime.listenSliceMs(60_000L, gradeWaitMs = 5_000L))
        assertEquals(3_000L, ThinkTime.listenSliceMs(3_000L, gradeWaitMs = 30_000L))
        assertNotEquals(15_000L, ThinkTime.listenSliceMs(60_000L, gradeWaitMs = 15_000L))
        assertNotEquals(5_000L, ThinkTime.listenSliceMs(60_000L, gradeWaitMs = 5_000L))
    }

    @Test
    fun `spoken answer show answer and the tap end think time early`() {
        assertEquals(ThinkTime.Hear.ANSWER, ThinkTime.classify("delta lake"))
        assertEquals(ThinkTime.Hear.ANSWER, ThinkTime.classify("B"))
        assertEquals(ThinkTime.Hear.SHOW_ANSWER, ThinkTime.classify("show answer"))
        assertEquals(ThinkTime.Hear.SHOW_ANSWER, ThinkTime.classify("Show the answer."))
        assertEquals(ThinkTime.Hear.EMPTY, ThinkTime.classify("   "))
        assertEquals(ThinkTime.Hear.EMPTY, ThinkTime.classify(null))
        assertEquals(ThinkTime.Hear.REPEAT, ThinkTime.classify("repeat"))
        assertEquals(ThinkTime.Hear.SKIP, ThinkTime.classify("skip"))
        assertEquals(ThinkTime.Hear.STOP_DRILL, ThinkTime.classify("pause"))
        assertTrue(ThinkTime.endsEarly(ThinkTime.Hear.ANSWER))
        assertTrue(ThinkTime.endsEarly(ThinkTime.Hear.SHOW_ANSWER))
        assertFalse(ThinkTime.endsEarly(ThinkTime.Hear.EMPTY))
        assertTrue(ThinkTime.isShowAnswer("show answer"))
        assertFalse(ThinkTime.isShowAnswer("show me a hint"))
    }
}
