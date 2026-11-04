package com.evan.voicesrs

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Report-only card fields from AnkiConnect `cardsInfo`. */
data class ReportFields(val deck: String, val domain: String, val stem: String, val correctText: String)

/** [DumpCard] shape for [DailyReportBuilder]; pure; no I/O. */
object ReportData {
    /** Fill a 0-ms rev's time from the app-local log (see [localMs]). */
    const val MAX_GAP_MS = 120_000L

    /** Fill a rev's time from the app-local log when revlog.time is 0 (stock AnkiConnect arms the timer right before answering). Matches cardId+ease, picks the unused [AnswerTime] whose atMs is closest within [MAX_GAP_MS], marks its index [used] so each is consumed once; 0 on no match. */
    fun localMs(rev: StudyReview, local: List<AnswerTime>, used: MutableSet<Int>): Long {
        if (rev.timeMs != 0L) return 0L
        var bestIdx = -1
        var bestGap = Long.MAX_VALUE
        for (i in local.indices) {
            if (i in used) continue
            val a = local[i]
            if (a.cardId != rev.cardId || a.ease != rev.ease) continue
            val gap = kotlin.math.abs(a.atMs - rev.atMs)
            if (gap <= MAX_GAP_MS && gap < bestGap) { bestGap = gap; bestIdx = i }
        }
        if (bestIdx < 0) return 0L
        used.add(bestIdx)
        return local[bestIdx].durationMs
    }

    /** One card per id, first-review order; revs oldest first, time as local "HH:mm:ss". */
    fun dumpCards(rows: List<StudyReview>, fields: Map<Long, ReportFields>, tz: TimeZone, local: List<AnswerTime> = emptyList()): List<DumpCard> {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tz }
        val used = HashSet<Int>()
        val byCard = LinkedHashMap<Long, MutableList<StudyReview>>()
        for (r in rows.sortedBy { it.atMs }) byCard.getOrPut(r.cardId) { mutableListOf() }.add(r)
        return byCard.map { (cid, revs) ->
            val f = fields[cid]
            val deck = f?.deck?.takeIf { it.isNotBlank() } ?: revs.first().deck
            DumpCard(
                cid = cid,
                deck = deck,
                domain = f?.domain?.takeIf { it.isNotBlank() } ?: practiceDomain(deck),
                stem = f?.stem.orEmpty(),
                correctText = f?.correctText.orEmpty(),
                revs = revs.map { DumpRev(fmt.format(Date(it.atMs)), it.ease, it.type, 0, if (it.timeMs != 0L) it.timeMs else localMs(it, local, used)) }
            )
        }
    }

    /** "…::Practice::01 Databricks Lakehouse Platform" → "Databricks Lakehouse Platform"; else blank. */
    fun practiceDomain(deck: String): String {
        val parts = deck.split("::").map { it.trim() }
        val i = parts.indexOfLast { it.equals("Practice", ignoreCase = true) }
        if (i < 0 || i + 1 >= parts.size) return ""
        return parts[i + 1].replace(Regex("^\\d+\\s+"), "")
    }

    /** One `cardsInfo` entry. Stem falls back to the rendered question; CorrectText to Correct. */
    fun fieldsOf(o: JSONObject): ReportFields {
        val fields = o.optJSONObject("fields")
        val stem = AnkiText.stripHtml(AnkiFields.value(fields, "Stem")).ifBlank {
            AnkiText.stripHtml(o.optString("question", ""))
        }
        val correct = AnkiText.stripHtml(AnkiFields.value(fields, "CorrectText")).ifBlank {
            AnkiFields.correct(fields)
        }
        return ReportFields(
            deck = o.optString("deckName", ""),
            domain = AnkiText.stripHtml(AnkiFields.value(fields, "Domain")),
            stem = stem.trim(),
            correctText = correct.trim()
        )
    }
}
