package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTimeoutsTest {
    @Test
    fun `budgets and clocks leave room for reasoning`() {
        assertEquals(10_000, AiTimeouts.CONNECT_MS)
        assertEquals(180_000, AiTimeouts.FIRST_TOKEN_MS)
        assertEquals(60_000, AiTimeouts.IDLE_MS)
        assertEquals(300_000, AiTimeouts.TOTAL_MS)
        assertEquals(4_096, AiTimeouts.MAX_TOKENS)
        assertEquals(2_048, AiTimeouts.TEST_MAX_TOKENS)
        assertEquals(0, AiTimeouts.PROVIDER_MAX_TOKENS)
        assertEquals(4_096, AiTimeouts.budget(AiTimeouts.MAX_TOKENS))
        assertEquals(2_048, AiTimeouts.budget(AiTimeouts.TEST_MAX_TOKENS))
        assertTrue(AiTimeouts.MAX_TOKENS >= 2048)
        assertTrue(AiTimeouts.TEST_MAX_TOKENS >= 2048)
        assertTrue(AiTimeouts.FIRST_TOKEN_MS >= 180_000)
    }
}
