package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceStatusLineTest {
    @Test
    fun `edge line names the voice and the offline fallback`() {
        assertEquals("Aria", VoiceStatusLine.edgeDisplayName("en-US-AriaNeural"))
        assertEquals("Ava", VoiceStatusLine.edgeDisplayName("en-US-AvaMultilingualNeural"))
        assertEquals(
            "Voice: Aria (Edge, natural) · falls back to device voice offline",
            VoiceStatusLine.format(EngineChoice.NEURAL, "en-US-AriaNeural")
        )
    }

    @Test
    fun `device line uses the stored name`() {
        assertEquals(
            "Voice: System default (device)",
            VoiceStatusLine.format(EngineChoice.ON_DEVICE, "  ")
        )
        assertEquals(
            "Voice: English (device)",
            VoiceStatusLine.format(EngineChoice.ON_DEVICE, "English")
        )
    }

    @Test
    fun `a failed edge probe resolves to the device engine`() {
        assertEquals(EngineChoice.NEURAL, VoiceStatusLine.resolve(EngineChoice.NEURAL, true))
        assertEquals(EngineChoice.NEURAL, VoiceStatusLine.resolve(EngineChoice.NEURAL, null))
        assertEquals(EngineChoice.ON_DEVICE, VoiceStatusLine.resolve(EngineChoice.NEURAL, false))
        assertEquals(EngineChoice.ON_DEVICE, VoiceStatusLine.resolve(EngineChoice.ON_DEVICE, true))
        assertEquals(EngineChoice.ON_DEVICE, VoiceStatusLine.resolve(EngineChoice.ON_DEVICE, null))
    }

    @Test
    fun `narrow header joins done and left with the voice line`() {
        val voice = VoiceStatusLine.format(EngineChoice.NEURAL, "en-US-AriaNeural")
        assertEquals(
            "Done 4 · Left 16 · $voice",
            VoiceStatusLine.headerLine("Done 4 · Left 16 · Streak 3 · ~8 min", voice)
        )
        assertEquals(voice, VoiceStatusLine.headerLine("", voice))
        assertEquals(
            "Done 4 · Left 16 · Goal 20 cards · $voice",
            VoiceStatusLine.headerLine("Done 4 · Left 16 · Streak 3", voice, "Goal 20 cards")
        )
    }
}
