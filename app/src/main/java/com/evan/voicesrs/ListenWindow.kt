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

    const val MIN_ATTEMPTS = 1
    const val MAX_ATTEMPTS = 5
    const val DEFAULT_ATTEMPTS = 3

    const val ANSWER_RELISTEN_CUE = "Take your time."
    const val SILENT_NOTICE = "Didn't hear a grade, switched to silent"
    const val ATTEMPTS_LABEL = "Listen attempts"
    const val ATTEMPTS_HELP = "How many times to listen before switching to silent."

    fun normalizeWaitSec(sec: Int): Int = if (sec in WAIT_SEC_CHOICES) sec else DEFAULT_WAIT_SEC

    fun normalizeSilenceMs(ms: Int): Int = if (ms in SILENCE_MS_CHOICES) ms else DEFAULT_SILENCE_MS

    fun waitMs(sec: Int): Long = normalizeWaitSec(sec) * 1_000L

    fun clampAttempts(n: Int): Int = n.coerceIn(MIN_ATTEMPTS, MAX_ATTEMPTS)

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
