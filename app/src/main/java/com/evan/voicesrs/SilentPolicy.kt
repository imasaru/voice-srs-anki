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
 * Buttons on the open card. Visibility is the card face only.
 * [silent] is accepted so a Silent toggle can be compared and ignored:
 * question -> Show answer; answer -> Again / Hard / Good / Easy.
 */
data class AnswerButtons(val showAnswer: Boolean, val rating: Boolean)

object AnswerControls {
    fun of(
        hasQuestion: Boolean,
        answerShown: Boolean,
        silent: Boolean = false
    ): AnswerButtons {
        val buttons = when {
            !hasQuestion -> AnswerButtons(showAnswer = false, rating = false)
            answerShown -> AnswerButtons(showAnswer = false, rating = true)
            else -> AnswerButtons(showAnswer = true, rating = false)
        }
        // Same object either way. Silent must not hide or show a control.
        return if (silent) buttons else buttons
    }
}

/**
 * What turning Silent off does to the card that is already up.
 * On stops audio and the mic. Off reads the face that is on screen.
 */
enum class CardResume {
    /** Silent is on, or no card is up. Do not speak. */
    HOLD,
    /** Read the question aloud, then open the microphone. */
    READ_QUESTION,
    /** Read the answer that is already on screen. */
    READ_ANSWER
}

/**
 * Live audio switch. Silent means no TTS, no beeps, and no microphone.
 * Turning it on aborts the utterance in progress. Turning it off resumes
 * the current card: the question (then listen) or the answer.
 */
object AudioGate {
    fun outputAllowed(silent: Boolean): Boolean = !silent
    fun micAllowed(silent: Boolean): Boolean = !silent
    fun earconAllowed(silent: Boolean): Boolean = !silent
    fun abortInProgress(nowSilent: Boolean): Boolean = nowSilent

    /**
     * Mid-card Silent off. [answerShown] is the card face, not the switch.
     * No card means there is nothing to resume, so the next prompt uses voice.
     */
    fun resume(nowSilent: Boolean, cardUp: Boolean, answerShown: Boolean): CardResume {
        if (nowSilent || !cardUp) return CardResume.HOLD
        return if (answerShown) CardResume.READ_ANSWER else CardResume.READ_QUESTION
    }

    /** Show answer cuts TTS and the open microphone, then the answer is revealed. */
    fun showAnswerTapInterruptsAudio(): Boolean = true

    /** A rating tap cuts TTS and listening in both modes, then the drill grades. */
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
