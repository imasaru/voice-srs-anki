package com.evan.voicesrs

/**
 * Drill-card settings: batch clamp, field enablement, goal suffix, and the
 * batch-runs-out hint. Pure Kotlin so the rules can be unit-tested without Android.
 *
 * An offline batch is due and new cards, stored in [MIN_BATCH]..[MAX_BATCH].
 * A cards goal past that batch ends when the fetched batch runs out.
 */
object DrillSettings {
    const val MIN_BATCH = 1
    const val MAX_BATCH = 100
    const val ENABLED_ALPHA = 1f
    /** Platform disabled alpha. Applied to the cards-to-fetch field and its label together. */
    const val DISABLED_ALPHA = 0.38f
    const val CLAMPED_NOTE = "Clamped to 100"
    const val SUFFIX_CARDS = "cards"
    const val SUFFIX_MINUTES = "minutes"
    const val GOAL_BATCH_HINT = "Offline sessions end when the batch runs out."

    data class BatchClamp(
        /** Value to store. Always inside [MIN_BATCH]..[MAX_BATCH]. */
        val value: Int,
        /** Canonical field text when [rewriteField] is true. */
        val text: String,
        /** True when the typed number was outside the range and the field should show [text]. */
        val rewriteField: Boolean,
        /** True when the typed number was above [MAX_BATCH]. */
        val cappedAtMax: Boolean
    )

    fun fieldAlpha(enabled: Boolean): Float = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA

    /**
     * Blank or non-numeric input keeps [fallback] (itself clamped) and does not
     * rewrite the field. A typed number outside 1..100 rewrites the field.
     * [cappedAtMax] is only the upper end, which is the silent clamp callers surface.
     */
    fun clampBatch(raw: String, fallback: Int): BatchClamp {
        val safeFallback = fallback.coerceIn(MIN_BATCH, MAX_BATCH)
        val parsed = raw.trim().toIntOrNull()
            ?: return BatchClamp(safeFallback, safeFallback.toString(), rewriteField = false, cappedAtMax = false)
        val value = parsed.coerceIn(MIN_BATCH, MAX_BATCH)
        return BatchClamp(
            value = value,
            text = value.toString(),
            rewriteField = parsed != value,
            cappedAtMax = parsed > MAX_BATCH
        )
    }

    fun showGoalAmount(mode: String?): Boolean = GoalModes.normalize(mode) != GoalModes.OFF

    /** Trailing unit for the goal amount. Empty when the goal is off. */
    fun goalSuffix(mode: String?): String = when (GoalModes.normalize(mode)) {
        GoalModes.CARDS -> SUFFIX_CARDS
        GoalModes.MINUTES -> SUFFIX_MINUTES
        else -> ""
    }

    /**
     * Cards goal, offline batch on, and the goal is larger than the batch that
     * will actually be fetched (typed batch, clamped, or [batchFallback]).
     */
    fun showGoalBatchHint(
        mode: String?,
        offlineOn: Boolean,
        goalRaw: String,
        batchRaw: String,
        batchFallback: Int
    ): Boolean {
        if (!offlineOn) return false
        if (GoalModes.normalize(mode) != GoalModes.CARDS) return false
        val goal = goalRaw.trim().toIntOrNull() ?: return false
        val batch = clampBatch(batchRaw, batchFallback).value
        return goal > batch
    }
}
