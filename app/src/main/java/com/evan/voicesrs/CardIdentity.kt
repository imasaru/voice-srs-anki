package com.evan.voicesrs

/**
 * Online review must speak, show, and grade one card.
 * A mismatch is not graded and the other card's answer is not shown.
 */
object CardIdentity {
    const val MAX_CONSECUTIVE_RESYNCS = 3

    enum class Verdict { PROCEED, RESYNC }

    /** After guiShowAnswer, the reviewer card must still be the one we asked. */
    fun onReveal(expectedId: Long, shownId: Long?): Verdict {
        return if (shownId != null && shownId == expectedId) Verdict.PROCEED else Verdict.RESYNC
    }

    /** Cheap re-check immediately before guiAnswerCard. */
    fun beforeGrade(expectedId: Long, currentId: Long?): Verdict = onReveal(expectedId, currentId)

    /**
     * The card object whose question and answer are shown together.
     * Null when the reviewer moved, so the other card is not displayed.
     */
    fun sameCard(expectedId: Long, shown: AnkiCard?): AnkiCard? {
        if (shown == null || onReveal(expectedId, shown.cardId) != Verdict.PROCEED) return null
        return shown
    }

    /** [consecutive] is 1-based after counting this resync. */
    fun shouldStop(consecutive: Int): Boolean = consecutive >= MAX_CONSECUTIVE_RESYNCS

    fun mismatchLog(expected: Long, got: Long?): String {
        return "card changed under us expected=$expected got=${got ?: 0}"
    }
}
