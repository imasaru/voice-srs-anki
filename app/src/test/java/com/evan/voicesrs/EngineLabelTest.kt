package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `fallback utterance then neural utterance labels on-device then neural`() {
        var label = ""
        val fallback = EngineLabel.startedEngine(
            attempted = EngineChoice.NEURAL,
            started = false,
            fallbackStarted = true
        )
        label = EngineLabel.afterStart(label, fallback)
        assertEquals(EngineLabel.DEVICE, label)
        assertEquals(EngineChoice.ON_DEVICE, fallback)

        val neural = EngineLabel.startedEngine(
            attempted = EngineChoice.NEURAL,
            started = true,
            fallbackStarted = false
        )
        label = EngineLabel.afterStart(label, neural)
        assertEquals(EngineLabel.NEURAL, label)
        assertEquals(EngineChoice.NEURAL, neural)
    }

    @Test
    fun `failed engine that never starts never sets the label`() {
        val held = EngineLabel.NEURAL
        val none = EngineLabel.startedEngine(
            attempted = EngineChoice.NEURAL,
            started = false,
            fallbackStarted = false
        )
        assertNull(none)
        assertEquals(held, EngineLabel.afterStart(held, none))
        assertEquals("", EngineLabel.afterStart("", none))
        assertEquals(
            EngineLabel.DEVICE,
            EngineLabel.afterStart(
                held,
                EngineLabel.startedEngine(EngineChoice.ON_DEVICE, started = true)
            )
        )
    }
}
