package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillNoticeTest {

    @Test
    fun `only a real microphone error blames the mic`() {
        assertTrue(DrillNotice.blamesMic(DrillNotice.ERROR_AUDIO, recognizerUnavailable = false))
        assertTrue(DrillNotice.blamesMic(DrillNotice.ERROR_INSUFFICIENT_PERMISSIONS, false))
        assertTrue(DrillNotice.blamesMic(error = null, recognizerUnavailable = true))
        assertFalse(DrillNotice.blamesMic(DrillNotice.ERROR_NO_MATCH, false))
        assertFalse(DrillNotice.blamesMic(DrillNotice.ERROR_SPEECH_TIMEOUT, false))
        assertFalse(DrillNotice.blamesMic(0, false))
        assertFalse(DrillNotice.blamesMic(1, false))
        assertFalse(DrillNotice.blamesMic(5, false))
        assertNull(DrillNotice.text(DrillNotice.ERROR_NO_MATCH, false))
        assertNull(DrillNotice.text(DrillNotice.ERROR_SPEECH_TIMEOUT, false))
        assertNull(DrillNotice.text(0, false))
        val warning = DrillNotice.text(DrillNotice.ERROR_AUDIO, false)
        assertTrue(warning!!.contains("microphone does not seem to be working"))
        assertEquals(warning, DrillNotice.text(null, recognizerUnavailable = true))
        assertEquals(warning, DrillNotice.text(DrillNotice.ERROR_INSUFFICIENT_PERMISSIONS, false))
    }

    @Test
    fun `every drill notice goes to the single status sink`() {
        for (notice in listOf(
            "The microphone does not seem to be working.",
            "Didn't hear a grade, switched to silent",
            "Listening for your grade",
            "Heard: \"bee\"",
            "Silent mode"
        )) {
            assertEquals(DrillNotice.Sink.STATUS, DrillNotice.route(notice))
        }
        assertFalse(DrillNotice.route("Heard: \"a\"").name == "CARD")
        assertEquals(DrillNotice.Sink.STATUS, DrillNotice.sink())
    }
}