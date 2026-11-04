package com.evan.voicesrs

/**
 * Pure decisions for one listen window. No Android types: the service passes
 * [ERROR_NO_MATCH] and [ERROR_SPEECH_TIMEOUT], which match SpeechRecognizer.
 *
 * Google's recognizer often ends a silent session after about five seconds.
 * [shouldRestart] keeps the same listen alive until its deadline when speech
 * has not started. Misses across windows use [openAnother] up to the saved
 * attempt count. Silence and no-match are not microphone faults.
 */
object ListenWindow {
    /** Matches SpeechRecognizer.ERROR_SPEECH_TIMEOUT. */
    const val ERROR_SPEECH_TIMEOUT = 6
    /** Matches SpeechRecognizer.ERROR_NO_MATCH. */
    const val ERROR_NO_MATCH = 7

    const val MIN_REMAINING_MS = 1_500L
    const val MAX_RESTARTS = 10

    val WAIT_SEC_CHOICES = intArrayOf(5, 10, 15, 20, 30)
    val SILENCE_MS_CHOICES = intArrayOf(1000, 1500, 2000, 3000)
    const val DEFAULT_WAIT_SEC = 15
    const val DEFAULT_SILENCE_MS = 1500
    /** h105: after the deadline fires mid-listen, wait this long for onResults
     * before falling back to the last partial. Kept short so a dropped final
     * doesn't stall the card reveal. */
    const val STOP_GRACE_MS = 1000L
    /** h105: listens inside the answer window get at least this long of silence
     * before giving up (longer than the default, which suits the short
     * end-of-card listens). Rating and command listens keep the default. */
    const val ANSWER_SILENCE_MS = 4000L

    /** h141: the answer-listen silence the user sets (seconds). Kept as the old floor for
     *  callers/tests that don't take the slider. The slider now replaces this value. */
    const val MIN_ANSWER_SILENCE_SEC = 1
    const val MAX_ANSWER_SILENCE_SEC = 10
    const val DEFAULT_ANSWER_SILENCE_SEC = 4

    const val MIN_ATTEMPTS = 1
    const val MAX_ATTEMPTS = 5
    const val DEFAULT_ATTEMPTS = 3

    const val ANSWER_RELISTEN_CUE = "Take your time."

    /** p92a: on NO_MATCH after speech, the first non-blank partial (trimmed) is the answer; otherwise null. */
    fun noMatchPartial(error: Int, spoke: Boolean, partials: List<String>): String? {
        if (error != ERROR_NO_MATCH || !spoke) return null
        return partials.firstOrNull { it.isNotBlank() }?.trim()
    }

    /** First non-blank final; else first non-blank partial, trimmed; else "". */
    fun finalOrPartial(finals: List<String>, partials: List<String>): String {
        finals.firstOrNull { it.isNotBlank() }?.let { return it }
        return partials.firstOrNull { it.isNotBlank() }?.trim() ?: ""
    }

    /** h102: window cut off / recognizer destroyed with no final. Own-words deadline text wins; else the latest non-blank partial; else null. */
    fun cutoffText(ownWords: String?, sessionPartials: List<String>): String? =
        ownWords ?: finalOrPartial(emptyList(), sessionPartials).ifBlank { null }

    /** h105: the deadline fired mid-listen and the recognizer was destroyed before
     * onResults. The last non-blank partial is the most complete capture, so it
     * wins over earlier fragments; a non-blank final still wins over everything.
     * All blank returns null (nothing was heard). */
    fun deadlineText(finals: List<String>, partials: List<String>): String? {
        finals.firstOrNull { it.isNotBlank() }?.let { return it }
        return partials.lastOrNull { it.isNotBlank() }?.trim()?.ifBlank { null }
    }

    const val SILENT_NOTICE = "Didn't hear a grade, switched to silent"
    const val ATTEMPTS_LABEL = "Listen attempts"
    const val ATTEMPTS_HELP = "How many times to listen before switching to silent."

    fun normalizeWaitSec(sec: Int): Int = if (sec in WAIT_SEC_CHOICES) sec else DEFAULT_WAIT_SEC

    fun normalizeSilenceMs(ms: Int): Int = if (ms in SILENCE_MS_CHOICES) ms else DEFAULT_SILENCE_MS

    fun waitMs(sec: Int): Long = normalizeWaitSec(sec) * 1_000L

    fun clampAttempts(n: Int): Int = n.coerceIn(MIN_ATTEMPTS, MAX_ATTEMPTS)

    /** h141: answer listens use the user-configured silence [answerSilenceSec] (seconds),
     *  which now REPLACES the old floor so 1–3 s is honoured. Non-answer listens
     *  (rating/command) keep [configuredMs] unchanged. */
    fun silenceFor(
        configuredMs: Long,
        answerListen: Boolean,
        answerSilenceSec: Int = DEFAULT_ANSWER_SILENCE_SEC
    ): Long =
        if (answerListen) normalizeAnswerSilenceSec(answerSilenceSec) * 1000L else configuredMs

    /** h141: clamp the answer-listen silence from seconds to the 1–10 slider range. */
    fun normalizeAnswerSilenceSec(s: Int): Int =
        s.coerceIn(MIN_ANSWER_SILENCE_SEC, MAX_ANSWER_SILENCE_SEC)

    /**
     * The new int key wins. Otherwise the old "listen once more" switch maps
     * on to 2 and off to 1. Neither key means [DEFAULT_ATTEMPTS].
     */
    fun migrateAttempts(stored: Int?, legacyOn: Boolean?): Int {
        if (stored != null) return clampAttempts(stored)
        return when (legacyOn) {
            true -> 2
            false -> 1
            null -> DEFAULT_ATTEMPTS
        }
    }

    /** Leading integer of a settings label. Unknown text falls back to the default. */
    fun waitSecFromLabel(label: String): Int {
        val n = leadingInt(label) ?: return DEFAULT_WAIT_SEC
        return normalizeWaitSec(n)
    }

    fun silenceMsFromLabel(label: String): Int {
        val n = leadingInt(label) ?: return DEFAULT_SILENCE_MS
        return normalizeSilenceMs(n)
    }

    fun attemptsFromLabel(label: String): Int {
        val n = leadingInt(label) ?: return DEFAULT_ATTEMPTS
        return clampAttempts(n)
    }

    /**
     * True when this recognizer ended because nobody started speaking and the
     * listen deadline still has at least [MIN_REMAINING_MS] left.
     * [restarts] is how many silence restarts have already happened.
     * Stopped and destroyed are the caller's job.
     */
    fun shouldRestart(
        speechBegan: Boolean,
        error: Int,
        nowMs: Long,
        deadlineMs: Long,
        restarts: Int
    ): Boolean {
        if (speechBegan) return false
        if (restarts >= MAX_RESTARTS) return false
        if (error != ERROR_NO_MATCH && error != ERROR_SPEECH_TIMEOUT) return false
        return deadlineMs - nowMs >= MIN_REMAINING_MS
    }

    /**
     * After a miss, open another listen while attempts remain.
     * [completed] is how many windows have already finished.
     * Speed mode still returns true: it skips the spoken cue, not the retry.
     * Stop does not open another window and does not count as giving up.
     */
    fun openAnother(completed: Int, max: Int, lastMissed: Boolean, stopped: Boolean): Boolean {
        if (stopped || !lastMissed) return false
        return completed < clampAttempts(max)
    }

    /** True when this miss was the last allowed listen. The caller switches to silent and does not grade. */
    fun gaveUp(completed: Int, max: Int, lastMissed: Boolean): Boolean {
        return lastMissed && completed >= clampAttempts(max)
    }

    /**
     * A recognizer error that is not silence or no-match.
     * Zero is the app timeout path, which is not a microphone failure.
     * A fault may warn. It must not end the drill.
     */
    fun isMicFault(error: Int): Boolean {
        if (error == 0) return false
        if (error == ERROR_NO_MATCH || error == ERROR_SPEECH_TIMEOUT) return false
        return true
    }

    private fun leadingInt(label: String): Int? {
        val digits = StringBuilder()
        for (ch in label.trim()) {
            if (ch.isDigit()) digits.append(ch) else if (digits.isNotEmpty()) break
        }
        return digits.toString().toIntOrNull()
    }
}

/**
 * h105: pure gate for the listen cue tones. Kept out of the service so the
 * "should this tone play?" decision is unit-tested and never depends on the
 * Android AudioTrack.
 */
object ListenCue {
    /** h105: tone length; short so it never clips the start of speech. */
    const val TONE_MS = 250L

    /**
     * Play the start tone? Play it on the first listen of a card, and on a
     * restart only when it is not a quiet restart. A quiet restart must stay
     * silent so it does not cover the user's speech.
     */
    fun playStartTone(firstListen: Boolean, restartQuiet: Boolean): Boolean =
        firstListen && !restartQuiet

    /** The end tone always plays; it signals the listen is over and must not be
     * suppressed by a quietListen flag left over from a restart. */
    fun playEndTone(): Boolean = true
}
