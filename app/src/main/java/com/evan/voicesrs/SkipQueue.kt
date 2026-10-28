package com.evan.voicesrs

/**
 * Requeue policy for a spoken "skip" ([R30]). A skip no longer suspends or drops
 * the card for the whole session; it re-appears a few cards later. Pure Kotlin,
 * no Anki side effects here.
 */
object SkipQueue {
    const val SKIP_GAP = 5
    const val MAX_SKIPS_PER_CARD = 2
    const val SAY = "Skipped. You'll see this card again later."
    const val SAY_SHORT = "Skipped."
    const val SAY_LAST = "Skipped again. Moving it to the end."

    /** Index to re-insert a card skipped at [i] in a queue that now has [size] cards (skipped card already removed). */
    fun insertAt(i: Int, size: Int, gap: Int = SKIP_GAP): Int = minOf(i + gap, size)

    /** True when the card skipped at graded count [at] may come back now. */
    fun dueForReturn(at: Int, done: Int, gap: Int = SKIP_GAP): Boolean = done - at >= gap

    /** Skip number [count] (1-based) for one card: false = over the cap, goes to the end / stays suspended. */
    fun requeue(count: Int): Boolean = count <= MAX_SKIPS_PER_CARD

    fun say(count: Int, speedMode: Boolean): String = when {
        !requeue(count) -> SAY_LAST
        speedMode -> SAY_SHORT
        else -> SAY
    }

    /**
     * Which hint line the answer controls should show (0 = hidden). Pure, mirrors
     * the old silent/non-silent split and adds the answer hint before reveal:
     * 0 = hidden, 1 = grade_guide, 2 = grade_guide_tap, 3 = answer_guide.
     */
    fun guide(hasQuestion: Boolean, answerShown: Boolean, silent: Boolean): Int = when {
        !hasQuestion -> 0;
        silent -> if (answerShown) 2 else 0;
        answerShown -> 1;
        else -> 3
    }
}
