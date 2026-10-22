package com.evan.voicesrs

/**
 * Auto-pause while the maintainer asks the AI.
 * Focusing the chat input, opening a thread, or typing or dictating a question
 * freezes the drill on the current card. Resume is only the Resume button or a
 * rating tap. A rating tap also grades. Dismissing the thread does not resume.
 */
object ChatPause {
    const val LABEL = "Paused, asking AI"
    const val RESUME = "Resume"

    enum class Trigger { FOCUS_INPUT, OPEN_THREAD, TYPING, DICTATION }

    enum class Action { RESUME, RATING, SHOW_ANSWER, DISMISS, OTHER }

    fun pauses(drillRunning: Boolean, trigger: Trigger): Boolean = drillRunning

    /** The next card must not come up while this is false. */
    fun advances(paused: Boolean): Boolean = !paused

    fun accepts(action: Action): Boolean = action == Action.RESUME || action == Action.RATING

    /**
     * A reply is spoken only while paused, when Silent is off, and the question
     * was dictated. Typed questions stay text. Unpaused review never speaks them.
     */
    fun speakReply(paused: Boolean, silent: Boolean, askedByVoice: Boolean): Boolean {
        return paused && !silent && askedByVoice
    }

    /** Remaining think time at the moment of the pause. Wall clock does not run it down. */
    fun freeze(nowMs: Long, deadlineMs: Long): Long = (deadlineMs - nowMs).coerceAtLeast(0L)

    fun resumeDeadline(nowMs: Long, frozenMs: Long): Long = nowMs + frozenMs.coerceAtLeast(0L)
}
