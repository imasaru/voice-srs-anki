package com.evan.voicesrs

/**
 * Pluggable grading path. The ease submitted to AnkiConnect's guiAnswerCard
 * always comes from a [Grader], so the drill loop never hard-codes how a
 * spoken response becomes an ease value.
 *
 * v1: [VoiceSelfGrader] — the user grades themselves with the spoken words
 * again/hard/good/easy (+repeat to re-hear the answer).
 *
 * v2 (future, NOT implemented): an LlmJudgeGrader that captures the full
 * spoken answer and has a model judge it against criteria to produce the
 * ease. That grader will need audio/transcript capture, which [VoiceIo]
 * can grow without changing the drill loop.
 */
interface Grader {
    /**
     * Interactively grade the card whose answer was just spoken.
     * Implementations may speak/listen via [io] as needed.
     * @return ease 1..4 to submit via guiAnswerCard.
     */
    fun grade(ctx: GradeContext, io: VoiceIo): Int
}

/** What the grader needs to know about the current card. */
data class GradeContext(
    val card: AnkiCard,
    /** The answer text exactly as spoken by TTS (for "repeat"). */
    val spokenAnswer: String,
    /** 0-based index of this card in the current drill. */
    val cardIndex: Int = 0,
    /** When true, skip the spoken rating guide and the one retry guide. */
    val speedMode: Boolean = false
)

/** Blocking voice I/O owned by the drill service. */
interface VoiceIo {
    /** Speak text to completion (blocking). */
    fun speak(text: String)
    /** Listen once; returns the transcript, or null on timeout/silence/error. */
    fun listen(timeoutMs: Long): String?
    fun beep()
}

/** Skip this card. The drill must not send an ease. */
class SkipRequested : Exception()

/** Pause or stop the drill without grading the open card. */
class PauseRequested : Exception()

/** v1 grader: self-grading via spoken grades, plus repeat / skip / pause. */
class VoiceSelfGrader : Grader {
    override fun grade(ctx: GradeContext, io: VoiceIo): Int {
        val opening = RatingGuides.initialPrompt(ctx.cardIndex, ctx.speedMode)
        if (opening != null) io.speak(opening)
        var reprompted = false
        while (true) {
            when (val action = GradeMapper.parse(io.listen(LISTEN_TIMEOUT_MS))) {
                VoiceAction.Repeat -> {
                    io.speak(ctx.spokenAnswer)
                    continue
                }
                is VoiceAction.Grade -> return GradeMapper.toAvailableEase(action.ease, ctx.card.buttons)
                VoiceAction.Skip -> throw SkipRequested()
                VoiceAction.Pause -> throw PauseRequested()
                VoiceAction.None -> {
                    if (!reprompted) {
                        reprompted = true
                        val again = RatingGuides.reprompt(ctx.speedMode)
                        if (again != null) io.speak(again)
                    } else {
                        // Still nothing: conservative default so the card comes back sooner.
                        return GradeMapper.toAvailableEase(2, ctx.card.buttons)
                    }
                }
            }
        }
    }

    companion object {
        const val LISTEN_TIMEOUT_MS = 20_000L
    }
}
