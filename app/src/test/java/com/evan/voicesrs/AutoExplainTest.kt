package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoExplainTest {

    @Test
    fun `again and hard auto explain by default and the others get a button`() {
        val selected = AutoExplain.DEFAULT
        assertEquals(setOf(1, 2), selected)
        assertTrue(AutoExplain.shouldAuto(selected, 1))
        assertTrue(AutoExplain.shouldAuto(selected, 2))
        assertFalse(AutoExplain.shouldAuto(selected, 3))
        assertFalse(AutoExplain.shouldAuto(selected, 4))
        assertEquals(
            listOf("Again", "Hard", "Good", "Easy"),
            AutoExplain.CHIPS
        )
        assertEquals("Other ratings get an Explain button instead.", AutoExplain.NOTE)
    }

    @Test
    fun `a chosen set is the only set that auto generates`() {
        val goodOnly = setOf(3)
        assertFalse(AutoExplain.shouldAuto(goodOnly, 1))
        assertTrue(AutoExplain.shouldAuto(goodOnly, 3))
        assertFalse(AutoExplain.shouldAuto(emptySet(), 1))
        assertFalse(AutoExplain.shouldAuto(setOf(1, 2, 3, 4), 0))
    }

    @Test
    fun `explain button hides while one exists or is generating`() {
        assertTrue(AutoExplain.showButton(ExplState.None))
        assertTrue(AutoExplain.showButton(ExplState.Error("nope", retryable = true)))
        assertFalse(AutoExplain.showButton(ExplState.Queued))
        assertFalse(AutoExplain.showButton(ExplState.Pending))
        assertFalse(AutoExplain.showButton(ExplState.Streaming("partial")))
        assertFalse(AutoExplain.showButton(ExplState.Ready("done", cached = false)))
        assertFalse(AutoExplain.showButton(ExplState.Ready("cached", cached = true)))
    }

    @Test
    fun `migration prefers the new set then a legacy switch then again and hard`() {
        assertEquals(setOf(3, 4), AutoExplain.migrate(stored = "3,4", legacy = true))
        assertEquals(setOf(1, 2, 3, 4), AutoExplain.migrate(stored = null, legacy = true))
        assertEquals(emptySet<Int>(), AutoExplain.migrate(stored = null, legacy = false))
        assertEquals(setOf(1, 2), AutoExplain.migrate(stored = null, legacy = null))
        assertEquals(setOf(1), AutoExplain.migrate(stored = "1,9,0", legacy = null))
        assertEquals("1,2", AutoExplain.encode(setOf(2, 1, 2)))
    }
}