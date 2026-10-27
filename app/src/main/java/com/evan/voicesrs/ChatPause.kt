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

    enum class Trigger { FOCUS_INPUT, OPEN_THREAD, TYPING, DICTATION, MANUAL }

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

    /** Wait this long, then resume, when the pause ends without a question. */
    const val AUTO_RESUME_MS = 1_000L

    enum class Disengage {
        QUESTION_SENT,
        DICTATION_FAILED,
        DICTATION_CANCELLED,
        INPUT_CLEARED,
        UNFOCUSED
    }

    data class ResumePlan(
        val autoResume: Boolean,
        val delayMs: Long,
        /** The card stays paused until the timer fires, or until Resume or a rating. */
        val stayPausedUntilTimer: Boolean,
        val showResume: Boolean,
        val pulse: Boolean
    )

    /**
     * A sent question stays paused and pulses Resume.
     * Dictation that failed or was cancelled, a cleared field, or leaving the
     * composer without sending schedules [AUTO_RESUME_MS].
     */
    fun afterDisengage(end: Disengage): ResumePlan {
        if (end == Disengage.QUESTION_SENT) {
            return ResumePlan(
                autoResume = false,
                delayMs = 0L,
                stayPausedUntilTimer = true,
                showResume = true,
                pulse = true
            )
        }
        return ResumePlan(
            autoResume = true,
            delayMs = AUTO_RESUME_MS,
            stayPausedUntilTimer = true,
            showResume = false,
            pulse = false
        )
    }

    /** Focusing, typing, or dictating again drops a pending auto-resume. */
    fun reengageCancels(timerArmed: Boolean): Boolean = timerArmed

    /**
     * Clearing the composer never auto-resumes. A typing pause holds the card
     * until the Resume button or a voice "resume"; [showResume] keeps that
     * button visible while it waits.
     */
    fun afterTextCleared(): ResumePlan = ResumePlan(
        autoResume = false,
        delayMs = 0L,
        stayPausedUntilTimer = true,
        showResume = true,
        pulse = false
    )

    /**
     * The pause row shows Resume as soon as the drill freezes.
     * The accent pulse waits until a question was actually sent.
     */
    fun onEnter(): ResumePlan = ResumePlan(
        autoResume = false,
        delayMs = 0L,
        stayPausedUntilTimer = true,
        showResume = true,
        pulse = false
    )

    /** Focus, typing, dictation, and opening a thread all drop a pending auto-resume. */
    fun triggerReengages(trigger: Trigger): Boolean = when (trigger) {
        Trigger.FOCUS_INPUT, Trigger.OPEN_THREAD, Trigger.TYPING, Trigger.DICTATION -> true
        Trigger.MANUAL -> false
    }
}
