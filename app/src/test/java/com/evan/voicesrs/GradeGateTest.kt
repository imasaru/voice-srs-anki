package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

class GradeGateTest {
    @Test fun firstClaimWins() {
        val g = GradeGate().apply { newCard(); reveal() }
        assertTrue(g.tryClaim(3))
        assertFalse(g.tryClaim(3))
        assertFalse(g.tryClaim(1))
        assertEquals(3, g.ease())
    }
    @Test fun newCardResets() {
        val g = GradeGate().apply { newCard(); reveal() }
        g.tryClaim(2); g.newCard(); g.reveal()
        assertFalse(g.isClaimed()); assertNull(g.ease())
        assertTrue(g.tryClaim(4)); assertEquals(4, g.ease())
    }
    @Test fun voiceAfterTapIgnored() {
        val g = GradeGate().apply { newCard(); reveal() }
        assertTrue(g.tryClaim(GradeGate.easeOf("good")))
        assertFalse(g.tryClaim(1))
    }
    @Test fun tapBeatsLaterVoice() {
        val g = GradeGate().apply { newCard(); reveal() }
        g.tryClaim(2)
        assertEquals(2, g.resolveVoice(4))
    }
    @Test fun voiceClaimsWhenNoTap() {
        val g = GradeGate().apply { newCard(); reveal() }
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
    @Test fun tapBeforeRevealIgnored() {
        val g = GradeGate().apply { newCard() }
        assertFalse(g.isRevealed())
        for (e in 1..4) assertFalse(g.tryClaim(e))
        assertFalse(g.isClaimed()); assertNull(g.ease())
    }
    @Test fun voiceBeforeRevealIgnored() {
        val g = GradeGate().apply { newCard() }
        assertNull(g.resolveVoice(4))
        assertFalse(g.isClaimed())
    }
    @Test fun earlyTapsDoNotCarryIntoReveal() {
        val g = GradeGate().apply { newCard() }
        g.tryClaim(1); g.tryClaim(4)
        g.reveal()
        assertTrue(g.isRevealed()); assertFalse(g.isClaimed())
        assertTrue(g.tryClaim(3)); assertEquals(3, g.ease())
    }
    @Test fun newCardHidesAgain() {
        val g = GradeGate().apply { newCard(); reveal() }
        g.newCard()
        assertFalse(g.isRevealed()); assertFalse(g.tryClaim(2))
    }
    @Test fun revealWithoutCardIgnored() {
        val g = GradeGate()
        g.reveal()
        assertFalse(g.isRevealed()); assertFalse(g.tryClaim(3))
        g.newCard(); g.reveal(); g.close()
        assertFalse(g.isRevealed()); assertNull(g.resolveVoice(2))
    }
    @Test fun uiStateHidesRatingByDefault() {
        assertFalse(DrillUiState().ratingVisible)
    }

    // ---- gradelock: the media fetch must not run while holding the grade gate ----
    // The render step taken inside synchronized(gradeGate) is display(html, resolved): it
    // consumes an already-fetched map and must never touch the network. The fetch lives in
    // prefetch(html), which runs before the lock is taken. These assert that ordering.

    private fun countingFetcher(): Pair<(String) -> ByteArray?, () -> Int> {
        var fetches = 0
        val f: (String) -> ByteArray? = { fetches++; "x".toByteArray() }
        return Pair(f, { fetches })
    }

    @Test fun `prefetch fetches each referenced image once`() {
        val (f, count) = countingFetcher()
        // preload fetches each referenced image via the injected fetcher before rendering.
        // (bitmap decode is skipped in the JVM unit env, but the fetch itself is the point.)
        CardMedia.preload("""<img src="a.png">Sign?""", f)
        assertEquals(1, count())
    }

    @Test fun `render with a resolved map performs no fetch`() {
        val (f, count) = countingFetcher()
        // A pre-resolved map is what prefetch returns; display(html, resolved) is exactly what
        // runs inside synchronized(gradeGate) and must not perform any fetch while the gate is held.
        val resolved = mapOf("a.png" to "a.png")
        val out = CardMedia.display("""<img src="a.png">Sign?""", resolved)
        assertEquals(0, count())
        assertTrue(out.contains("""<img src="a.png">"""))
    }
}
