package com.evan.voicesrs

/**
 * Who owns the microphone.
 *
 * Dictate used to call chat-pause, and chat-pause always cancelled the drill
 * recognizer. That cancel and a new dictation recognizer then shared the one
 * system recognition session. The dictation session ended in an error, and
 * the error path dropped the words. Pause must release the drill listener
 * first, then start a separate dictation recognizer, and must never cancel
 * dictation once it owns the mic.
 */
object DictationGate {
    enum class Owner { NONE, DRILL, DICTATION }

    enum class Step { RELEASE_DRILL, START_DICTATION, CANCEL_DRILL }

    data class State(val owner: Owner = Owner.NONE, val drillListening: Boolean = false)

    data class Plan(val state: State, val steps: List<Step>)

    sealed class Result {
        data class Text(val text: String) : Result()
        data object Miss : Result()
        data object Empty : Result()
    }

    /** Release the drill mic, then start dictation. The dictation start is last. */
    fun beginDictation(state: State): Plan {
        val steps = ArrayList<Step>(2)
        if (state.drillListening || state.owner == Owner.DRILL) steps += Step.RELEASE_DRILL
        steps += Step.START_DICTATION
        return Plan(State(Owner.DICTATION, drillListening = false), steps)
    }

    /**
     * Pause may cancel the drill listener. It must not cancel dictation.
     * An empty step list means the recognizer is left alone.
     */
    fun pause(state: State): Plan {
        if (state.owner == Owner.DICTATION) return Plan(state, emptyList())
        if (state.drillListening || state.owner == Owner.DRILL) {
            return Plan(State(Owner.NONE, drillListening = false), listOf(Step.CANCEL_DRILL))
        }
        return Plan(state, emptyList())
    }

    /** Final text wins. A partial still counts when the recognizer then errors. */
    fun result(finalText: String?, partial: String?, error: Boolean): Result {
        val text = finalText?.trim().orEmpty().ifBlank { partial?.trim().orEmpty() }
        if (text.isNotEmpty()) return Result.Text(text)
        if (error) return Result.Miss
        return Result.Empty
    }
}
