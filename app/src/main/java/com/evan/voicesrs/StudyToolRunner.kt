package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * The slice of AnkiConnect that [StudyToolRunner] uses, exposed as an
 * interface so tests can supply a fake without a network. [cardReviewsTree]
 * returns reviews for [deck] and every subdeck (AnkiConnect's `cardReviews`
 * only covers the exact deck it is given).
 */
interface StudyAnki {
    fun cardReviewsTree(deck: String, startId: Long): List<StudyReview>
    fun cardsInfo(ids: List<Long>): List<OfflineCard>
    fun noteTags(ids: List<Long>): Map<Long, List<String>>
    /** Deck, Domain, Stem and CorrectText per card for the daily report. */
    fun reportFields(ids: List<Long>): Map<Long, ReportFields> = emptyMap()
}

/**
 * Runs [StudyTools] against AnkiConnect. cardReviews for the window,
 * cardsInfo for questions, notesInfo for tags. Failures return an error
 * JSON object so a grade or chat turn is not aborted.
 */
class StudyToolRunner(
    private val anki: StudyAnki,
    private val defaultDeck: String,
    private val tz: TimeZone = TimeZone.getDefault(),
    private val pending: () -> List<PendingGrade> = { emptyList() },
    private val timeLog: () -> List<AnswerTime> = { AnswerTimeLog.snapshot() }
) {
    fun invoke(name: String, argsJson: String): String {
        val args = try {
            JSONObject(argsJson.ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
        val from = args.optString("from").ifBlank { StudyTools.today(tz) }
        val to = args.optString("to").ifBlank { from }
        val deck = args.optString("deck").ifBlank { defaultDeck }
        val limit = args.optInt("limit", StudyTools.DEFAULT_LIMIT)
        return try {
            val (rows, cards) = load(from, to, deck)
            buildToolResponse(name, from, to, deck, limit, rows, cards)
        } catch (e: Exception) {
            // Anki unreachable: surface the error but keep the local answer
            // times for the requested range so a grade or chat turn is not aborted.
            JSONObject()
                .put("error", e.javaClass.simpleName)
                .put("answer_time", answerTime(from, to, deck, 0))
                .put("pending_sync", pendingSync(from, to))
                .toString()
        }
    }

    private fun buildToolResponse(
        name: String,
        from: String,
        to: String,
        deck: String,
        limit: Int,
        rows: List<StudyReview>,
        cards: Map<Long, StudyCard>
    ): String = when (name) {
        StudyTools.GET_STUDY_SUMMARY -> StudyTools.summary(rows)
            .put("answer_time", answerTime(from, to, deck, rows.size))
            .put("pending_sync", pendingSync(from, to))
            .toString()
        StudyTools.GET_CATEGORY_STATS -> JSONObject()
            .put("categories", StudyTools.categoryStats(rows, cards))
            .put("answer_time", answerTime(from, to, deck, rows.size))
            .toString()
        StudyTools.GET_MISSED_CARDS -> JSONObject()
            .put("missed", StudyTools.missed(rows, cards, limit))
            .toString()
        else -> JSONObject().put("error", "unknown tool").toString()
    }

    fun answerTime(from: String, to: String, deck: String, reviews: Int): JSONObject =
        StudyTools.answerTime(
            timeLog(),
            StudyTools.dayStartMs(from, tz),
            StudyTools.dayEndMs(to, tz),
            deck,
            reviews
        )

    /** Grades still waiting in the local queue for [from]..[to]. Anki's review
     *  counts miss these, so the report shows them alongside. Any failure is 0
     *  so a report is never aborted by the local read. */
    fun pendingSync(from: String, to: String): Int = try {
        val start = StudyTools.dayStartMs(from, tz)
        val end = StudyTools.dayEndMs(to, tz)
        pending().count { it.queuedAtMs in start..end }
    } catch (_: Exception) {
        0
    }

    fun todayBundle(deck: String): String {
        val day = StudyTools.today(tz)
        val target = deck.ifBlank { defaultDeck }
        return try {
            val (rows, cards) = load(day, day, target)
            JSONObject(StudyTools.formatBundle(
                StudyTools.summary(rows),
                StudyTools.categoryStats(rows, cards),
                StudyTools.missed(rows, cards, StudyTools.DEFAULT_LIMIT)
            )).put("pending_sync", pendingSync(day, day)).toString()
        } catch (e: Exception) {
            // Anki unreachable: surface the error but keep the local answer
            // times for the requested range so today's report is never silent.
            JSONObject()
                .put("error", e.javaClass.simpleName)
                .put("answer_time", answerTime(day, day, target, 0))
                .put("pending_sync", pendingSync(day, day))
                .toString()
        }
    }

    /**
     * Today's full report as markdown from [DailyReportBuilder], for the
     * parent deck and every subdeck, plus "Where understanding broke" from
     * [threadsJson] (thread-history.json). Anki unreachable → an error report,
     * never a zero report.
     */
    fun dailyReport(deck: String, issuedAtMs: Long, threadsJson: String = ""): String {
        val day = StudyTools.today(tz)
        val target = deck.ifBlank { defaultDeck }
        val start = StudyTools.dayStartMs(day, tz)
        val end = StudyTools.dayEndMs(day, tz)
        val issued = java.text.SimpleDateFormat("HH:mm z", java.util.Locale.US).apply { timeZone = tz }.format(issuedAtMs)
        val pending = pendingSync(day, day)
        return try {
            val rows = anki.cardReviewsTree(target, start - 1L).filter { it.atMs in start..end }
            val ids = rows.map { it.cardId }.distinct()
            val fields = try {
                anki.reportFields(ids)
            } catch (_: Exception) {
                emptyMap()
            }
            val cards = ReportData.dumpCards(rows, fields, tz)
            val labels = cards.associate { it.cid to it.stem }
            val broke = if (threadsJson.isBlank()) "" else UnderstandingInsights.whereBroke(threadsJson, start, end, labels)
            val md = DailyReportBuilder.render(DailyReportBuilder.build(day, cards), issued, broke)
            if (pending > 0) "$md\nNote: $pending grades are queued offline and not yet in Anki, so they are not counted above.\n" else md
        } catch (e: Exception) {
            "# Daily study report — $day · issued $issued\n" +
                "Error: could not read reviews from Anki (${e.javaClass.simpleName}). These are not zero reviews.\n" +
                (if (pending > 0) "$pending grades are queued offline.\n" else "")
        }
    }

    fun load(from: String, to: String, deck: String): Pair<List<StudyReview>, Map<Long, StudyCard>> {
        val start = StudyTools.dayStartMs(from, tz)
        val end = StudyTools.dayEndMs(to, tz)
        val raw = anki.cardReviewsTree(deck, start - 1L)
        val rows = raw.filter { it.atMs in start..end }
        val ids = rows.map { it.cardId }.distinct()
        val info = if (ids.isEmpty()) emptyList() else anki.cardsInfo(ids)
        val noteIds = info.mapNotNull { it.noteId }.distinct()
        val tagsByNote = try {
            if (noteIds.isEmpty()) emptyMap() else anki.noteTags(noteIds)
        } catch (_: Exception) {
            emptyMap()
        }
        val cards = LinkedHashMap<Long, StudyCard>()
        for (card in info) {
            val tags = card.noteId?.let { tagsByNote[it] }.orEmpty()
            cards[card.cardId] = StudyCard(card.cardId, tags, card.front)
        }
        for (id in ids) {
            if (id !in cards) cards[id] = StudyCard(id, emptyList(), "")
        }
        return rows to cards
    }
}
