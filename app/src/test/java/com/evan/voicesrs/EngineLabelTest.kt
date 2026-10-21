package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineLabelTest {
    @Test
    fun `neural only when configured and healthy`() {
        assertEquals("Checking…", EngineLabel.of(EngineChoice.NEURAL, null))
        assertEquals("Neural", EngineLabel.of(EngineChoice.NEURAL, true))
        assertEquals("Device voice", EngineLabel.of(EngineChoice.NEURAL, false))
    }

    @Test
    fun `device mode never waits on a probe`() {
        assertEquals("Device voice", EngineLabel.of(EngineChoice.ON_DEVICE, null))
        assertEquals("Device voice", EngineLabel.of(EngineChoice.ON_DEVICE, true))
        assertEquals("Device voice", EngineLabel.of(EngineChoice.ON_DEVICE, false))
    }
}
