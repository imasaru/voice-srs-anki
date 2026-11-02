package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechEngineTest {

    @Test
    fun `parse null blank and unknown are AUTO`() {
        assertEquals(SpeechChoice.AUTO, SpeechEngine.parse(null))
        assertEquals(SpeechChoice.AUTO, SpeechEngine.parse(""))
        assertEquals(SpeechChoice.AUTO, SpeechEngine.parse("bogus"))
    }

    @Test
    fun `parse VOSK`() {
        assertEquals(SpeechChoice.VOSK, SpeechEngine.parse("VOSK"))
    }

    @Test
    fun `order auto whisper then vosk then google`() {
        assertEquals(
            listOf(EngineKind.WHISPER, EngineKind.VOSK, EngineKind.GOOGLE),
            SpeechEngine.order(SpeechChoice.AUTO, whisperReachable = true, voskModelReady = true)
        )
    }

    @Test
    fun `order auto skips an unreachable whisper`() {
        assertEquals(
            listOf(EngineKind.VOSK, EngineKind.GOOGLE),
            SpeechEngine.order(SpeechChoice.AUTO, whisperReachable = false, voskModelReady = true)
        )
    }

    @Test
    fun `order auto is google when nothing else is ready`() {
        assertEquals(
            listOf(EngineKind.GOOGLE),
            SpeechEngine.order(SpeechChoice.AUTO, whisperReachable = false, voskModelReady = false)
        )
    }

    @Test
    fun `order whisper falls through when the server is down`() {
        assertEquals(
            listOf(EngineKind.VOSK, EngineKind.GOOGLE),
            SpeechEngine.order(SpeechChoice.WHISPER, whisperReachable = false, voskModelReady = true)
        )
    }

    @Test
    fun `order vosk without a model is google`() {
        assertEquals(
            listOf(EngineKind.GOOGLE),
            SpeechEngine.order(SpeechChoice.VOSK, whisperReachable = true, voskModelReady = false)
        )
    }

    @Test
    fun `order google is google`() {
        assertEquals(
            listOf(EngineKind.GOOGLE),
            SpeechEngine.order(SpeechChoice.GOOGLE, whisperReachable = true, voskModelReady = true)
        )
    }

    @Test
    fun `log line names the engine latency and card tag`() {
        assertEquals(
            "engine=WHISPER latencyMs=812 n=9 id=123",
            SpeechEngine.logLine(EngineKind.WHISPER, 812, "n=9 id=123", null)
        )
    }

    @Test
    fun `log line appends fallbackFrom`() {
        val line = SpeechEngine.logLine(EngineKind.WHISPER, 812, "n=9 id=123", EngineKind.VOSK)
        assertTrue(line.endsWith(" fallbackFrom=VOSK"))
        assertEquals("engine=WHISPER latencyMs=812 n=9 id=123 fallbackFrom=VOSK", line)
    }

    @Test
    fun `asr base is the asr url only`() {
        assertEquals("http://a:9", SpeechEngine.asrBase("http://a:9/"))
        assertNull(SpeechEngine.asrBase(""))
    }
}
