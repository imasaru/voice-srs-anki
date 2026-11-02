package com.evan.voicesrs

/** Deck counts for the header: [due] = is:due (reviews + learning due now), [new] = new cards available today. */
data class DueCounts(val due: Int, val new: Int, val newCapped: Boolean)

object DueSplit {
    /**
     * New cards shown today. With a readable [perDay] limit and [introducedToday]
     * count: min(unseen, max(0, perDay - introducedToday)), capped = true.
     * Otherwise all [unseen] cards, capped = false.
     */
    fun counts(due: Int, unseen: Int, perDay: Int?, introducedToday: Int?): DueCounts {
        val d = due.coerceAtLeast(0)
        val u = unseen.coerceAtLeast(0)
        if (perDay == null || introducedToday == null || perDay < 0) return DueCounts(d, u, false)
        val left = (perDay - introducedToday).coerceAtLeast(0)
        return DueCounts(d, minOf(u, left), true)
    }

    /** "27 due · 134 new" (same separator as SlimBarText). */
    fun label(c: DueCounts): String = "${c.due} due · ${c.new} new"

    /** Cards the online Anki reviewer will show this session: due + today's new. */
    fun sessionTotal(c: DueCounts): Int = c.due + c.new
}
