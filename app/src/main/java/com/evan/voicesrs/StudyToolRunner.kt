package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
    /** Every card matching [query] (type, queue, interval, lapses, deck, note tags). */
    fun cardSnaps(query: String): List<CardSnap> = emptyList()
    /** Reviews for [deck] and every subdeck as [RevRow] (keeps lastIvl), after [startMs]. */
    fun revRows(deck: String, startMs: Long): List<RevRow> = emptyList()
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
    private val examDate: () -> LocalDate? = { null },
    private val timeLog: () -> List<AnswerTime> = { AnswerTimeLog.snapshot() }
) {
    private val zone: ZoneId = ZoneId.of(tz.id)
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
        // Exam readiness doesn't use a from/to range; it reads a rolling window.
        if (name == StudyTools.GET_EXAM_READINESS) {
            return examReadiness(args)
        }
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
    fun dailyReport(deck: String, issuedAtMs: Long, threadsJson: String = "", blockHistory: String = ""): String {
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
            val md = DailyReportBuilder.render(DailyReportBuilder.build(day, cards), issued, broke, StudyBlockStore.reportLines(blockHistory, day))
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

    /**
     * get_exam_readiness: a rolling review window (no from/to). Reads card
     * states, true retention, pace, weakest subdecks/tags and the projected
     * ready date vs the exam date. All numbers are computed by
     * [examReadinessJson]; the model only explains them.
     */
    private fun examReadiness(args: JSONObject): String {
        val deck = args.optString("deck").ifBlank { defaultDeck }
        val windowDays = args.optInt("window_days", StudyTools.EXAM_WINDOW_DEFAULT)
            .coerceIn(StudyTools.EXAM_WINDOW_MIN, StudyTools.EXAM_WINDOW_MAX)
        return try {
            val today = AnkiDay.today(zone)
            val exam = runCatching { examDate() }.getOrNull()
            val query = deckQuery(deck)
            val cards = anki.cardSnaps(query)
            val rows = anki.revRows(deck, AnkiDay.startMs(today.minusDays(windowDays - 1L), zone))
            val tags = cards.associate { it.cardId to it.tags }
            examReadinessJson(today, exam, cards, rows, tags, windowDays, zone).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.javaClass.simpleName).toString()
        }
    }

    /**
     * Exam-readiness forecast JSON for [defaultDeck] over a rolling
     * [StudyTools.EXAM_WINDOW_DEFAULT]-day window. Reuses [examReadinessJson]
     * (no maths duplicated); never throws.
     */
    fun readinessJson(): String = try {
        val today = AnkiDay.today(zone)
        val exam = runCatching { examDate() }.getOrNull()
        val query = deckQuery(defaultDeck)
        val cards = anki.cardSnaps(query)
        val rows = anki.revRows(
            defaultDeck,
            AnkiDay.startMs(today.minusDays(StudyTools.EXAM_WINDOW_DEFAULT - 1L), zone)
        )
        val tags = cards.associate { it.cardId to it.tags }
        examReadinessJson(today, exam, cards, rows, tags, StudyTools.EXAM_WINDOW_DEFAULT, zone).toString()
    } catch (e: Exception) {
        JSONObject().put("error", e.javaClass.simpleName).toString()
    }

    private fun deckQuery(deck: String): String = "deck:\"$deck\""

    /**
     * Pure exam-readiness forecast over [cards] and review [rows] for a
     * [windowDays]-day window ending at [today]. [exam] is the target date
     * (nullable: everything still computes, with exam_date/days_left null).
     */
    internal fun examReadinessJson(
        today: LocalDate,
        exam: LocalDate?,
        cards: List<CardSnap>,
        rows: List<RevRow>,
        tags: Map<Long, List<String>>,
        windowDays: Int,
        zone: ZoneId
    ): JSONObject {
        val start = AnkiDay.startMs(today.minusDays(windowDays - 1L), zone)
        val end = AnkiDay.startMs(today.plusDays(1), zone)
        val s = ReadinessStats.states(cards)
        val ret = ReadinessStats.retention(rows, start, end)
        val pace = ReadinessStats.pace(rows, start, end, windowDays)
        val newRemaining = s.new
        val proj = ReadinessStats.project(today, exam, newRemaining, pace.newPerDay)
        return JSONObject()
            .put("exam_date", exam?.toString())
            .put("days_left", proj.daysLeft)
            .put("states", JSONObject()
                .put("new", s.new)
                .put("learning", s.learning)
                .put("young", s.young)
                .put("mature", s.mature)
                .put("suspended", s.suspended)
                .put("total", s.total))
            .put("retention", JSONObject()
                .put("pct", ret.pct)
                .put("young_pct", ret.youngPct)
                .put("mature_pct", ret.maturePct)
                .put("reviews", ret.total))
            .put("pace", JSONObject()
                .put("days", pace.days)
                .put("new_per_day", pace.newPerDay)
                .put("reviews_per_day", pace.reviewsPerDay))
            .put("weakest_decks", topLapses(ReadinessStats.lapsesByDeck(rows), 5))
            .put("weakest_tags", topLapses(ReadinessStats.lapsesByTag(rows, tags), 5))
            .put("projection", JSONObject()
                .put("days_to_introduce", proj.daysToIntroduce)
                .put("ready_date", proj.readyDate?.toString())
                .put("slack_days", proj.slackDays)
                .put("on_track", proj.onTrack)
                .put("required_new_per_day", proj.requiredNewPerDay))
            .put("assumptions", JSONArray(listOf(
                "90% of new cards introduced",
                "mature = interval >= 21 days",
                "32 days from first learn to mature",
                "Anki day rolls over at 4:00"
            )))
    }

    private fun topLapses(rows: List<LapseRow>, n: Int): JSONArray {
        val arr = JSONArray()
        for (r in rows.take(n)) {
            arr.put(JSONObject()
                .put("name", r.name)
                .put("lapses", r.lapses)
                .put("reviews", r.reviews)
                .put("pct", r.pct))
        }
        return arr
    }
}
