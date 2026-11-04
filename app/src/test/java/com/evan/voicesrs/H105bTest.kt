package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * h105b: answer-listen silence helper (item 2). Pure, tested before wiring.
 *
 * h141 TESTEDIT: the answer-listen silence is now the user-configured value
 * [answerSilenceSec] (seconds, default 4), which REPLACES the old 4 s floor.
 * `configuredMs` no longer governs answer listens; it is ignored. The second
 * test below was rewritten from "keeps configured when longer than four seconds"
 * because that rule no longer holds — the configured seconds (1–10) always wins.
 */
class H105bTest {

    @Test
    fun `answer listen uses the configured seconds (default four)`() {
        assertEquals(4000L, ListenWindow.silenceFor(configuredMs = 1500L, answerListen = true))
    }

    @Test
    fun `answer listen honours a shorter configured seconds`() {
        // h141: 1–3 s is now honoured (replaces the old 4 s floor), not raised to it.
        assertEquals(2000L, ListenWindow.silenceFor(configuredMs = 1500L, answerListen = true, answerSilenceSec = 2))
    }

    @Test
    fun `non-answer listen ignores the four-second floor`() {
        assertEquals(1500L, ListenWindow.silenceFor(configuredMs = 1500L, answerListen = false))
    }
}
