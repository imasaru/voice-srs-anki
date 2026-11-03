package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure timer-row logic so the compact main-screen row and
 * SettingsActivity stay in lockstep. The assertions pin the exact goal-mode
 * constants and AnkiConfig fields that SettingsActivity writes, so a drift
 * between the two shows up here.
 */
class TimerRowModelTest {

    /** Base config: goal off, a 10-minute time goal already stored. */
    private val offAtTen = AnkiConfig(url = "http://anki.example.invalid", apiKey = "k", deck = "d")
        .copy(goalMode = GoalModes.OFF, goalMinutes = 10, goalCards = 20)

    @Test
    fun `enable selects the minutes goal mode`() {
        val on = TimerRowModel.setEnabled(offAtTen, on = true)
        // Same constant SettingsActivity's MINUTES branch writes.
        assertEquals(GoalModes.MINUTES, on.goalMode)
    }

    @Test
    fun `disable selects the off goal mode`() {
        val off = TimerRowModel.setEnabled(offAtTen, on = false)
        // Same constant SettingsActivity's OFF branch writes.
        assertEquals(GoalModes.OFF, off.goalMode)
    }

    @Test
    fun `enable and disable change only the goal mode, not the stored minutes`() {
        val on = TimerRowModel.setEnabled(offAtTen, on = true)
        val off = TimerRowModel.setEnabled(offAtTen, on = false)
        // Settings MINUTES commit keeps goalMinutes when the user has not retyped it,
        // and the OFF commit never touches goalMinutes, so the amount survives.
        assertEquals(10, on.goalMinutes)
        assertEquals(10, off.goalMinutes)
        // The amount field is untouched by the toggle; only goalMode changed.
        assertEquals(offAtTen.goalMinutes, on.goalMinutes)
        assertEquals(offAtTen.goalMinutes, off.goalMinutes)
    }

    @Test
    fun `step up adds five minutes`() {
        assertEquals(15, TimerRowModel.step(offAtTen, TimerRowModel.STEP_MINUTES).goalMinutes)
    }

    @Test
    fun `step down removes five minutes`() {
        assertEquals(5, TimerRowModel.step(offAtTen, -TimerRowModel.STEP_MINUTES).goalMinutes)
    }

    @Test
    fun `step is five minutes`() {
        assertEquals(5, TimerRowModel.STEP_MINUTES)
    }

    @Test
    fun `step clamps at the minimum Settings uses`() {
        val tiny = offAtTen.copy(goalMinutes = 1)
        assertEquals(1, TimerRowModel.step(tiny, -5).goalMinutes)
        assertEquals(1, TimerRowModel.step(tiny, -1).goalMinutes)
    }

    @Test
    fun `step clamps at the maximum Settings uses`() {
        val big = offAtTen.copy(goalMinutes = 180)
        assertEquals(180, TimerRowModel.step(big, 5).goalMinutes)
    }

    @Test
    fun `step clamps a raw delta that crosses the bounds`() {
        assertEquals(1, TimerRowModel.step(offAtTen.copy(goalMinutes = 3), -5).goalMinutes) // -2 -> 1
        assertEquals(180, TimerRowModel.step(offAtTen.copy(goalMinutes = 178), 5).goalMinutes) // 183 -> 180
    }

    @Test
    fun `step changes only the minutes field, leaving mode and cards alone`() {
        val stepped = TimerRowModel.step(offAtTen, 5)
        assertEquals(15, stepped.goalMinutes)
        assertEquals(GoalModes.OFF, stepped.goalMode)
        assertEquals(20, stepped.goalCards)
    }

    @Test
    fun `steps work while the goal is off and leave the mode unchanged`() {
        val stepped = TimerRowModel.step(offAtTen, 5)
        assertEquals(GoalModes.OFF, stepped.goalMode)
    }

    @Test
    fun `label reads the minutes value with a min suffix`() {
        assertEquals("10 min", TimerRowModel.label(offAtTen))
        assertEquals("15 min", TimerRowModel.label(TimerRowModel.step(offAtTen, 5)))
        assertEquals("1 min", TimerRowModel.label(offAtTen.copy(goalMinutes = 1)))
    }

    @Test
    fun `enabled reflects the minutes goal mode only`() {
        assertFalse(TimerRowModel.enabled(offAtTen))
        assertTrue(TimerRowModel.enabled(offAtTen.copy(goalMode = GoalModes.MINUTES)))
        // A cards goal is not the timer row's time/minutes mode.
        assertFalse(TimerRowModel.enabled(offAtTen.copy(goalMode = GoalModes.CARDS)))
    }

    @Test
    fun `bounds mirror the Prefs save clamp and the step size`() {
        // Prefs.save clamps goalMinutes with coerceIn(1, 180); the row uses the same limits.
        assertEquals(1, TimerRowModel.MIN_MINUTES)
        assertEquals(180, TimerRowModel.MAX_MINUTES)
        assertEquals(5, TimerRowModel.STEP_MINUTES)
    }

    @Test
    fun `progress maps elapsed against the goal onto 0 to 1000`() {
        assertEquals(0, TimerRowModel.progress(0L, 180))
        assertEquals(500, TimerRowModel.progress(90 * 60_000L, 180))
        assertEquals(1000, TimerRowModel.progress(200 * 60_000L, 180))
        assertEquals(0, TimerRowModel.progress(60_000L, 0))
        assertEquals(0, TimerRowModel.progress(-5L, 180))
    }
}
