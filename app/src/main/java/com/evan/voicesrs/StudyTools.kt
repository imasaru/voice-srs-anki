package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One Anki revlog row in a date window. [timeMs] 0 means unknown (older days). */
data class StudyReview(
    val atMs: Long,
    val cardId: Long,
    val ease: Int,
    val timeMs: Long,
    /** revlog type (0 learn, 1 review, 2 relearn, 3 filtered); -1 when unknown. */
    val type: Int = -1,
    /** Deck the review was fetched from; blank when unknown. */
    val deck: String = ""
)

data class StudyCard(
    val cardId: Long,
    val tags: List<String>,
    val question: String
)

/**
 * OpenAI function-calling schemas and pure aggregation over review rows.
 * Categories are card tags, skipping Anki internals (marked, leech, _*).
 */
object StudyTools {
    const val UNAVAILABLE = "unavailable"
    const val MAX_ROUNDS = 4
    const val DEFAULT_LIMIT = 10
    const val MAX_LIMIT = 50
    const val GET_STUDY_SUMMARY = "get_study_summary"
    const val GET_CATEGORY_STATS = "get_category_stats"
    const val GET_MISSED_CARDS = "get_missed_cards"

    private val SKIP_TAGS = setOf("marked", "leech")

    fun schemas(): JSONArray {
        val arr = JSONArray()
        arr.put(fn(GET_STUDY_SUMMARY, "Cards, reviews, ease counts, accuracy, and study time for a date range.", rangeParams()))
        arr.put(fn(GET_CATEGORY_STATS, "Per-category question counts, first-try percent, and all-review percent.", rangeParams()))
        arr.put(
            fn(
                GET_MISSED_CARDS,
                "Cards whose last review in the range was Again, with category and a short question.",
                rangeParams().put(
                    "limit",
                    JSONObject()
                        .put("type", "integer")
                        .put("description", "Max cards to return (default 10, max 50).")
                )
            )
        )
        return arr
    }

    fun names(): Set<String> = setOf(GET_STUDY_SUMMARY, GET_CATEGORY_STATS, GET_MISSED_CARDS)

    fun today(tz: TimeZone = TimeZone.getDefault(), nowMs: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = tz
        return fmt.format(nowMs)
    }

    fun dayStartMs(date: String, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = parseDay(date, tz) ?: return 0L
        return cal.timeInMillis
    }

    fun dayEndMs(date: String, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = parseDay(date, tz) ?: return 0L
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis - 1L
    }

    /** AnkiConnect `cardReviews` rows: id, cid, usn, ease, ivl, lastIvl, factor, time, type. */
    fun parseCardReviews(arr: JSONArray): List<StudyReview> {
        val out = ArrayList<StudyReview>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONArray(i) ?: continue
            val at = row.optLong(0, 0L)
            val cid = row.optLong(1, 0L)
            if (at == 0L || cid == 0L) continue
            out += StudyReview(at, cid, row.optInt(3, 0), row.optLong(7, 0L), row.optInt(8, -1))
        }
        return out
    }

    fun inRange(rows: List<StudyReview>, from: String, to: String, tz: TimeZone = TimeZone.getDefault()): List<StudyReview> {
        val start = dayStartMs(from, tz)
        val end = dayEndMs(to, tz)
        if (start <= 0L || end <= 0L) return emptyList()
        return rows.filter { it.atMs in start..end }
    }

    fun categories(tags: List<String>): List<String> {
        val cats = tags.map { it.trim() }.filter { tag ->
            tag.isNotEmpty() && tag.lowercase(Locale.US) !in SKIP_TAGS && !tag.startsWith("_")
        }
        return if (cats.isEmpty()) listOf("Untagged") else cats
    }

    fun shortQuestion(raw: String, max: Int = 80): String {
        val line = AnkiText.stripHtml(raw).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        if (line.length <= max) return line
        return line.take(max - 1).trimEnd() + "…"
    }

    fun summary(rows: List<StudyReview>): JSONObject {
        val cards = rows.map { it.cardId }.toSet().size
        var again = 0
        var hard = 0
        var good = 0
        var easy = 0
        var knownMs = 0L
        var timed = 0
        for (row in rows) {
            when (row.ease) {
                1 -> again++
                2 -> hard++
                3 -> good++
                4 -> easy++
            }
            if (row.timeMs > 0L) {
                knownMs += row.timeMs
                timed++
            }
        }
        val reviews = rows.size
        val accuracy = if (reviews == 0) 0.0 else (good + easy).toDouble() / reviews * 100.0
        val obj = JSONObject()
            .put("cards", cards)
            .put("reviews", reviews)
            .put("again", again)
            .put("hard", hard)
            .put("good", good)
            .put("easy", easy)
            .put("accuracy", round1(accuracy))
        if (reviews == 0 || timed == 0) {
            obj.put("study_time", UNAVAILABLE)
        } else {
            obj.put("study_time_ms", knownMs)
            obj.put("study_time", formatMs(knownMs))
        }
        return obj
    }

    const val TIME_UNAVAILABLE_NOTE =
        "Answer time unavailable for this range: Voice SRS only logs answer time locally " +
            "since v0.1.21, so older reviews (and reviews done outside the app) have no time data."

    /** True when [deck] is [wanted] or one of its subdecks. Blank [wanted] matches all. */
    fun deckMatches(deck: String, wanted: String): Boolean {
        val w = wanted.trim()
        if (w.isEmpty()) return true
        return deck == w || deck.startsWith("$w::")
    }

    /**
     * Total and average answer time from the local [AnswerTimeLog] for the
     * window and deck. [reviews] is Anki's review count for the same window,
     * used to say how much of it has time data.
     */
    fun answerTime(
        log: List<AnswerTime>,
        startMs: Long,
        endMs: Long,
        deck: String,
        reviews: Int
    ): JSONObject {
        val timed = log.filter {
            it.atMs in startMs..endMs && it.durationMs > 0L && deckMatches(it.deck, deck)
        }
        val obj = JSONObject()
        if (timed.isEmpty()) {
            return obj.put("answer_time", UNAVAILABLE)
                .put("timed_reviews", 0)
                .put("note", TIME_UNAVAILABLE_NOTE)
        }
        val total = timed.sumOf { it.durationMs }
        val avg = total / timed.size
        obj.put("timed_reviews", timed.size)
            .put("total_answer_time_ms", total)
            .put("total_answer_time", formatMs(total))
            .put("avg_answer_time_ms", avg)
            .put("avg_answer_time", formatSec(avg))
        if (reviews > timed.size) {
            obj.put(
                "note",
                "Only ${timed.size} of $reviews reviews in this range have answer time; " +
                    "older reviews or ones done outside Voice SRS have no time data."
            )
        }
        return obj
    }

    fun categoryStats(rows: List<StudyReview>, cards: Map<Long, StudyCard>): JSONArray {
        val first = firstReviews(rows)
        val byCat = LinkedHashMap<String, CatAcc>()
        fun bucket(name: String): CatAcc = byCat.getOrPut(name) { CatAcc() }
        val cardIds = rows.map { it.cardId }.toSet()
        for (id in cardIds) {
            val cats = categories(cards[id]?.tags.orEmpty())
            val firstOk = first[id]?.let { it.ease >= 3 } == true
            for (cat in cats) {
                val acc = bucket(cat)
                acc.questions += 1
                if (firstOk) acc.firstGood += 1
            }
        }
        for (row in rows) {
            val cats = categories(cards[row.cardId]?.tags.orEmpty())
            for (cat in cats) {
                val acc = bucket(cat)
                acc.reviews += 1
                if (row.ease >= 3) acc.reviewGood += 1
            }
        }
        val arr = JSONArray()
        for ((name, acc) in byCat) {
            arr.put(
                JSONObject()
                    .put("category", name)
                    .put("questions", acc.questions)
                    .put("first_try_pct", pct(acc.firstGood, acc.questions))
                    .put("all_review_pct", pct(acc.reviewGood, acc.reviews))
            )
        }
        return arr
    }

    fun missed(
        rows: List<StudyReview>,
        cards: Map<Long, StudyCard>,
        limit: Int = DEFAULT_LIMIT
    ): JSONArray {
        val cap = limit.coerceIn(1, MAX_LIMIT)
        val last = LinkedHashMap<Long, StudyReview>()
        for (row in rows.sortedBy { it.atMs }) last[row.cardId] = row
        val arr = JSONArray()
        for ((id, row) in last) {
            if (row.ease != 1) continue
            if (arr.length() >= cap) break
            val meta = cards[id]
            val cat = categories(meta?.tags.orEmpty()).first()
            arr.put(
                JSONObject()
                    .put("cardId", id)
                    .put("category", cat)
                    .put("question", shortQuestion(meta?.question.orEmpty()))
            )
        }
        return arr
    }

    fun formatBundle(summary: JSONObject, categories: JSONArray, missed: JSONArray): String {
        return JSONObject()
            .put("summary", summary)
            .put("categories", categories)
            .put("missed", missed)
            .toString()
    }

    /** HTTP 400 that mentions tools, so the model cannot take the schema. */
    fun toolsRejected(code: Int, body: String?): Boolean {
        if (code != 400) return false
        val m = body.orEmpty().lowercase(Locale.US)
        return m.contains("tool")
    }

    /** True when we offered tools and the model never called one. */
    fun needsFallbackChip(offeredTools: Boolean, sawToolCalls: Boolean, rejected: Boolean): Boolean {
        if (!offeredTools) return false
        return rejected || !sawToolCalls
    }

    fun looksLikeStudyAsk(question: String): Boolean {
        val t = question.trim().lowercase(Locale.US)
        if (t.isEmpty()) return false
        val keys = listOf(
            "today", "report", "accuracy", "missed", "study time", "progress",
            "how did i", "how am i", "stats", "category", "this week"
        )
        return keys.any { it in t }
    }

    private fun firstReviews(rows: List<StudyReview>): Map<Long, StudyReview> {
        val first = LinkedHashMap<Long, StudyReview>()
        for (row in rows.sortedBy { it.atMs }) {
            if (row.cardId !in first) first[row.cardId] = row
        }
        return first
    }

    private fun pct(num: Int, den: Int): Double {
        if (den <= 0) return 0.0
        return round1(num.toDouble() / den * 100.0)
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    private fun formatMs(ms: Long): String {
        val min = (ms + 30_000L) / 60_000L
        return if (min <= 0L) "${ms / 1000L} sec" else "$min min"
    }

    private fun formatSec(ms: Long): String = "${round1(ms / 1000.0)} sec"

    private fun parseDay(date: String, tz: TimeZone): Calendar? {
        val parts = date.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.YEAR, y)
        cal.set(Calendar.MONTH, m - 1)
        cal.set(Calendar.DAY_OF_MONTH, d)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun rangeParams(): JSONObject {
        return JSONObject()
            .put("from", JSONObject().put("type", "string").put("description", "Start date YYYY-MM-DD inclusive."))
            .put("to", JSONObject().put("type", "string").put("description", "End date YYYY-MM-DD inclusive."))
            .put("deck", JSONObject().put("type", "string").put("description", "Anki deck name."))
    }

    private fun fn(name: String, description: String, properties: JSONObject): JSONObject {
        val required = JSONArray().put("from").put("to").put("deck")
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put("properties", properties)
                            .put("required", required)
                    )
            )
    }

    private class CatAcc {
        var questions = 0
        var firstGood = 0
        var reviews = 0
        var reviewGood = 0
    }
}
