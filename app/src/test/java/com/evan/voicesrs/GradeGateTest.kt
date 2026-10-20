package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

class GradeGateTest {
    @Test fun firstClaimWins() {
        val g = GradeGate().apply { newCard() }
        assertTrue(g.tryClaim(3))
        assertFalse(g.tryClaim(3))
        assertFalse(g.tryClaim(1))
        assertEquals(3, g.ease())
    }
    @Test fun newCardResets() {
        val g = GradeGate().apply { newCard() }
        g.tryClaim(2); g.newCard()
        assertFalse(g.isClaimed()); assertNull(g.ease())
        assertTrue(g.tryClaim(4)); assertEquals(4, g.ease())
    }
    @Test fun voiceAfterTapIgnored() {
        val g = GradeGate().apply { newCard() }
        assertTrue(g.tryClaim(GradeGate.easeOf("good")))
        assertFalse(g.tryClaim(1))
    }
    @Test fun tapBeatsLaterVoice() {
        val g = GradeGate().apply { newCard() }
        g.tryClaim(2)
        assertEquals(2, g.resolveVoice(4))
    }
    @Test fun voiceClaimsWhenNoTap() {
        val g = GradeGate().apply { newCard() }
        assertEquals(4, g.resolveVoice(4))
        assertFalse(g.tryClaim(1))
    }
    @Test fun wordRoundTrip() {
        for (e in 1..4) assertEquals(e, GradeGate.easeOf(GradeGate.wordOf(e)))
    }
    @Test fun tapWithoutCardIgnored() {
        val g = GradeGate()
        assertFalse(g.tryClaim(3))
        g.newCard(); g.close()
        assertFalse(g.tryClaim(3))
    }
    @Test fun easeMapping() {
        assertEquals(listOf(1, 2, 3, 4), listOf("again", "hard", "good", "easy").map { GradeGate.easeOf(it) })
        assertNull(GradeGate.easeOf("nope"))
    }
}
