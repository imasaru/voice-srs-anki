package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RetryWordingTest {
    @Test
    fun `a tap retry text says tap to retry`() {
        assertEquals("Connection dropped while the model was working. Tap to retry.", ChatSend.tapRetry(ChatSend.Fail.DROPPED))
        assertEquals("The model stopped mid-answer. Tap to retry.", ChatSend.tapRetry(ChatSend.Fail.TIMEOUT_GENERATING))
        assertEquals("The AI could not answer. Tap to retry.", ChatSend.tapRetry(ChatSend.Fail.AI))
        assertEquals("No connection. The question was not sent. Tap to retry.", ChatSend.tapRetry(ChatSend.Fail.NO_NETWORK))
    }

    @Test
    fun `b no tap retry paths never say retry`() {
        for (f in ChatSend.Fail.values()) {
            assertFalse(ChatSend.askAgain(f).contains("Retry"))
            assertFalse(ChatSend.askAgain(f).contains("Tap"))
        }
        assertEquals("The model stopped mid-answer. Ask again.", ChatSend.askAgain(ChatSend.Fail.TIMEOUT_GENERATING))
        assertEquals("The AI could not answer. Try again.", ChatSend.askAgain(ChatSend.Fail.AI))
    }
}
