package com.evan.voicesrs

/**
 * Correcting an earlier rated multiple-choice card.
 * The current card is never part of this. Confirming calls answerCards, or
 * replaces a grade that is still in the offline queue. setDueDate is not used.
 * Cancel restores the marks the learner had.
 */
object PastGrade {
    data class Preview(
        val letter: String,
        val correct: Boolean,
        val ease: Int,
        val prompt: String
    )

    enum class Route { QUEUE, ANSWER_CARDS }

    data class QueueResult(
        val grades: List<PendingGrade>,
        val replaced: Boolean,
        val route: Route,
        val usesSetDueDate: Boolean = false
    )

    data class Commit(
        val transcript: String,
        val ease: Int,
        val regenerate: Boolean
    )

    fun preview(
        letter: String,
        correctLetter: String?,
        currentCard: Boolean,
        rated: Boolean,
        mcq: Boolean
    ): Preview? {
        if (currentCard || !rated || !mcq) return null
        val clean = letter.trim().uppercase()
        if (clean.length != 1 || clean[0] !in 'A'..'F') return null
        val right = correctLetter != null && clean.equals(correctLetter, ignoreCase = true)
        val ease = if (right) 3 else 1
        val prompt = if (right) "Change rating to Good?" else "Change rating to Again?"
        return Preview(clean, right, ease, prompt)
    }

    /**
     * Re-grade the user's own rating pill. Unlike [preview] this is not
     * letter- or mcq-specific: the ease is chosen directly, so it works for
     * any rated turn. Returns null when the turn is the live card, was never
     * rated, or the ease is out of range.
     */
    fun previewEase(ease: Int, currentCard: Boolean, rated: Boolean): Preview? {
        if (currentCard || !rated || ease !in 1..4) return null
        val right = ease >= 3
        val prompt = when (ease) {
            1 -> "Change rating to Again?"
            2 -> "Change rating to Hard?"
            3 -> "Change rating to Good?"
            else -> "Change rating to Easy?"
        }
        return Preview("", right, ease, prompt)
    }

    /** This path does not pause, speak, or advance the live card. */
    fun touchesCurrentCard(): Boolean = false

    /**
     * Swap the ease on the latest queued grade for [cardId].
     * No queued row means the caller must send answerCards. Never setDueDate.
     */
    fun replacePending(
        grades: List<PendingGrade>,
        cardId: Long,
        ease: Int,
        nowMs: Long
    ): QueueResult {
        val index = grades.indexOfLast { it.cardId == cardId }
        if (index < 0) {
            return QueueResult(grades, replaced = false, route = Route.ANSWER_CARDS)
        }
        val copy = grades.toMutableList()
        copy[index] = copy[index].copy(ease = ease, queuedAtMs = nowMs)
        return QueueResult(copy, replaced = true, route = Route.QUEUE)
    }

    /** A new letter with an explanation already on the card is the only regen. */
    fun commit(
        oldLetter: String?,
        newLetter: String,
        ease: Int,
        hasExplanation: Boolean
    ): Commit {
        val letter = newLetter.trim().uppercase()
        val changed = !oldLetter.equals(letter, ignoreCase = true)
        return Commit(letter, ease, regenerate = changed && hasExplanation)
    }

    fun cancel(previousLetter: String?): String? = previousLetter

    /**
     * Commit for a rating-only regrade (previewEase, blank letter): keep the
     * spoken transcript as-is and do not regenerate the explanation.
     */
    fun commitEase(transcript: String, ease: Int): Commit = Commit(transcript, ease, regenerate = false)
}