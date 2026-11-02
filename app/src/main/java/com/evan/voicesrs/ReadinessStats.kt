package com.evan.voicesrs

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Anki's study day: the calendar day shifted back by the rollover hour (Anki default 4). */
object AnkiDay {
    const val DEFAULT_ROLLOVER_HOUR = 4

    fun dayOf(ms: Long, zone: ZoneId, rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).minusHours(rolloverHour.toLong()).toLocalDate()

    fun today(zone: ZoneId, nowMs: Long = System.currentTimeMillis(), rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): LocalDate =
        dayOf(nowMs, zone, rolloverHour)

    /** Epoch ms where Anki day [day] starts (inclusive). */
    fun startMs(day: LocalDate, zone: ZoneId, rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): Long =
        day.atStartOfDay(zone).plusHours(rolloverHour.toLong()).toInstant().toEpochMilli()
}

/** One card as Anki reports it (cardsInfo): type 0 new, 1 learn, 2 review, 3 relearn; queue -1 suspended; ivl days. */
data class CardSnap(
    val cardId: Long,
    val deck: String,
    val tags: List<String> = emptyList(),
    val type: Int,
    val queue: Int = 0,
    val ivl: Int = 0,
    val lapses: Int = 0
)

/** One revlog row: type 0 learn, 1 review, 2 relearn, 3 filtered; lastIvl days (negative = seconds, learning). */
data class RevRow(
    val atMs: Long,
    val cardId: Long,
    val ease: Int,
    val type: Int,
    val lastIvl: Int = 0,
    val deck: String = ""
)

data class StateCounts(val new: Int, val learning: Int, val young: Int, val mature: Int, val suspended: Int) {
    val total: Int get() = new + learning + young + mature
}

data class Retention(val passed: Int, val total: Int, val youngPassed: Int, val youngTotal: Int, val maturePassed: Int, val matureTotal: Int) {
    val pct: Double? get() = pct(passed, total)
    val youngPct: Double? get() = pct(youngPassed, youngTotal)
    val maturePct: Double? get() = pct(maturePassed, matureTotal)
    private fun pct(a: Int, b: Int): Double? = if (b == 0) null else Math.round(a * 1000.0 / b) / 10.0
}

data class Pace(val days: Int, val newCards: Int, val reviews: Int) {
    val newPerDay: Double get() = if (days <= 0) 0.0 else Math.round(newCards * 10.0 / days) / 10.0
    val reviewsPerDay: Double get() = if (days <= 0) 0.0 else Math.round(reviews * 10.0 / days) / 10.0
}

data class LapseRow(val name: String, val lapses: Int, val reviews: Int) {
    val pct: Double get() = if (reviews == 0) 0.0 else Math.round(lapses * 1000.0 / reviews) / 10.0
}

data class Projection(
    val today: LocalDate,
    val exam: LocalDate?,
    val daysLeft: Int?,
    val newRemaining: Int,
    val newPerDay: Double,
    /** ceil(0.9 * newRemaining / newPerDay); null when pace is 0 and cards remain. */
    val daysToIntroduce: Int?,
    /** today + daysToIntroduce + MATURE_DAYS. */
    val readyDate: LocalDate?,
    /** daysLeft - (daysToIntroduce + MATURE_DAYS); null when unknown. */
    val slackDays: Int?,
    /** New cards per day needed to be ready by the exam; null when no exam or no time left. */
    val requiredNewPerDay: Double?
) {
    val onTrack: Boolean? get() = slackDays?.let { it >= 0 }
}

/**
 * Pure exam-readiness maths. No Android, no network. The model gets these
 * numbers as data and never does the arithmetic itself.
 */
object ReadinessStats {
    const val MATURE_IVL = 21
    const val MATURE_DAYS = 32
    const val COVER_FRACTION = 0.9

    fun states(cards: List<CardSnap>): StateCounts {
        var n = 0; var l = 0; var y = 0; var m = 0; var s = 0
        for (c in cards) {
            if (c.queue == -1) s++
            when {
                c.type == 0 -> n++
                c.type == 1 || c.type == 3 -> l++
                c.ivl >= MATURE_IVL -> m++
                else -> y++
            }
        }
        return StateCounts(n, l, y, m, s)
    }

    /** States per direct deck name, sorted by deck name. */
    fun statesByDeck(cards: List<CardSnap>): Map<String, StateCounts> =
        cards.groupBy { it.deck }.toSortedMap().mapValues { states(it.value) }

    /** True retention: review-type rows only (type 1), pass = ease > 1; young/mature by lastIvl. */
    fun retention(rows: List<RevRow>, fromMs: Long, toMs: Long): Retention {
        val r = rows.filter { it.type == 1 && it.atMs in fromMs until toMs }
        val young = r.filter { it.lastIvl < MATURE_IVL }
        val mature = r.filter { it.lastIvl >= MATURE_IVL }
        return Retention(
            r.count { it.ease > 1 }, r.size,
            young.count { it.ease > 1 }, young.size,
            mature.count { it.ease > 1 }, mature.size
        )
    }

    /**
     * New cards = distinct cards whose first type-0 row is in the window and
     * that have no row before the window. Reviews = all rows in the window.
     */
    fun pace(rows: List<RevRow>, fromMs: Long, toMs: Long, days: Int): Pace {
        val before = rows.filter { it.atMs < fromMs }.map { it.cardId }.toSet()
        val inWin = rows.filter { it.atMs in fromMs until toMs }
        val newIds = inWin.filter { it.type == 0 && it.cardId !in before }.map { it.cardId }.toSet()
        return Pace(days, newIds.size, inWin.size)
    }

    /** Lapse rate of review rows (type 1, ease 1) grouped by [key]; highest pct first, then name. */
    fun lapses(rows: List<RevRow>, key: (RevRow) -> List<String>): List<LapseRow> {
        val acc = LinkedHashMap<String, IntArray>()
        for (r in rows) {
            if (r.type != 1) continue
            for (k in key(r)) {
                val a = acc.getOrPut(k) { IntArray(2) }
                a[1]++
                if (r.ease == 1) a[0]++
            }
        }
        return acc.map { LapseRow(it.key, it.value[0], it.value[1]) }
            .sortedWith(compareByDescending<LapseRow> { it.pct }.thenBy { it.name })
    }

    fun lapsesByDeck(rows: List<RevRow>): List<LapseRow> = lapses(rows) { listOf(it.deck.ifBlank { "Unknown" }) }

    fun lapsesByTag(rows: List<RevRow>, tags: Map<Long, List<String>>): List<LapseRow> =
        lapses(rows) { StudyTools.categories(tags[it.cardId].orEmpty()) }

    fun project(today: LocalDate, exam: LocalDate?, newRemaining: Int, newPerDay: Double): Projection {
        val daysLeft = exam?.let { ChronoUnit.DAYS.between(today, it).toInt() }
        val intro: Int? = when {
            newRemaining <= 0 -> 0
            newPerDay <= 0.0 -> null
            else -> ceil(COVER_FRACTION * newRemaining / newPerDay - 1e-9).toInt()
        }
        val ready = intro?.let { today.plusDays((it + MATURE_DAYS).toLong()) }
        val slack = if (daysLeft != null && intro != null) daysLeft - (intro + MATURE_DAYS) else null
        val window = daysLeft?.minus(MATURE_DAYS)
        val required = when {
            daysLeft == null -> null
            newRemaining <= 0 -> 0.0
            window == null || window <= 0 -> null
            else -> Math.round(COVER_FRACTION * newRemaining * 10.0 / window) / 10.0
        }
        return Projection(today, exam, daysLeft, newRemaining, newPerDay, intro, ready, slack, required)
    }
}
