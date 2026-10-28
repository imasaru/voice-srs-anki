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

    companion object {
        const val LISTEN_TIMEOUT_MS = 20_000L
    }
}

/** What the grader needs to know about the current card. */
data class GradeContext(
    val card: AnkiCard,
    /** The answer text exactly as spoken by TTS (for "repeat"). */
    val spokenAnswer: String,
    /** 0-based index of this card in the current drill. */
    val cardIndex: Int = 0,
    /** When true, skip spoken rating guides. Missed listens still count toward [listenAttempts]. */
    val speedMode: Boolean = false,
    /** How long each grade listen waits. The drill passes the saved listen wait. */
    val listenTimeoutMs: Long = Grader.LISTEN_TIMEOUT_MS,
    /** Grade listens before auto-silent. Clamped to 1..5. The same count is used for the answer. */
    val listenAttempts: Int = ListenWindow.DEFAULT_ATTEMPTS,
    /**
     * Ease already claimed for this card (a rating tap), or null.
     * When non-null the grader returns that ease and does not listen.
     */
    val claimedEase: () -> Int? = { null }
)

/** Blocking voice I/O owned by the drill service. */
interface VoiceIo {
    /** Speak text to completion (blocking). */
    fun speak(text: String)
    /** Listen once; returns the transcript, or null on timeout/silence/error. */
    fun listen(timeoutMs: Long): String?
    fun beep()
    /** True once the user pressed Stop (or the session is ending). A silent listen then must not grade. */
    fun stopped(): Boolean = false
    /** Silent mode flipped on. The grader must not invent an ease; the drill waits for a tap. */
    fun audioSuppressed(): Boolean = false
}

/** The learner turned silent on during voice grading. Wait for a chip instead. */
class SilentHandoff : Exception()

/**
 * Every listen attempt missed. The drill switches to silent and waits.
 * This is not a grade and must not advance the card.
 */
class ListenGaveUp : Exception()

/** Skip this card. The drill must not send an ease. */
class SkipRequested : Exception()

/** Pause or stop the drill without grading the open card. */
class PauseRequested : Exception()

/** v1 grader: self-grading via spoken grades, plus repeat / skip / pause. */
class VoiceSelfGrader : Grader {
    companion object {
        const val MAX_REPEATS = 8
    }

    override fun grade(ctx: GradeContext, io: VoiceIo): Int {
        fun tapped(): Int? =
            ctx.claimedEase()?.let { GradeMapper.toAvailableEase(it, ctx.card.buttons) }

        fun handedOff() {
            if (io.audioSuppressed()) throw SilentHandoff()
        }
        handedOff()
        tapped()?.let { return it }
        val opening = RatingGuides.initialPrompt(ctx.cardIndex, ctx.speedMode)
        if (opening != null) io.speak(opening)
        handedOff()
        tapped()?.let { return it }
        var attempts = 0
        var repeats = 0
        val max = ListenWindow.clampAttempts(ctx.listenAttempts)
        while (true) {
            handedOff()
            tapped()?.let { return it }
            // Stop pressed while waiting for a grade: end without sending any ease.
            if (io.stopped()) throw PauseRequested()
            val action = GradeMapper.parse(io.listen(ctx.listenTimeoutMs))
            // A tap during the listen wins over the transcript and over Silent flipping on.
            tapped()?.let { return it }
            handedOff()
            when (action) {
                VoiceAction.Repeat -> {
                    repeats += 1
                    if (repeats > MAX_REPEATS) throw ListenGaveUp()
                    attempts = 0
                    io.speak(ctx.spokenAnswer)
                    handedOff()
                    tapped()?.let { return it }
                    continue
                }
                is VoiceAction.Grade -> return GradeMapper.toAvailableEase(action.ease, ctx.card.buttons)
                VoiceAction.Skip -> throw SkipRequested()
                VoiceAction.Pause -> throw PauseRequested()
                VoiceAction.None -> {
                    // Stop during the listen: leave the card ungraded. Do not switch to silent.
                    if (io.stopped()) throw PauseRequested()
                    attempts += 1
                    tapped()?.let { return it }
                    if (ListenWindow.gaveUp(attempts, max, lastMissed = true)) throw ListenGaveUp()
                    val again = RatingGuides.reprompt(ctx.speedMode)
                    if (again != null) io.speak(again)
                    handedOff()
                    tapped()?.let { return it }
                }
            }
        }
    }
}
