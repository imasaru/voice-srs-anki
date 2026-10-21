package com.evan.voicesrs

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenWindowTest {

    @Test
    fun `silence error codes match SpeechRecognizer`() {
        assertEquals(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, ListenWindow.ERROR_SPEECH_TIMEOUT)
        assertEquals(SpeechRecognizer.ERROR_NO_MATCH, ListenWindow.ERROR_NO_MATCH)
    }

    @Test
    fun `silence before speech restarts while time remains`() {
        assertTrue(ListenWindow.shouldRestart(false, ListenWindow.ERROR_SPEECH_TIMEOUT, 0, 15_000, 0))
        assertTrue(ListenWindow.shouldRestart(false, ListenWindow.ERROR_NO_MATCH, 1_000, 15_000, 2))
    }

    @Test
    fun `speech already started does not restart`() {
        assertFalse(ListenWindow.shouldRestart(true, ListenWindow.ERROR_SPEECH_TIMEOUT, 0, 15_000, 0))
        assertFalse(ListenWindow.shouldRestart(true, ListenWindow.ERROR_NO_MATCH, 0, 15_000, 0))
    }

    @Test
    fun `other errors do not restart`() {
        assertFalse(ListenWindow.shouldRestart(false, SpeechRecognizer.ERROR_CLIENT, 0, 15_000, 0))
        assertFalse(ListenWindow.shouldRestart(false, SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 0, 15_000, 0))
        assertFalse(ListenWindow.shouldRestart(false, 0, 0, 15_000, 0))
    }

    @Test
    fun `restart needs at least 1500 ms and stops after 10`() {
        assertTrue(ListenWindow.shouldRestart(false, ListenWindow.ERROR_NO_MATCH, 0, 1_500, 0))
        assertFalse(ListenWindow.shouldRestart(false, ListenWindow.ERROR_NO_MATCH, 0, 1_499, 0))
        assertFalse(ListenWindow.shouldRestart(false, ListenWindow.ERROR_SPEECH_TIMEOUT, 5_000, 4_000, 0))
        assertTrue(ListenWindow.shouldRestart(false, ListenWindow.ERROR_NO_MATCH, 0, 10_000, 9))
        assertFalse(ListenWindow.shouldRestart(false, ListenWindow.ERROR_NO_MATCH, 0, 10_000, 10))
        assertFalse(ListenWindow.shouldRestart(false, ListenWindow.ERROR_NO_MATCH, 0, 10_000, 11))
    }

    @Test
    fun `answer relisten is one extra window and not for speed or stop`() {
        assertTrue(ListenWindow.shouldRelistenAnswer(null, relistenOnce = true, stopped = false, speedMode = false))
        assertFalse(ListenWindow.shouldRelistenAnswer(null, relistenOnce = false, stopped = false, speedMode = false))
        assertFalse(ListenWindow.shouldRelistenAnswer(null, relistenOnce = true, stopped = true, speedMode = false))
        assertFalse(ListenWindow.shouldRelistenAnswer(null, relistenOnce = true, stopped = false, speedMode = true))
        assertFalse(ListenWindow.shouldRelistenAnswer("good", relistenOnce = true, stopped = false, speedMode = false))
        assertFalse(ListenWindow.shouldRelistenAnswer("", relistenOnce = true, stopped = false, speedMode = false))
    }

    @Test
    fun `a silent answer plus a silent relisten is one null`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(ListenWindow.healthSample(null, null, didRelisten = true)))
        assertFalse(t.recordHeard(ListenWindow.healthSample(null, null, didRelisten = true)))
        assertTrue(t.recordHeard(ListenWindow.healthSample(null, null, didRelisten = true)))
    }

    @Test
    fun `a heard relisten clears the streak`() {
        val t = ListenHealthTracker(3)
        assertFalse(t.recordHeard(ListenWindow.healthSample(null, null, didRelisten = true)))
        assertFalse(t.recordHeard(ListenWindow.healthSample(null, null, didRelisten = true)))
        val heard = ListenWindow.healthSample("ignored", "paris", didRelisten = true)
        assertEquals("paris", heard)
        assertFalse(t.recordHeard(heard))
        assertEquals("paris", ListenWindow.healthSample("paris", null, didRelisten = false))
        assertFalse(t.recordHeard(null))
        assertFalse(t.recordHeard(null))
    }

    @Test
    fun `wait and silence snap to the allowed choices`() {
        assertEquals(15, ListenWindow.normalizeWaitSec(15))
        assertEquals(15, ListenWindow.normalizeWaitSec(7))
        assertEquals(30, ListenWindow.normalizeWaitSec(30))
        assertEquals(5, ListenWindow.normalizeWaitSec(5))
        assertEquals(1500, ListenWindow.normalizeSilenceMs(1500))
        assertEquals(1500, ListenWindow.normalizeSilenceMs(999))
        assertEquals(3000, ListenWindow.normalizeSilenceMs(3000))
        assertEquals(15_000L, ListenWindow.waitMs(15))
        assertEquals(15_000L, ListenWindow.waitMs(99))
        assertEquals(20_000L, ListenWindow.waitMs(20))
    }

    @Test
    fun `settings labels parse back to the choice`() {
        assertEquals(15, ListenWindow.waitSecFromLabel("15 seconds"))
        assertEquals(5, ListenWindow.waitSecFromLabel("5 seconds"))
        assertEquals(15, ListenWindow.waitSecFromLabel("nope"))
        assertEquals(15, ListenWindow.waitSecFromLabel("7 seconds"))
        assertEquals(1500, ListenWindow.silenceMsFromLabel("1500 ms"))
        assertEquals(3000, ListenWindow.silenceMsFromLabel("3000 ms"))
        assertEquals(1500, ListenWindow.silenceMsFromLabel(""))
        assertEquals(ListenWindow.ANSWER_RELISTEN_CUE, "Take your time.")
    }
}
