package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPreflightTest {
    @Test
    fun `version and deck are required`() {
        val ok = ConnectionPreflight.evaluate(6, listOf("Default", "VoiceSRS-Test"), "VoiceSRS-Test", null)
        assertTrue(ok.ok)

        val down = ConnectionPreflight.evaluate(null, null, "VoiceSRS-Test", "connection refused")
        assertFalse(down.ok)
        assertTrue(down.message.contains("Can't reach Anki"))

        val missing = ConnectionPreflight.evaluate(6, listOf("Default"), "VoiceSRS-Test", null)
        assertFalse(missing.ok)
        assertTrue(missing.message.contains("VoiceSRS-Test"))
    }
}
