package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

/** h105b: answer-listen silence helper (item 2). Pure, tested before wiring. */
class H105bTest {

    @Test
    fun `answer listen is four seconds capped by configured`() {
        assertEquals(4000L, ListenWindow.silenceFor(configuredMs = 1500L, answerListen = true))
    }

    @Test
    fun `answer listen keeps configured when longer than four seconds`() {
        assertEquals(5000L, ListenWindow.silenceFor(configuredMs = 5000L, answerListen = true))
    }

    @Test
    fun `non-answer listen ignores the four-second floor`() {
        assertEquals(1500L, ListenWindow.silenceFor(configuredMs = 1500L, answerListen = false))
    }
}
