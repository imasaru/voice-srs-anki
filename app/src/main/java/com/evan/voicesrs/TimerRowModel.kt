package com.evan.voicesrs

/**
 * Pure logic for the compact session-timer row on the main screen.
 *
 * Operates on [AnkiConfig] only, so it is unit-tested without Android and stays
 * in lockstep with [SettingsActivity]. The row mirrors the goal-mode / goal-minutes
 * semantics there: [enabled]/[setEnabled] toggle the time/minutes goal, [step] nudges
 * the amount by [STEP_MINUTES] clamped to the same [MIN_MINUTES]..[MAX_MINUTES] bounds
 * [Prefs.save] uses for the minutes field, and [label] renders the stored amount.
 * Toggling never rewrites the stored minutes, so a cards-mode amount survives.
 */
object TimerRowModel {
    /** Step size for the − / + buttons, in minutes. */
    const val STEP_MINUTES = 5

    /** Same clamp as [Prefs.save] for the goal-minutes field. */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180

    /** True when the goal is the time/minutes mode (the row's mode). */
    fun enabled(cfg: AnkiConfig): Boolean = cfg.goalMode == GoalModes.MINUTES

    /** The stored time-goal amount, in minutes. */
    fun minutes(cfg: AnkiConfig): Int = cfg.goalMinutes

    /**
     * Turn the time goal on or off. On selects the minutes goal; off selects the
     * "no goal" mode. Either way the stored minutes are preserved, matching
     * SettingsActivity, which leaves goalMinutes untouched when toggling the goal.
     */
    fun setEnabled(cfg: AnkiConfig, on: Boolean): AnkiConfig =
        cfg.copy(goalMode = if (on) GoalModes.MINUTES else GoalModes.OFF)

    /** Change the time-goal amount by [delta] minutes, clamped to [MIN_MINUTES]..[MAX_MINUTES]. */
    fun step(cfg: AnkiConfig, delta: Int): AnkiConfig =
        cfg.copy(goalMinutes = (cfg.goalMinutes + delta).coerceIn(MIN_MINUTES, MAX_MINUTES))

    /** Row value text, e.g. "10 min". */
    fun label(cfg: AnkiConfig): String = "${cfg.goalMinutes} min"
}
