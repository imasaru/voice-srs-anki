package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VadTest {

    private val quiet = ShortArray(320)
    private val speech = ShortArray(320) { 8_000 }

    @Test
    fun `no end before the first speech`() {
        val vad = SilenceVad(thresholdRms = 500.0, silenceMs = 400)
        // Ten seconds of quiet, including a span past any 10 s cap, stays open.
        repeat(500) {
            assertFalse(vad.feed(quiet))
        }
        assertFalse(vad.heardSpeech())
    }

    @Test
    fun `ends after silenceMs of quiet once speech has started`() {
        val vad = SilenceVad(thresholdRms = 500.0, silenceMs = 400)
        assertFalse(vad.feed(speech))
        assertTrue(vad.heardSpeech())
        // 19 quiet frames are 380 ms. The 20th reaches 400 ms.
        repeat(19) {
            assertFalse(vad.feed(quiet))
        }
        assertTrue(vad.feed(quiet))
    }

    @Test
    fun `continuous speech has no 10 second cap`() {
        val vad = SilenceVad(thresholdRms = 500.0, silenceMs = 400)
        // 25 s at 20 ms per frame.
        repeat(1_250) {
            assertFalse(vad.feed(speech))
        }
        assertTrue(vad.heardSpeech())
    }
}
