package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure retry rules for general asks. */
class AiRetryTest {

    @Test
    fun `a dropped retries once automatically`() {
        assertTrue(AiRetry.autoRetry(ChatSend.Fail.DROPPED, 0, false))
        assertFalse(AiRetry.autoRetry(ChatSend.Fail.DROPPED, 1, false))
    }

    @Test
    fun `b other kinds never auto retry`() {
        for (f in listOf(
            ChatSend.Fail.NO_NETWORK,
            ChatSend.Fail.TIMEOUT_WAITING,
            ChatSend.Fail.TIMEOUT_GENERATING,
            ChatSend.Fail.AI
        )) {
            assertFalse(AiRetry.autoRetry(f, 0, false))
        }
    }

    @Test
    fun `c cancel never auto retries`() {
        assertFalse(AiRetry.autoRetry(ChatSend.Fail.DROPPED, 0, true))
    }

    @Test
    fun `d partial text is kept above the error`() {
        assertEquals(
            "Part one\n\nConnection dropped while the model was working. Retry?",
            AiRetry.keptText(
                "Part one ",
                "Connection dropped while the model was working. Retry?"
            )
        )
        assertEquals("x", AiRetry.keptText("  ", "x"))
    }

    @Test
    fun `e manual retry only for retryable error with a stored request`() {
        val err = ExplState.Error("x", retryable = true)
        assertTrue(AiRetry.canManualRetry(err, true))
        assertFalse(AiRetry.canManualRetry(ExplState.Error("x", retryable = false), true))
        assertFalse(AiRetry.canManualRetry(ExplState.Ready("ok", cached = false), true))
        assertFalse(AiRetry.canManualRetry(ExplState.Pending, true))
        assertFalse(AiRetry.canManualRetry(err, false))
    }
}
