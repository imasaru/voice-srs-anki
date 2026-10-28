package com.evan.voicesrs

/**
 * Silent tap versus a recognizer that is still starting, and versus a UI
 * snapshot that was captured before the tap.
 *
 * The listen epoch advances whenever Silent flips. A start posted under the
 * old epoch cannot open the microphone after the tap. A snapshot whose
 * generation is older than the tap cannot move the switch.
 */
object ListenGate {
    data class State(val silent: Boolean, val epoch: Int)

    /** Same value keeps the epoch, so a repeated write does not kill a start. */
    fun toggle(state: State, silent: Boolean): State {
        if (state.silent == silent) return state
        return State(silent = silent, epoch = state.epoch + 1)
    }

    /** Null when Silent is on: nothing may post a start. */
    fun postedEpoch(state: State): Int? = if (state.silent) null else state.epoch

    /**
     * True only for the epoch that was current when the start was posted,
     * and only while Silent is still off.
     */
    fun mayOpenMic(state: State, postedEpoch: Int): Boolean {
        return !state.silent && state.epoch == postedEpoch
    }
}

/**
 * Switch position. [userTap] bumps the generation. [reconcile] drops a paint
 * that was captured before that tap, so the switch stays where the user left it.
 */
object SwitchBoard {
    data class Model(val on: Boolean, val generation: Int)

    data class Paint(val on: Boolean, val generation: Int)

    fun userTap(model: Model, on: Boolean): Model {
        return Model(on = on, generation = model.generation + 1)
    }

    fun reconcile(model: Model, paint: Paint): Model {
        if (paint.generation < model.generation) return model
        return Model(on = paint.on, generation = paint.generation)
    }
}

/**
 * What one Silent change does to the open card.
 * A user turning it on while the question is up reveals the answer, so the
 * rating buttons show and the card is not read aloud. Giving up after missed
 * listens ([fromUser] false) leaves the question on Show answer.
 * Turning it off does not reveal and does not cancel a resume.
 */
object SilentTap {
    data class Plan(
        val silent: Boolean,
        val cancelMic: Boolean,
        val stopThinkTimer: Boolean,
        val revealAnswer: Boolean,
        val showRating: Boolean,
        val showAnswerButton: Boolean
    )

    fun plan(
        wantOn: Boolean,
        wasOn: Boolean,
        hasQuestion: Boolean,
        answerShown: Boolean,
        fromUser: Boolean,
        thinkRunning: Boolean = false
    ): Plan {
        val turningOn = wantOn && !wasOn
        val keepThink = fromUser && turningOn && hasQuestion && !answerShown && thinkRunning
        val reveal = fromUser && turningOn && hasQuestion && !answerShown && !keepThink
        val shown = answerShown || reveal
        val controls = AnswerControls.of(hasQuestion || reveal, shown, wantOn)
        return Plan(
            silent = wantOn,
            cancelMic = wantOn,
            stopThinkTimer = wantOn && !keepThink,
            revealAnswer = reveal,
            showRating = controls.rating,
            showAnswerButton = controls.showAnswer && !reveal
        )
    }

    /**
     * A user flip with the question up, no answer yet, and the think timer
     * running keeps the question and its timer: the mic stays off, nothing
     * reveals, and the timer keeps counting until the deadline, at which point
     * the answer shows as normal. False once the deadline has passed, no timer
     * is running, or Silent is off.
     */
    fun thinkContinues(silent: Boolean, deadlineMs: Long, nowMs: Long): Boolean =
        silent && deadlineMs > 0L && nowMs < deadlineMs
}
