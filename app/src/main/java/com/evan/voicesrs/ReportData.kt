package com.evan.voicesrs

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Report-only card fields from AnkiConnect `cardsInfo`. */
data class ReportFields(val deck: String, val domain: String, val stem: String, val correctText: String)

/**
 * Turns the reviews the app already fetches (parent deck + subdecks) into the
 * [DumpCard] shape [DailyReportBuilder] reads. Pure; no I/O.
 */
object ReportData {
    /** One card per id, in first-review order; revs oldest first, time as local "HH:mm:ss". */
    fun dumpCards(rows: List<StudyReview>, fields: Map<Long, ReportFields>, tz: TimeZone): List<DumpCard> {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tz }
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
                revs = revs.map { DumpRev(fmt.format(Date(it.atMs)), it.ease, it.type, 0, it.timeMs) }
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
