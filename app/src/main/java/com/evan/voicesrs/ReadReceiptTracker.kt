package com.evan.voicesrs

/**
 * When a fresh explanation becomes read.
 * Visible means the explanation section is at least half on screen.
 * The timer resets if that drops below half, the turn leaves the sample,
 * or the activity is not resumed. Expanding the bubble marks it read now.
 * [now] is injected so tests can move the clock without sleeping.
 */
class ReadReceiptTracker(
    private val now: () -> Long,
    private val dwellMs: Long = DWELL_MS,
    private val threshold: Float = VISIBLE_FRACTION
) {
    private val started = HashMap<Long, Long>()
    private val read = HashSet<Long>()

    /**
     * @param visible turn id to the fraction of the explanation section on screen (0..1)
     * @param expanded turns whose bubble is expanded; those are read immediately
     * @return turn ids that became read on this sample
     */
    fun onSample(visible: Map<Long, Float>, resumed: Boolean, expanded: Set<Long>): Set<Long> {
        if (!resumed) {
            started.clear()
            return emptySet()
        }
        val newly = LinkedHashSet<Long>()
        for (id in expanded) {
            if (read.add(id)) newly += id
            started.remove(id)
        }
        val still = HashSet<Long>()
        for ((id, fraction) in visible) {
            if (id in read || id in expanded) continue
            if (fraction < threshold) {
                started.remove(id)
                continue
            }
            still += id
            val began = started.getOrPut(id) { now() }
            if (now() - began >= dwellMs) {
                if (read.add(id)) newly += id
                started.remove(id)
            }
        }
        val gone = started.keys.filter { it !in still }
        for (id in gone) started.remove(id)
        return newly
    }

    companion object {
        const val DWELL_MS = 1_500L
        const val VISIBLE_FRACTION = 0.5f
    }
}
