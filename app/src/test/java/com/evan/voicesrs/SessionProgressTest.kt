package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProgressTest {
    @Test
    fun `streak resets on again and estimate uses the average`() {
        val progress = SessionProgress(dueAtStart = 10)
        progress.recordGrade(3, 10_000)
        progress.recordGrade(4, 20_000)
        progress.recordGrade(1, 30_000)
        assertEquals(3, progress.done)
        assertEquals(0, progress.streak)
        assertEquals(2, progress.goodOrEasy)
        assertEquals(7, progress.left())
        assertEquals(20, progress.avgSec())
        assertEquals(140, progress.etaSec())
        assertEquals("Done 3 · Left 7 · Streak 0 · ~2 min", progress.line())
    }

    @Test
    fun `no estimate before the first graded card`() {
        val progress = SessionProgress(4)
        assertEquals("Done 0 · Left 4 · Streak 0", progress.line())
        assertEquals(0, progress.etaSec())
    }

    @Test
    fun `spoken summary matches the goal sentence`() {
        val progress = SessionProgress(20)
        repeat(17) { progress.recordGrade(3, 1_000) }
        repeat(3) { progress.recordGrade(1, 1_000) }
        assertEquals(
            "Done: 20 cards, 17 good or easy, 12 minutes.",
            progress.spokenSummary(12 * 60_000L)
        )
    }

    @Test
    fun `goals`() {
        val progress = SessionProgress(100)
        assertFalse(progress.goalReached(GoalModes.OFF, 1, 1, 999_999))
        progress.recordGrade(3, 1_000)
        assertTrue(progress.goalReached(GoalModes.CARDS, 1, 10, 0))
        assertFalse(progress.goalReached(GoalModes.MINUTES, 1, 10, 9 * 60_000L))
        assertTrue(progress.goalReached(GoalModes.MINUTES, 1, 10, 10 * 60_000L))
    }

    @Test
    fun `goal mode aliases`() {
        assertEquals(GoalModes.OFF, GoalModes.normalize(null))
        assertEquals(GoalModes.CARDS, GoalModes.normalize("Cards"))
        assertEquals(GoalModes.MINUTES, GoalModes.normalize("minutes"))
    }

    @Test
    fun `left is remaining in the session not remaining deck due`() {
        val progress = SessionProgress(dueAtStart = 12)
        progress.recordGrade(3, 0)
        assertEquals(11, progress.left())
        assertEquals("Done 1 · Left 11 · Streak 1", progress.line())
    }

    @Test
    fun `progress line is blank when the drill is not running`() {
        val line = "Done 1 · Left 11 · Streak 1"
        assertEquals("", SessionProgress.visibleLine(false, line))
        assertEquals("", SessionProgress.visibleLine(false, ""))
        assertEquals(line, SessionProgress.visibleLine(true, line))
        assertEquals("", SessionProgress.visibleLine(true, ""))
    }
}
