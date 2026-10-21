package com.evan.voicesrs

/** How far ahead to prefetch, and when an online due-list snapshot is stale. */
object LookaheadWindow {
    const val REFRESH_EVERY = 10
    const val MAX_N = 5

    fun clamp(n: Int): Int = n.coerceIn(0, MAX_N)

    fun shouldRefresh(cardsSinceRefresh: Int): Boolean = cardsSinceRefresh >= REFRESH_EVERY
}

data class AheadCard(
    val cardId: Long,
    val noteId: Long?,
    val front: String,
    val back: String
) {
    val noteKey: Long get() = noteId ?: cardId
}

/** Picks the next uncached cards. Cache hits are skipped and do not count toward N. */
object LookaheadPicker {
    fun nextAfter(
        cards: List<AheadCard>,
        startExclusive: Int,
        n: Int,
        cached: Set<ExplainKey>
    ): List<AheadCard> = take(cards.drop(startExclusive + 1), n, cached)

    /** Online snapshot in due order. [seen] cards were already shown this session. */
    fun nextUnseen(
        cards: List<AheadCard>,
        seen: Set<Long>,
        n: Int,
        cached: Set<ExplainKey>
    ): List<AheadCard> = take(cards.filter { it.cardId !in seen }, n, cached)

    private fun take(cards: List<AheadCard>, n: Int, cached: Set<ExplainKey>): List<AheadCard> {
        val limit = LookaheadWindow.clamp(n)
        if (limit == 0) return emptyList()
        val out = ArrayList<AheadCard>(limit)
        for (card in cards) {
            if (out.size >= limit) break
            val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, card.front, card.back)
            if (ExplainKey(card.noteKey, hash) in cached) continue
            out += card
        }
        return out
    }
}
