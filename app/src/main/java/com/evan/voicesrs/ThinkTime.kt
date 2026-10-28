package com.evan.voicesrs

/**
 * App-side think timer. It is not a SpeechRecognizer timeout and it is not
 * the "Wait for speech" window, which stays the per-listen mic window for grades.
 * Silence and no-match restart the mic without an error and without a listen attempt.
 * Silent mode does not arm the timer, so the answer is not revealed on its own.
 */
object ThinkTime {
    const val OFF = 0
    val CHOICES_SEC = intArrayOf(OFF, 5, 10, 15, 20, 30, 45, 60)
    const val DEFAULT_SEC = 20
    const val LABEL = "Think time"
    const val HELP = "How long to give you to think before the answer is read."
    const val OFF_LABEL = "Off"

    /** One recognizer slice while thinking. Independent of the grade wait. */
    const val MIC_SLICE_MS = 8_000L

    /** Think-time mic restarts are capped here so a stuck mic stops retrying. */
    const val MAX_RESTARTS = 10

    fun normalize(sec: Int): Int = if (sec in CHOICES_SEC) sec else DEFAULT_SEC

    fun label(sec: Int): String {
        val n = normalize(sec)
        return if (n == OFF) OFF_LABEL else "$n s"
    }

    fun fromLabel(label: String): Int {
        val t = label.trim()
        if (t.equals(OFF_LABEL, ignoreCase = true)) return OFF
        val digits = StringBuilder()
        for (ch in t) {
            if (ch.isDigit()) digits.append(ch) else if (digits.isNotEmpty()) break
        }
        val n = digits.toString().toIntOrNull() ?: return DEFAULT_SEC
        return normalize(n)
    }

    /** Countdown runs only in voice mode with a positive choice. */
    fun armed(sec: Int, silent: Boolean): Boolean = !silent && normalize(sec) > 0

    fun durationMs(sec: Int): Long = normalize(sec) * 1_000L

    /**
     * Time from question presented (end of TTS, or card shown in silent)
     * to the grade, in ms. Zero means unknown: the clock never armed, or
     * an old queued row from before duration was stored.
     */
    const val UNKNOWN_DURATION_MS = 0L

    fun answerDurationMs(presentedAtMs: Long, gradedAtMs: Long): Long {
        if (presentedAtMs <= 0L || gradedAtMs <= 0L) return UNKNOWN_DURATION_MS
        val delta = gradedAtMs - presentedAtMs
        if (delta <= 0L) return UNKNOWN_DURATION_MS
        return delta
    }

    /**
     * Whole seconds still on the clock, counting the current second.
     * Zero once [deadlineMs] has been reached.
     */
    fun remainingSec(nowMs: Long, deadlineMs: Long): Int {
        val left = deadlineMs - nowMs
        if (left <= 0L) return 0
        return ((left + 999L) / 1_000L).toInt()
    }

    fun expired(nowMs: Long, deadlineMs: Long): Boolean = nowMs >= deadlineMs

    fun countdownText(remainingSec: Int): String = "${remainingSec.coerceAtLeast(0)}s"

    /**
     * Speech-timeout and no-match restart the microphone.
     * They are not errors and do not consume a listen attempt.
     */
    fun silentRestart(error: Int): Boolean {
        return error == ListenWindow.ERROR_NO_MATCH || error == ListenWindow.ERROR_SPEECH_TIMEOUT
    }

    /** Think-time restarts never charge the Listen attempts setting. */
    fun attemptsCharged(restarts: Int): Int = 0

    /**
     * Slice for the open mic during think time.
     * [gradeWaitMs] is the saved "Wait for speech" value and is ignored:
     * that setting is the grade listen window only.
     */
    fun listenSliceMs(remainingMs: Long, gradeWaitMs: Long): Long {
        if (remainingMs <= 0L) return 0L
        return remainingMs
    }

    /** Settling grace before a quiet reveal: at least 1.5 s of no new speech. */
    fun settleMs(listenSilenceMs: Long): Long = maxOf(1_500L, listenSilenceMs)

    /** What think time does next with the current speech state. */
    enum class Step { SETTLE, REVEAL, RESTART_QUIET, ACT }

    /**
     * Decide the next think-time action from the latest speech [hear] and the
     * clock. A spent deadline reveals now; a command acts at once; a spoken
     * answer settles to keep listening; a quiet pause with something already
     * buffered reveals it; otherwise restart the mic quietly.
     */
    fun nextStep(hear: Hear, bufferEmpty: Boolean, remainingMs: Long, settling: Boolean): Step {
        if (remainingMs <= 0L) return Step.REVEAL
        return when (hear) {
            Hear.REPEAT, Hear.SKIP, Hear.STOP_DRILL, Hear.SHOW_ANSWER -> Step.ACT
            Hear.ANSWER -> Step.SETTLE
            Hear.EMPTY -> if (settling && !bufferEmpty) Step.REVEAL else Step.RESTART_QUIET
        }
    }

    enum class Hear { EMPTY, SHOW_ANSWER, ANSWER, REPEAT, SKIP, STOP_DRILL }

    /** What think time does with speech that is not a letter on the card. */
    enum class UnmatchedAction { REVEAL_JUDGE, COUNT_MISS }

    /** Own-words on and something was said: reveal and judge it. Otherwise count a miss. */
    fun unmatchedAction(heard: String?, ownWordsOn: Boolean): UnmatchedAction =
        if (!heard.isNullOrBlank() && ownWordsOn) UnmatchedAction.REVEAL_JUDGE else UnmatchedAction.COUNT_MISS

    /** A spoken answer or "show answer" ends the timer before it runs out. A tap is separate. */
    fun endsEarly(hear: Hear): Boolean = hear == Hear.ANSWER || hear == Hear.SHOW_ANSWER

    fun classify(raw: String?): Hear {
        if (raw.isNullOrBlank()) return Hear.EMPTY
        val text = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (isShowAnswer(text)) return Hear.SHOW_ANSWER
        return when (GradeMapper.parse(text)) {
            VoiceAction.Repeat -> Hear.REPEAT
            VoiceAction.Skip -> Hear.SKIP
            VoiceAction.Pause -> Hear.STOP_DRILL
            else -> Hear.ANSWER
        }
    }

    fun isShowAnswer(text: String): Boolean {
        val t = text.trim().lowercase()
            .replace(Regex("[.!?]+$"), "")
            .replace(Regex("\\s+"), " ")
        return t == "show answer" || t == "show the answer"
    }
}
