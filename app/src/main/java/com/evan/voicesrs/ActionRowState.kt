package com.evan.voicesrs

/**
 * Pure decision logic for the single action row above the chat input (item 54A).
 * No UI here — layouts and binding are item 54B.
 */

/** High-level drill lifecycle the row reacts to. */
enum class DrillPhase { IDLE, DRILLING, PAUSED }

/** The single leading pill in the row. */
enum class ActionPill { START_DRILL, RESUME, NONE }

/**
 * Everything the action row renders. All fields are computed by
 * [decide] from the phase + a few environment flags, so the row is a pure
 * function of this state.
 */
data class ActionRowState(
    val rowVisible: Boolean,
    val pill: ActionPill,
    val showPauseButton: Boolean,   // ⏸ in the ⏸|⏹ segment
    val showStopButton: Boolean,    // ⏹
    val progressText: String?,      // "Q6 of 44" or null
    val showReportChip: Boolean,    // "Today's report" chip
    val inputHint: String,
) {
    companion object {
        const val HINT_IDLE = "Ask the AI…"
        const val HINT_DRILLING = "Answer or ask…"

        /**
         * Build the action-row state. The row hides while the IME is up
         * ([imeVisible]) but every other field is computed the same way.
         */
        fun decide(
            phase: DrillPhase,
            inputEmpty: Boolean,
            imeVisible: Boolean,
            q: Int,
            total: Int,
        ): ActionRowState {
            val rowVisible = !imeVisible
            val reportChip = phase == DrillPhase.IDLE && inputEmpty

            val pill: ActionPill
            val showPause: Boolean
            val showStop: Boolean
            val progressText: String?
            when (phase) {
                DrillPhase.IDLE -> {
                    pill = ActionPill.START_DRILL
                    showPause = false
                    showStop = false
                    progressText = null
                }
                DrillPhase.DRILLING -> {
                    pill = ActionPill.NONE
                    showPause = true
                    showStop = true
                    progressText = progress(q, total)
                }
                DrillPhase.PAUSED -> {
                    pill = ActionPill.RESUME
                    showPause = false
                    showStop = true
                    progressText = progress(q, total)
                }
            }

            val hint = when (phase) {
                DrillPhase.IDLE -> HINT_IDLE
                DrillPhase.DRILLING, DrillPhase.PAUSED -> HINT_DRILLING
            }

            return ActionRowState(
                rowVisible = rowVisible,
                pill = pill,
                showPauseButton = showPause,
                showStopButton = showStop,
                progressText = progressText,
                showReportChip = reportChip,
                inputHint = hint,
            )
        }

        /** "Q$q of $total" when total > 0 and q is in 1..total; otherwise null. */
        fun progress(q: Int, total: Int): String? =
            if (total > 0 && q in 1..total) "Q$q of $total" else null
    }
}
