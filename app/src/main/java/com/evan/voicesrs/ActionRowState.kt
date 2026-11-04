package com.evan.voicesrs

/**
 * Pure decision logic for the single action row above the chat input (item 54A).
 * No UI here — layouts and binding are item 54B.
 *
 * Review fix (the maintainer, 2026-10-27): the Start drill and Stop drill buttons keep their
 * text, size and position in every state — they are never hidden or swapped for
 * icons. Only the Pause control next to the chat box changes: a pause icon while drilling,
 * a play icon while paused, at the same size. [ActionPill.RESUME] is kept for source
 * compatibility but [decide] never returns it; resuming is the play state of Pause.
 */

/** High-level drill lifecycle the row reacts to. */
enum class DrillPhase { IDLE, DRILLING, PAUSED }

/** The single leading pill in the row. */
enum class ActionPill { START_DRILL, RESUME, NONE }

data class ActionRowState(
    val rowVisible: Boolean,
    val pill: ActionPill,
    val showPauseButton: Boolean,   // Pause control next to the chat box (drilling or paused)
    val showStopButton: Boolean,    // "Stop drill" text button — always shown, fixed position
    val progressText: String?,      // "Q6 of 44" or null
    val showReportChip: Boolean,    // report chip
    val inputHint: String,
    val pauseShowsResume: Boolean = false, // true -> Pause control shows the play icon (paused), else the pause icon
) {
    companion object {
        const val HINT_IDLE = "Ask the AI…"
        const val HINT_DRILLING = "Answer or ask…"

        fun decide(
            phase: DrillPhase,
            inputEmpty: Boolean,
            imeVisible: Boolean,
            q: Int,
            total: Int,
        ): ActionRowState = ActionRowState(
            rowVisible = !imeVisible,
            pill = ActionPill.START_DRILL,              // "Start drill" stays put in every state
            showPauseButton = phase != DrillPhase.IDLE,
            showStopButton = true,                      // "Stop drill" stays put in every state
            progressText = if (phase == DrillPhase.IDLE) null else progress(q, total),
            showReportChip = phase == DrillPhase.IDLE && inputEmpty,
            inputHint = if (phase == DrillPhase.IDLE) HINT_IDLE else HINT_DRILLING,
            pauseShowsResume = phase == DrillPhase.PAUSED,
        )

        /** "Q$q of $total" when total > 0 and q is in 1..total; otherwise null. */
        fun progress(q: Int, total: Int): String? =
            if (total > 0 && q in 1..total) "Q$q of $total" else null
    }
}
