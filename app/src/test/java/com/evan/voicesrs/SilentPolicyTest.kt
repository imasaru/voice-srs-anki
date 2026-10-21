package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentPolicyTest {
    @Test
    fun `silent mode is tap only and skips the microphone`() {
        val mode = SilentPolicy.of(true)
        assertTrue(mode.silent)
        assertEquals(FgsType.MEDIA_PLAYBACK, mode.fgsType)
        assertEquals("mediaPlayback", mode.fgsType.logName)
        assertFalse(mode.createRecognizer)
        assertFalse(mode.requestRecordAudio)
        assertEquals(AnswerReveal.SHOW_ANSWER, mode.answerReveal)
    }

    @Test
    fun `voice mode keeps the recognizer and microphone service`() {
        val mode = SilentPolicy.of(false)
        assertFalse(mode.silent)
        assertEquals(FgsType.MICROPHONE, mode.fgsType)
        assertEquals("microphone", mode.fgsType.logName)
        assertTrue(mode.createRecognizer)
        assertTrue(mode.requestRecordAudio)
        assertEquals(AnswerReveal.VOICE, mode.answerReveal)
    }
}
