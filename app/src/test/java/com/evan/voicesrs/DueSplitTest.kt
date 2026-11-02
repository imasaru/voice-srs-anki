package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class DueSplitTest {
    @Test fun `a capped by perDay minus introduced today`() =
        assertEquals(DueCounts(27, 15, true), DueSplit.counts(27, 134, 20, 5))
    @Test fun `b never below zero when limit already used`() =
        assertEquals(DueCounts(27, 0, true), DueSplit.counts(27, 134, 20, 25))
    @Test fun `c unseen smaller than limit`() =
        assertEquals(DueCounts(3, 4, true), DueSplit.counts(3, 4, 20, 0))
    @Test fun `d uncapped when limit unreadable`() {
        assertEquals(DueCounts(27, 134, false), DueSplit.counts(27, 134, null, 5))
        assertEquals(DueCounts(27, 134, false), DueSplit.counts(27, 134, 20, null))
    }
    @Test fun `e negatives clamp`() = assertEquals(DueCounts(0, 0, false), DueSplit.counts(-1, -3, null, null))
    @Test fun `f label and session total`() {
        assertEquals("27 due · 134 new", DueSplit.label(DueCounts(27, 134, false)))
        assertEquals(42, DueSplit.sessionTotal(DueCounts(27, 15, true)))
    }
}
