package com.evan.voicesrs

/**
 * Pure session decisions. No Android types.
 * [FgsType.logName] is the value in the `fgs type=` log line.
 * A silent session never creates a recognizer, so it never requests
 * recording audio focus and never asks for RECORD_AUDIO.
 */
enum class FgsType(val logName: String) {
    MICROPHONE("microphone"),
    MEDIA_PLAYBACK("mediaPlayback")
}

/** How the answer leaves the question side. */
enum class AnswerReveal {
    /** Beep, then listen. Grades can be spoken. */
    VOICE,
    /** Wait for the Show answer tap, then a grade chip. */
    SHOW_ANSWER
}

data class SessionMode(
    val silent: Boolean,
    val fgsType: FgsType,
    val createRecognizer: Boolean,
    val requestRecordAudio: Boolean,
    val answerReveal: AnswerReveal
)

object SilentPolicy {
    fun of(silent: Boolean): SessionMode {
        return if (silent) {
            SessionMode(
                silent = true,
                fgsType = FgsType.MEDIA_PLAYBACK,
                createRecognizer = false,
                requestRecordAudio = false,
                answerReveal = AnswerReveal.SHOW_ANSWER
            )
        } else {
            SessionMode(
                silent = false,
                fgsType = FgsType.MICROPHONE,
                createRecognizer = true,
                requestRecordAudio = true,
                answerReveal = AnswerReveal.VOICE
            )
        }
    }
}
