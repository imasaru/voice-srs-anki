package com.evan.voicesrs

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure exam-date scheduling math for a single deck. No Android framework,
 * no network, no AnkiConnect. Exam dates are stored in app prefs per deck
 * (wired up separately).
 */
object ExamPlan {

    data class CardDue(
        val cardId: Long,
        val dueDate: LocalDate?,
        val isNew: Boolean
    )

    data class Coverage(
        val total: Int,
        val covered: Int
    ) {
        val percent: Int
            get() = if (total == 0) 100 else covered * 100 / total
    }

    /** Days between today and the exam; negative when the exam is past. */
    fun daysLeft(today: LocalDate, exam: LocalDate): Int =
        ChronoUnit.DAYS.between(today, exam).toInt()

    /**
     * Desired retention for a deck as the exam approaches. Ramps linearly from
     * [base] (far away / over) up to [peak] on exam day over [rampDays] days.
     * Rounded to 2 decimals, clamped into 0.70..0.99.
     */
    fun targetRetention(
        daysLeft: Int,
        base: Double = 0.90,
        peak: Double = 0.95,
        rampDays: Int = 21
    ): Double {
        require(base <= peak) { "base ($base) must be <= peak ($peak)" }
        require(rampDays > 0) { "rampDays ($rampDays) must be > 0" }

        val raw = when {
            daysLeft < 0 -> base
            daysLeft >= rampDays -> base
            else -> base + (peak - base) * (rampDays - daysLeft) / rampDays
        }
        val rounded = Math.round(raw * 100.0) / 100.0
        return rounded.coerceIn(0.70, 0.99)
    }

    /** True while the sweep window (default 7 days) is still open. */
    fun inSweepWindow(daysLeft: Int, windowDays: Int = 7): Boolean =
        daysLeft in 0..windowDays

    /**
     * setDueDate "days" argument for a sweep. null before the window; "0" on
     * exam day / the day before; otherwise "0-(daysLeft-1)" so cards land
     * strictly before the exam.
     */
    fun sweepRange(daysLeft: Int): String? = when {
        daysLeft < 0 -> null
        daysLeft <= 1 -> "0"
        else -> "0-" + (daysLeft - 1)
    }

    /** Cards that should be swept back: new, un-dated, or due on/after the exam. */
    fun cardsToSweep(cards: List<CardDue>, exam: LocalDate): List<Long> =
        cards.filter { it.isNew || it.dueDate == null || it.dueDate >= exam }
            .map { it.cardId }

    /** How many cards are already scheduled before the exam. */
    fun coverage(cards: List<CardDue>, exam: LocalDate): Coverage {
        val total = cards.size
        val covered = cards.count { !it.isNew && it.dueDate != null && it.dueDate < exam }
        return Coverage(total, covered)
    }

    /** Spoken status line. Empty when the exam is past. */
    fun spokenSummary(label: String, daysLeft: Int, percent: Int): String = when {
        daysLeft < 0 -> ""
        daysLeft == 0 -> "$label is today, $percent% covered."
        daysLeft == 1 -> "$label in 1 day, $percent% covered."
        else -> "$label in $daysLeft days, $percent% covered."
    }
}
