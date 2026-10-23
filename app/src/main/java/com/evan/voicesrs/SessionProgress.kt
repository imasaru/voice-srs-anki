package com.evan.voicesrs

object GoalModes {
    const val OFF = "off"
    const val CARDS = "cards"
    const val MINUTES = "minutes"

    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        CARDS, "n", "card" -> CARDS
        MINUTES, "m", "minute", "time" -> MINUTES
        else -> OFF
    }
}

/**
 * Cards done, streak of non-again grades, and a time estimate from the average
 * so far. Pure Kotlin.
 */
class SessionProgress(val dueAtStart: Int) {
    var done: Int = 0
        private set
    var streak: Int = 0
        private set
    var goodOrEasy: Int = 0
        private set
    var totalMs: Long = 0
        private set
    var timedCards: Int = 0
        private set

    fun recordGrade(ease: Int, cardMs: Long) {
        done += 1
        streak = if (ease <= 1) 0 else streak + 1
        if (ease >= 3) goodOrEasy += 1
        if (cardMs > 0) {
            totalMs += cardMs
            timedCards += 1
        }
    }

    /** Remaining cards in this session (due-at-start or batch size), not remaining deck due. */
    fun left(): Int = (dueAtStart - done).coerceAtLeast(0)

    fun avgSec(): Int = if (timedCards == 0) 0 else (totalMs / timedCards / 1000L).toInt()

    /** Average seconds per graded card, times cards still left. 0 before the first grade. */
    fun etaSec(): Int = if (timedCards == 0) 0 else avgSec() * left()

    fun line(): String {
        val eta = if (timedCards == 0) "" else " · ~${etaLabel(etaSec())}"
        return "Done $done · Left ${left()} · Streak $streak$eta"
    }

    fun goalReached(mode: String, goalCards: Int, goalMinutes: Int, elapsedMs: Long): Boolean {
        return when (GoalModes.normalize(mode)) {
            GoalModes.CARDS -> goalCards > 0 && done >= goalCards
            GoalModes.MINUTES -> goalMinutes > 0 && elapsedMs >= goalMinutes * 60_000L
            else -> false
        }
    }

    /** "Done: 20 cards, 17 good or easy, 12 minutes." Minutes are rounded. */
    fun spokenSummary(elapsedMs: Long): String {
        val mins = ((elapsedMs.coerceAtLeast(0L) + 30_000L) / 60_000L).toInt()
        val cards = if (done == 1) "card" else "cards"
        val minutes = if (mins == 1) "minute" else "minutes"
        return "Done: $done $cards, $goodOrEasy good or easy, $mins $minutes."
    }

    companion object {
        fun etaLabel(sec: Int): String {
            if (sec < 60) return "$sec sec"
            val minutes = (sec + 30) / 60
            return "$minutes min"
        }

        /**
         * [line] is remaining work in the current session, not remaining
         * cards in the deck. Hide it when no drill is running so a stale
         * "Left N" cannot disagree with "Due now".
         */
        fun visibleLine(running: Boolean, line: String): String =
            if (running) line else ""

        fun goalLabel(mode: String, goalCards: Int, goalMinutes: Int): String {
            return when (GoalModes.normalize(mode)) {
                GoalModes.CARDS -> "Goal $goalCards cards"
                GoalModes.MINUTES -> "Goal $goalMinutes min"
                else -> ""
            }
        }

        fun withGoal(line: String, goal: String): String {
            val parts = listOf(line.trim(), goal.trim()).filter { it.isNotEmpty() }
            return parts.joinToString(" · ")
        }
    }
}
