package com.evan.voicesrs

/** h102: dead-server TTS fallback, on-device highlight parts, show-answer-during-read. Pure, no Android. */
object TtsFallback {
    const val SHOWING_ANSWER = "Showing answer"

    enum class Spoke { DONE, STOPPED, FAILED }
    enum class AfterQuestion { ANNOUNCE_THEN_ANSWER, SKIP_LISTEN, THINK_LISTEN, LISTEN }

    /** neuralBlocked after a neural attempt: a real failure (not a user stop) blocks until /health passes. */
    fun blockedAfter(blocked: Boolean, ok: Boolean, stopped: Boolean): Boolean = blocked || (!ok && !stopped)

    fun useNeural(preferredNeural: Boolean, blocked: Boolean): Boolean = preferredNeural && !blocked

    /** One card: neural when allowed, else on-device. Returns the new blocked flag. */
    fun route(preferredNeural: Boolean, blocked: Boolean, neural: () -> Boolean, onDevice: () -> Spoke, stopped: () -> Boolean = { false }): Boolean {
        if (useNeural(preferredNeural, blocked)) {
            val ok = neural()
            if (ok) return blocked
            val b = blockedAfter(blocked, ok, stopped())
            if (!stopped()) onDevice()
            return b
        }
        onDevice()
        return blocked
    }

    /** On-device pieces: exactly spoken.substring(SentenceSplit.ranges(spoken)[i]) (same as speakNeuralCard). None -> whole trimmed; blank -> empty. */
    fun onDeviceParts(spoken: String): List<String> {
        if (spoken.isBlank()) return emptyList()
        return SentenceSplit.ranges(spoken).map { spoken.substring(it) }.ifEmpty { listOf(spoken.trim()) }
    }

    fun spokeResult(accepted: Boolean, stoppedFlag: Boolean): Spoke = when {
        !accepted -> Spoke.FAILED
        stoppedFlag -> Spoke.STOPPED
        else -> Spoke.DONE
    }

    /** After speak(question). cut = the read was stopped by the tap (speechInterrupted). */
    fun afterQuestion(showEarly: Boolean, cut: Boolean, silent: Boolean, thinkArmed: Boolean): AfterQuestion = when {
        showEarly && cut && !silent -> AfterQuestion.ANNOUNCE_THEN_ANSWER
        showEarly || silent -> AfterQuestion.SKIP_LISTEN
        thinkArmed -> AfterQuestion.THINK_LISTEN
        else -> AfterQuestion.LISTEN
    }

    fun listens(a: AfterQuestion): Boolean = a == AfterQuestion.THINK_LISTEN || a == AfterQuestion.LISTEN

    fun announceFirst(a: AfterQuestion, answer: String): List<String> =
        if (a == AfterQuestion.ANNOUNCE_THEN_ANSWER) listOf(SHOWING_ANSWER, answer) else listOf(answer)
}
