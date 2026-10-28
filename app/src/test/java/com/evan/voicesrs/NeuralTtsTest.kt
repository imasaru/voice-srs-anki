package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralTtsTest {
    @Test
    fun `server rate clamps to the edge server effective max of 2x`() {
        // server.py rate_str clamps 0.5–2.0 and emits "+N%". 2.0× is "+100%".
        // edge-tts only checks ^[+-]\d+%$ and would accept a larger percent,
        // but the server never sends one. Custom 4.0× is spoken at 2.0×.
        assertEquals(2.0f, NeuralTts.MAX_RATE, 0.001f)
        assertEquals(1.2f, NeuralTts.serverRate(1.2f), 0.001f)
        assertEquals(0.5f, NeuralTts.serverRate(0.5f), 0.001f)
        assertEquals(1.5f, NeuralTts.serverRate(1.5f), 0.001f)
        assertEquals(2.0f, NeuralTts.serverRate(2.0f), 0.001f)
        assertEquals(0.5f, NeuralTts.serverRate(0.1f), 0.001f)
        assertEquals(2.0f, NeuralTts.serverRate(2.6f), 0.001f)
        assertEquals(2.0f, NeuralTts.serverRate(3f), 0.001f)
        assertEquals(2.0f, NeuralTts.serverRate(4f), 0.001f)
        assertEquals(1f, NeuralTts.serverRate(Float.NaN), 0.001f)
        assertEquals("+0%", NeuralTts.serverPercent(1f))
        assertEquals("+20%", NeuralTts.serverPercent(1.2f))
        assertEquals("-50%", NeuralTts.serverPercent(0.5f))
        assertEquals("+100%", NeuralTts.serverPercent(2f))
        assertEquals("+100%", NeuralTts.serverPercent(2.6f))
        assertEquals("+100%", NeuralTts.serverPercent(4f))
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

    @Test
    fun `parseMarks reads the speak2 marks array`() {
        val marks = NeuralTts.parseMarks("""[{"o":0,"d":300,"t":"One"},{"o":300,"d":250,"t":"two"}]""")
        assertEquals(listOf(ReadingTimeline.Mark(0, 300, "One"), ReadingTimeline.Mark(300, 250, "two")), marks)
    }

    @Test
    fun `parseMarks returns empty for bad or missing input`() {
        assertEquals(emptyList<ReadingTimeline.Mark>(), NeuralTts.parseMarks("not json"))
        assertEquals(emptyList<ReadingTimeline.Mark>(), NeuralTts.parseMarks(""))
        assertEquals(emptyList<ReadingTimeline.Mark>(), NeuralTts.parseMarks(null.toString()))
    }

    @Test
    fun `parseMarks skips blank or missing word`() {
        val marks = NeuralTts.parseMarks("""[{"o":5,"d":1,"t":""},{"o":6,"d":1}]""")
        assertEquals(emptyList<ReadingTimeline.Mark>(), marks)
    }

    @Test
    fun `marksJson round trips through parseMarks`() {
        val list = listOf(ReadingTimeline.Mark(0, 1, "a"), ReadingTimeline.Mark(1500, 320, "Héllo"))
        assertEquals(list, NeuralTts.parseMarks(NeuralTts.marksJson(list)))
    }

    @Test
    fun `marksJson of an empty list is empty array`() {
        assertEquals("[]", NeuralTts.marksJson(emptyList()))
    }
}
