package com.evan.voicesrs

/**
 * Hands the judge's reason from DrillService to the turn ReviewLog creates for the same grade.
 * The reason is then saved on that turn (ReviewTurn.judgeReason / judgeEase, written by
 * ChatHistoryStore), so it survives restarts and belongs to the turn, not the card.
 */
object JudgeNote {
    private val pending = java.util.concurrent.ConcurrentHashMap<Long, Pair<Int, String>>()

    fun put(cardId: Long, ease: Int, reason: String) { pending[cardId] = ease to reason }

    fun drop(cardId: Long) { pending.remove(cardId) }

    /** Consume the pending reason for [cardId]; null unless the recorded [ease] is the judge's. */
    fun take(cardId: Long, ease: Int): String? {
        val (e, r) = pending.remove(cardId) ?: return null
        return if (e == ease && r.isNotBlank()) r else null
    }

    /** Reason to show on a past turn: only while it still has the rating the judge gave. */
    fun shown(turn: ReviewTurn): String? {
        val e = turn.judgeEase ?: return null
        return turn.judgeReason.takeIf { it.isNotBlank() && turn.ease == e }
    }

    fun clear() = pending.clear()
}
