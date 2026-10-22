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
    fun `a miss opens another listen until the attempt count`() {
        assertTrue(ListenWindow.openAnother(1, 3, lastMissed = true, stopped = false))
        assertTrue(ListenWindow.openAnother(2, 3, lastMissed = true, stopped = false))
        assertFalse(ListenWindow.openAnother(3, 3, lastMissed = true, stopped = false))
        assertTrue(ListenWindow.gaveUp(3, 3, lastMissed = true))
        assertFalse(ListenWindow.gaveUp(2, 3, lastMissed = true))
        assertFalse(ListenWindow.openAnother(1, 3, lastMissed = false, stopped = false))
        assertFalse(ListenWindow.openAnother(1, 3, lastMissed = true, stopped = true))
        assertFalse(ListenWindow.gaveUp(1, 1, lastMissed = false))
        assertTrue(ListenWindow.gaveUp(1, 1, lastMissed = true))
        assertEquals(ListenWindow.SILENT_NOTICE, "Didn't hear a grade, switched to silent")
    }

    @Test
    fun `listen attempts migrate the old switch and clamp to 1 through 5`() {
        assertEquals(3, ListenWindow.migrateAttempts(null, null))
        assertEquals(2, ListenWindow.migrateAttempts(null, true))
        assertEquals(1, ListenWindow.migrateAttempts(null, false))
        assertEquals(4, ListenWindow.migrateAttempts(4, true))
        assertEquals(1, ListenWindow.migrateAttempts(0, null))
        assertEquals(5, ListenWindow.migrateAttempts(9, false))
        assertEquals(3, ListenWindow.DEFAULT_ATTEMPTS)
        assertEquals(1, ListenWindow.MIN_ATTEMPTS)
        assertEquals(5, ListenWindow.MAX_ATTEMPTS)
        assertEquals(3, ListenWindow.attemptsFromLabel("3"))
        assertEquals(5, ListenWindow.attemptsFromLabel("8"))
        assertEquals(3, ListenWindow.attemptsFromLabel("nope"))
        assertEquals(ListenWindow.ATTEMPTS_LABEL, "Listen attempts")
        assertEquals(ListenWindow.ATTEMPTS_HELP, "How many times to listen before switching to silent.")
    }

    @Test
    fun `silence and no match are not a microphone fault`() {
        assertFalse(ListenWindow.isMicFault(ListenWindow.ERROR_NO_MATCH))
        assertFalse(ListenWindow.isMicFault(ListenWindow.ERROR_SPEECH_TIMEOUT))
        assertFalse(ListenWindow.isMicFault(0))
        assertTrue(ListenWindow.isMicFault(SpeechRecognizer.ERROR_AUDIO))
        assertTrue(ListenWindow.isMicFault(SpeechRecognizer.ERROR_NETWORK))
        assertTrue(ListenWindow.isMicFault(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertTrue(ListenWindow.isMicFault(SpeechRecognizer.ERROR_CLIENT))
        val health = ListenHealthTracker()
        assertFalse(health.recordHeard(null))
        assertFalse(health.recordHeard(null))
        assertFalse(health.recordHeard(""))
        assertTrue(health.recordFault())
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
