package com.evan.voicesrs

/**
 * Where an Ask the AI question is attached.
 * The main box uses the card on screen. An older card is reached only from
 * that card's own reply thread. Asking is not an answer and does not grade.
 */
object ChatTarget {
    data class Screen(val cardId: Long, val ordinal: Int, val revealed: Boolean)

    data class Graded(val turnId: Long, val cardId: Long)

    sealed class Target {
        data class Live(val cardId: Long, val ordinal: Int, val revealed: Boolean) : Target()
        data class OlderThread(val turnId: Long) : Target()
        /** No card on screen. The question is about the selected deck. */
        data object General : Target()
        data object None : Target()
    }

    /**
     * The composer. A card on screen stays that card.
     * No card is a general question, never the previous graded turn.
     */
    fun composer(screen: Screen?): Target {
        if (screen == null || screen.ordinal <= 0) return Target.General
        return Target.Live(screen.cardId, screen.ordinal, screen.revealed)
    }

    /** One card's reply thread. Unknown ids are ignored. */
    fun thread(turnId: Long, graded: List<Graded>): Target {
        if (graded.none { it.turnId == turnId }) return Target.None
        return Target.OlderThread(turnId)
    }

    fun countsAsGrade(): Boolean = false
}
