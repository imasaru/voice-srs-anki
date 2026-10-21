package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CardIdentityTest {
    private val card = AnkiCard("<p>Question 1</p>", "<p>Answer B</p>", "Mock Exam 1", listOf(1, 2, 3, 4), 1001)

    @Test
    fun `reveal and grade proceed only for the same card`() {
        assertEquals(CardIdentity.Verdict.PROCEED, CardIdentity.onReveal(1001, 1001))
        assertEquals(CardIdentity.Verdict.RESYNC, CardIdentity.onReveal(1001, 1002))
        assertEquals(CardIdentity.Verdict.RESYNC, CardIdentity.onReveal(1001, null))
        assertEquals(CardIdentity.Verdict.PROCEED, CardIdentity.beforeGrade(1001, 1001))
        assertEquals(CardIdentity.Verdict.RESYNC, CardIdentity.beforeGrade(1001, 1002))
        assertEquals(CardIdentity.Verdict.RESYNC, CardIdentity.beforeGrade(1001, null))
    }

    @Test
    fun `the shown question and answer come from one card object`() {
        assertSame(card, CardIdentity.sameCard(1001, card))
        assertNull(CardIdentity.sameCard(1002, card))
        assertNull(CardIdentity.sameCard(1001, null))
    }

    @Test
    fun `three consecutive resyncs stop`() {
        assertTrue(!CardIdentity.shouldStop(1))
        assertTrue(!CardIdentity.shouldStop(2))
        assertTrue(CardIdentity.shouldStop(3))
        assertTrue(CardIdentity.shouldStop(4))
        assertEquals(3, CardIdentity.MAX_CONSECUTIVE_RESYNCS)
    }

    @Test
    fun `mismatch log names both ids`() {
        assertEquals("card changed under us expected=1001 got=1002", CardIdentity.mismatchLog(1001, 1002))
        assertEquals("card changed under us expected=1001 got=0", CardIdentity.mismatchLog(1001, null))
    }
}
