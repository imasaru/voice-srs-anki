package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralTtsTest {
    @Test
    fun `server rate passes the slider through and clamps to the server range`() {
        assertEquals(1.2f, NeuralTts.serverRate(1.2f), 0.001f)
        assertEquals(0.5f, NeuralTts.serverRate(0.5f), 0.001f)
        assertEquals(1.5f, NeuralTts.serverRate(1.5f), 0.001f)
        assertEquals(0.5f, NeuralTts.serverRate(0.1f), 0.001f)
        assertEquals(2.0f, NeuralTts.serverRate(3f), 0.001f)
        assertEquals(1f, NeuralTts.serverRate(Float.NaN), 0.001f)
    }

    @Test
    fun `timeouts match the short connect and read budget`() {
        assertEquals(3_000, NeuralTts.CONNECT_TIMEOUT_MS)
        assertEquals(15_000, NeuralTts.READ_TIMEOUT_MS)
        assertEquals(3_000, NeuralTts.HEALTH_TIMEOUT_MS)
    }

    @Test
    fun `failed health check keeps the session on-device until a later check passes`() {
        assertEquals(EngineChoice.NEURAL, NeuralTts.sessionEngine(EngineChoice.NEURAL, true))
        assertEquals(EngineChoice.ON_DEVICE, NeuralTts.sessionEngine(EngineChoice.NEURAL, false))
        assertEquals(EngineChoice.ON_DEVICE, NeuralTts.sessionEngine(EngineChoice.ON_DEVICE, true))
        assertTrue(NeuralTts.shouldProbeNeural(EngineChoice.NEURAL, healthKnown = false, blocked = false, elapsedSinceProbeMs = 0L))
        assertTrue(!NeuralTts.shouldProbeNeural(EngineChoice.ON_DEVICE, healthKnown = false, blocked = false, elapsedSinceProbeMs = 0L))
        assertTrue(!NeuralTts.shouldProbeNeural(EngineChoice.NEURAL, healthKnown = true, blocked = false, elapsedSinceProbeMs = 60_000L))
        assertTrue(!NeuralTts.shouldProbeNeural(EngineChoice.NEURAL, healthKnown = true, blocked = true, elapsedSinceProbeMs = 1_000L))
        assertTrue(NeuralTts.shouldProbeNeural(EngineChoice.NEURAL, healthKnown = true, blocked = true, elapsedSinceProbeMs = NeuralTts.HEALTH_RECHECK_MS))
    }

    @Test
    fun `cache key is stable for the same clip and changes with voice or rate`() {
        val a = NeuralTts.cacheKey("This is Voice SRS.", "en-US-AriaNeural", 0.9f)
        val b = NeuralTts.cacheKey("This is Voice SRS.", "en-US-AriaNeural", 0.90f)
        val otherVoice = NeuralTts.cacheKey("This is Voice SRS.", "en-GB-SoniaNeural", 0.9f)
        val otherRate = NeuralTts.cacheKey("This is Voice SRS.", "en-US-AriaNeural", 1.2f)
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertNotEquals(a, otherVoice)
        assertNotEquals(a, otherRate)
        assertEquals("0.90", NeuralTts.rateToken(0.9f))
        assertEquals("1.20", NeuralTts.rateToken(1.2f))
    }

    @Test
    fun `neural failure falls back to on-device and on-device does not`() {
        assertEquals(EngineChoice.NEURAL, TtsEngines.preferred("neural"))
        assertEquals(EngineChoice.NEURAL, TtsEngines.preferred(null))
        assertEquals(EngineChoice.NEURAL, TtsEngines.preferred(""))
        assertEquals(EngineChoice.ON_DEVICE, TtsEngines.preferred("device"))
        assertEquals(EngineChoice.ON_DEVICE, TtsEngines.fallback(EngineChoice.NEURAL))
        assertNull(TtsEngines.fallback(EngineChoice.ON_DEVICE))
    }

    @Test
    fun `voice json keeps name locale and gender and falls back when empty`() {
        val parsed = NeuralTts.parseVoices(
            """[{"name":"en-US-AriaNeural","locale":"en-US","gender":"Female"},{"name":"","locale":"en-US"}]"""
        )
        assertEquals(1, parsed.size)
        assertEquals("en-US-AriaNeural", parsed[0].name)
        assertEquals("en-US", parsed[0].locale)
        assertEquals("Female", parsed[0].gender)
        assertEquals(NeuralTts.FALLBACK_VOICES, NeuralTts.pickerNames(emptyList()))
        assertTrue(NeuralTts.DEFAULT_VOICE in NeuralTts.pickerNames(parsed))
        assertEquals(emptyList<NeuralVoice>(), NeuralTts.parseVoices("not-json"))
    }
}
