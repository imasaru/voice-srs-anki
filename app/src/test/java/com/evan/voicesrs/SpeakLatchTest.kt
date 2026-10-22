package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakLatchTest {
    @Test
    fun `stale utterance completion does not finish the current speak`() {
        assertTrue(SpeakLatch.isCurrent("utterance-b", "utterance-b"))
        assertFalse(SpeakLatch.isCurrent("utterance-b", "utterance-a"))
        assertFalse(SpeakLatch.isCurrent("utterance-b", null))
        assertFalse(SpeakLatch.isCurrent(null, "utterance-b"))
        assertFalse(SpeakLatch.isCurrent("", "utterance-b"))
        assertFalse(SpeakLatch.isCurrent("", ""))
    }
}
