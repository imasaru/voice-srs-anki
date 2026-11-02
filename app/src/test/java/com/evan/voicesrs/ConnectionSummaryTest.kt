package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.SocketTimeoutException

class ConnectionSummaryTest {
    @Test
    fun `anki summary is one line with the deck leaf`() {
        assertEquals(
            "✓ Connected · AnkiConnect v6 · 213 decks · 'Mock Exam 1': 45 due",
            ConnectionSummary.ankiOk(6, 213, "Databricks DEA::kengio::Mock Exam 1", 45)
        )
        assertEquals(
            "✓ Connected · AnkiConnect v6 · 1 decks · 'VoiceSRS-Test': 0 due",
            ConnectionSummary.ankiOk(6, 1, "VoiceSRS-Test", 0)
        )
    }

    @Test
    fun `ai summary quotes the reply and the elapsed seconds`() {
        assertEquals(
            "✓ mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit replied in 1.8 s: \"okay\"",
            ConnectionSummary.aiOk("mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit", 1800, "okay")
        )
        assertEquals(
            "✓ qwen replied in 0.5 s: \"ok\"",
            ConnectionSummary.aiOk("qwen", 500, " ok\n")
        )
    }

    @Test
    fun `failures are a short reason without a stack`() {
        assertEquals("✗ Timed out", ConnectionSummary.failure(SocketTimeoutException("Read timed out")))
        assertEquals("✗ HTTP 401", ConnectionSummary.failure(AiHttpException(401)))
        val boom = RuntimeException(
            "Could not reach AnkiConnect\n\tat com.evan.voicesrs.AnkiConnect.post(AnkiConnect.kt:60)"
        )
        val line = ConnectionSummary.failure(boom)
        assertEquals("✗ Could not reach AnkiConnect", line)
        assertFalse(line.contains("AnkiConnect.kt"))
        assertFalse(line.contains("decks:"))
    }
}
