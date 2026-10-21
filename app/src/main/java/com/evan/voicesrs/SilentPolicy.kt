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

/**
 * What the question row may be tapped while a card is up.
 * Silent mode shows Show answer and the rating options immediately.
 * Voice mode shows ratings only after the answer is revealed.
 */
data class AnswerButtons(val showAnswer: Boolean, val rating: Boolean)

object AnswerControls {
    fun of(
        silent: Boolean,
        hasQuestion: Boolean,
        answerShown: Boolean,
        voiceRating: Boolean
    ): AnswerButtons {
        if (!hasQuestion) return AnswerButtons(showAnswer = false, rating = false)
        if (silent) return AnswerButtons(showAnswer = !answerShown, rating = true)
        return AnswerButtons(showAnswer = false, rating = voiceRating && answerShown)
    }
}

/**
 * Live audio switch. Silent means no TTS, no beeps, and no microphone.
 * Turning it on aborts the utterance in progress. Turning it off does not
 * replay that utterance; the next prompt uses voice.
 */
object AudioGate {
    fun outputAllowed(silent: Boolean): Boolean = !silent
    fun micAllowed(silent: Boolean): Boolean = !silent
    fun earconAllowed(silent: Boolean): Boolean = !silent
    fun abortInProgress(nowSilent: Boolean): Boolean = nowSilent
    fun replayCurrentPrompt(): Boolean = false

    /** A rating tap cuts TTS in both modes and the drill continues with that ease. */
    fun ratingTapInterruptsTts(): Boolean = true
}

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
