package com.evan.voicesrs

/**
 * Pure decisions for one listen window. No Android types: the service passes
 * [ERROR_NO_MATCH] and [ERROR_SPEECH_TIMEOUT], which match SpeechRecognizer.
 *
 * Google's recognizer often ends a silent session after about five seconds.
 * [shouldRestart] keeps the same listen alive until its deadline when speech
 * has not started. A spoken answer that comes back empty can be tried once
 * more; [healthSample] counts that pair as one dead-mic sample.
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

    const val ANSWER_RELISTEN_CUE = "Take your time."

    fun normalizeWaitSec(sec: Int): Int = if (sec in WAIT_SEC_CHOICES) sec else DEFAULT_WAIT_SEC

    fun normalizeSilenceMs(ms: Int): Int = if (ms in SILENCE_MS_CHOICES) ms else DEFAULT_SILENCE_MS

    fun waitMs(sec: Int): Long = normalizeWaitSec(sec) * 1_000L

    /** Leading integer of a settings label. Unknown text falls back to the default. */
    fun waitSecFromLabel(label: String): Int {
        val n = leadingInt(label) ?: return DEFAULT_WAIT_SEC
        return normalizeWaitSec(n)
    }

    fun silenceMsFromLabel(label: String): Int {
        val n = leadingInt(label) ?: return DEFAULT_SILENCE_MS
        return normalizeSilenceMs(n)
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

    /** Answer phase only. Grade keeps its own single reprompt. */
    fun shouldRelistenAnswer(
        result: String?,
        relistenOnce: Boolean,
        stopped: Boolean,
        speedMode: Boolean
    ): Boolean {
        return result == null && relistenOnce && !stopped && !speedMode
    }

    /**
     * The one transcript that counts toward the dead-mic streak.
     * When [didRelisten] is true, the provisional window is dropped.
     */
    fun healthSample(first: String?, retry: String?, didRelisten: Boolean): String? {
        return if (didRelisten) retry else first
    }

    private fun leadingInt(label: String): Int? {
        val digits = StringBuilder()
        for (ch in label.trim()) {
            if (ch.isDigit()) digits.append(ch) else if (digits.isNotEmpty()) break
        }
        return digits.toString().toIntOrNull()
    }
}
