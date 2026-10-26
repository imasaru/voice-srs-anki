package com.evan.voicesrs

import java.time.LocalDate

/**
 * Pure exam-date apply planner. Decides clone / assign / save / sweep from
 * the deck config, per-deck prefs, and today's date. AnkiConnect I/O lives
 * in [ExamSync].
 */
object ExamApply {
    data class Input(
        val deck: String,
        val today: LocalDate,
        val examDate: LocalDate?,
        val targetOverride: Double? = null,
        val cloneId: Long? = null,
        val lastApplied: LocalDate? = null,
        val configId: Long? = null,
        val coveragePercent: Int = 0
    )

    data class Plan(
        val enabled: Boolean,
        val skipWrites: Boolean,
        val clone: Boolean,
        val cloneName: String?,
        val cloneFrom: Long?,
        val assign: Boolean,
        val assignId: Long?,
        val save: Boolean,
        val retention: Double?,
        val sweep: Boolean,
        val sweepRange: String?,
        val spoken: String,
        val daysLeft: Int,
        val coveragePercent: Int
    )

    fun cloneName(deck: String): String {
        val name = "VoiceSRS exam $deck"
        return if (name.length <= 90) name else name.take(90)
    }

    fun allQuery(deck: String): String = "deck:\"$deck\""

    fun coveredQuery(deck: String, daysLeft: Int): String =
        "deck:\"$deck\" -is:new prop:due<$daysLeft"

    fun sweepQuery(deck: String, daysLeft: Int): String =
        "deck:\"$deck\" (is:new OR prop:due>=$daysLeft)"

    fun retentionOf(daysLeft: Int, override: Double?): Double {
        val raw = override ?: ExamPlan.targetRetention(daysLeft)
        return raw.coerceIn(0.70, 0.99)
    }

    fun countdownLine(daysLeft: Int, retention: Double, coveragePercent: Int): String {
        if (daysLeft < 0) return ""
        val days = when (daysLeft) {
            0 -> "Exam today"
            1 -> "Exam in 1 day"
            else -> "Exam in $daysLeft days"
        }
        val ret = Math.round(retention * 100.0).toInt()
        return "$days · $ret% retention · $coveragePercent% covered"
    }

    fun spoken(label: String, daysLeft: Int, coveragePercent: Int, retention: Double): String {
        val base = ExamPlan.spokenSummary(label, daysLeft, coveragePercent)
        if (base.isEmpty()) return ""
        val pct = Math.round(retention * 100.0).toInt()
        return "$base Target retention $pct percent."
    }

    fun plan(input: Input): Plan {
        val idle = Plan(
            enabled = false,
            skipWrites = true,
            clone = false,
            cloneName = null,
            cloneFrom = null,
            assign = false,
            assignId = null,
            save = false,
            retention = null,
            sweep = false,
            sweepRange = null,
            spoken = "",
            daysLeft = -1,
            coveragePercent = input.coveragePercent
        )
        val exam = input.examDate ?: return idle
        val days = ExamPlan.daysLeft(input.today, exam)
        if (days < 0) return idle.copy(daysLeft = days)
        val leaf = DeckName.split(input.deck).second.ifBlank { input.deck }
        val retention = retentionOf(days, input.targetOverride)
        val spoken = spoken(leaf, days, input.coveragePercent, retention)
        val enabled = idle.copy(
            enabled = true,
            retention = retention,
            spoken = spoken,
            daysLeft = days,
            coveragePercent = input.coveragePercent
        )
        if (input.lastApplied == input.today) {
            return enabled
        }
        val hasClone = (input.cloneId ?: 0L) != 0L
        val hasConfig = (input.configId ?: 0L) != 0L
        val clone = !hasClone
        if (clone && !hasConfig) {
            return enabled
        }
        val sweepRange = if (ExamPlan.inSweepWindow(days)) ExamPlan.sweepRange(days) else null
        val assignId = if (clone) null else input.cloneId
        val assign = clone || input.configId != input.cloneId
        return enabled.copy(
            skipWrites = false,
            clone = clone,
            cloneName = if (clone) cloneName(input.deck) else null,
            cloneFrom = if (clone) input.configId else null,
            assign = assign,
            assignId = assignId,
            save = true,
            sweep = sweepRange != null,
            sweepRange = sweepRange
        )
    }
}
