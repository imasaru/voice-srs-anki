package com.evan.voicesrs

/**
 * Pure state machine for the drill's response to AudioManager audio-focus
 * changes. Kept free of android.* so it can be unit-tested without Robolectric.
 * The focus constants mirror [AudioManager.AUDIOFOCUS_*] but are literal so the
 * object stays pure.
 */
object FocusPolicy {
    // Same values as AudioManager.AUDIOFOCUS_* (kept literal so the object stays pure)
    const val GAIN = 1
    const val LOSS = -1
    const val LOSS_TRANSIENT = -2
    const val LOSS_TRANSIENT_CAN_DUCK = -3
    const val RESUME_DELAY_MS = 1000L

    enum class Phase { IDLE, SPEAKING, LISTENING }
    enum class Action { NONE, PAUSE_SPEAK, ABORT_LISTEN, RESUME }

    /** State the caller keeps between callbacks. */
    class State { var transientLoss = false; var lostForGood = false; var lostAt = 0L }

    fun onChange(change: Int, phase: Phase, state: State, now: Long): Action {
        when (change) {
            LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK -> {
                // h97 hotfix: the speech service (com.google.android.tts TNG_TRANSCRIPTION) takes
                // transient focus itself right after startListening. Treating that as a loss
                // aborted every listen and looped. Ignore transient losses while listening.
                if (phase == Phase.LISTENING) return Action.NONE
                state.transientLoss = true
                state.lostForGood = false
                state.lostAt = now
                return when (phase) {
                    Phase.SPEAKING -> Action.PAUSE_SPEAK
                    Phase.LISTENING -> Action.ABORT_LISTEN
                    Phase.IDLE -> Action.NONE
                }
            }
            LOSS -> {
                state.lostForGood = true
                state.transientLoss = false
                state.lostAt = now
                return when (phase) {
                    Phase.SPEAKING -> Action.PAUSE_SPEAK
                    Phase.LISTENING -> Action.ABORT_LISTEN
                    Phase.IDLE -> Action.NONE
                }
            }
            GAIN -> {
                if (state.transientLoss && !state.lostForGood) {
                    state.transientLoss = false
                    state.lostAt = 0
                    return Action.RESUME
                }
                return Action.NONE
            }
            else -> return Action.NONE
        }
    }

    /**
     * True when a listen result may be graded: no focus loss, or the loss landed
     * before this listen started. A loss during the answer window means the other
     * audio contaminated the mic, so the result must be dropped, not graded.
     */
    fun shouldGrade(focusLostAt: Long, resultAt: Long): Boolean = focusLostAt == 0L || resultAt < focusLostAt
}
