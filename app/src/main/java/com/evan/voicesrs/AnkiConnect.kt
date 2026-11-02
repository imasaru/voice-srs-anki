package com.evan.voicesrs

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AnkiConnectException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Card as returned by AnkiConnect's guiCurrentCard. */
data class AnkiCard(
    val questionHtml: String,
    val answerHtml: String,
    val deckName: String,
    /** Ease values the reviewer currently offers (usually [1,2,3,4]; new/learning cards may offer fewer). */
    val buttons: List<Int>,
    val cardId: Long = 0,
    /** Note Correct field. Blank when the model has no such field. */
    val correct: String = "",
    val noteId: Long? = null,
    val memoFront: String = "",
    val memoBack: String = "",
    val memoAvailable: Boolean = false,
    val memoHint: Boolean = false
)

/**
 * Minimal AnkiConnect (v6) client over HttpURLConnection.
 * Every call is a POST of {"action","version":6,"key","params"}.
 * All methods block; call off the main thread.
 */
class AnkiConnect(private val baseUrl: String, private val apiKey: String) : StudyAnki {

    companion object {
        private const val TAG = "AnkiConnect"
        private const val VERSION = 6
        /** Tag applied by "Flag card" (h121b) to mark a note for follow-up review. */
        const val FLAG_TAG = "voice-srs-flag"

        /** Payload for addTags: the note ids plus the [FLAG_TAG] tag. */
        fun addTagsParams(notes: List<Long>): Map<String, Any?> =
            mapOf("notes" to notes, "tags" to FLAG_TAG)
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /** Best-effort readable form of an AnkiConnect error response (string or list). */
        private fun errorText(resp: JSONObject): String {
            val err = resp.opt("error")
            val raw = when (err) {
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until err.length()) {
                        val v = err.opt(i)
                        if (v is String) {
                            if (sb.isNotEmpty()) sb.append("; ")
                            sb.append(v)
                        }
                    }
                    sb.toString()
                }
                is String -> err
                else -> "AnkiConnect refused the request"
            }
            return if (raw.isBlank()) "AnkiConnect refused the request" else raw
        }
    }

    private fun post(action: String, params: Map<String, Any?> = emptyMap()): Any? {
        val payload = JSONObject().apply {
            put("action", action)
            put("version", VERSION)
            put("key", apiKey)
            put("params", JSONObject(params))
        }.toString()
        val conn = (URL(baseUrl.trimEnd('/'))).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.readBytes()?.decodeToString() ?: ""
            if (code !in 200..299) throw AnkiConnectException("HTTP $code from AnkiConnect")
            val resp = JSONObject(text)
            if (!resp.isNull("error")) throw AnkiConnectException(errorText(resp))
            return if (resp.isNull("result")) null else resp.get("result")
        } catch (e: AnkiConnectException) {
            throw e
        } catch (e: Exception) {
            throw AnkiConnectException("Could not reach AnkiConnect at $baseUrl (${e.message})", e)
        } finally {
            conn.disconnect()
        }
    }

    fun sync() {
        post("sync")
    }

    fun version(): Int {
        val r = post("version")
        return (r as? Number)?.toInt()
            ?: throw AnkiConnectException("unexpected version response: $r")
    }

    fun deckNames(): List<String> {
        val r = post("deckNames") as? JSONArray
            ?: throw AnkiConnectException("unexpected deckNames response")
        return List(r.length()) { r.optString(it) }
    }

    fun guiDeckReview(deckName: String) {
        post("guiDeckReview", mapOf("name" to deckName))
    }

    fun guiShowQuestion() {
        post("guiShowQuestion")
    }

    fun guiShowAnswer() {
        post("guiShowAnswer")
    }

    fun guiAnswerCard(ease: Int) {
        require(ease in 1..4)
        post("guiAnswerCard", mapOf("ease" to ease))
    }

    fun guiUndo() {
        post("guiUndo")
    }

    /**
     * The card currently shown in the reviewer, or null when the queue is
     * empty / not in review mode (AnkiConnect returns false).
     */
    fun guiCurrentCard(): AnkiCard? {
        val r = try {
            post("guiCurrentCard")
        } catch (e: AnkiConnectException) {
            if (e.message?.contains("not currently active") == true) return null
            throw e
        } ?: return null
        if (r == JSONObject.NULL || r == false) return null
        val o = r as JSONObject
        val fields = o.optJSONObject("fields")
        val memos = CardMemo.of(fields)
        return AnkiCard(
            questionHtml = o.optString("question", ""),
            answerHtml = o.optString("answer", ""),
            deckName = o.optString("deckName", ""),
            buttons = parseButtons(o.optJSONArray("buttons")),
            cardId = o.optLong("cardId", 0L),
            correct = AnkiFields.correct(fields),
            noteId = noteIdOf(o),
            memoFront = memos.front,
            memoBack = memos.back,
            memoAvailable = memos.available,
            memoHint = memos.showHint
        )
    }

    fun preflight(deck: String): PreflightReport {
        return try {
            ConnectionPreflight.evaluate(version(), deckNames(), deck, null)
        } catch (e: Exception) {
            ConnectionPreflight.evaluate(null, null, deck, e.message)
        }
    }

    /** Card ids matching an Anki search, uncapped. */
    fun findCardIds(query: String): List<Long> {
        val r = post("findCards", mapOf("query" to query)) as? JSONArray ?: return emptyList()
        val ids = ArrayList<Long>(r.length())
        for (i in 0 until r.length()) {
            val id = r.optLong(i, 0L)
            if (id != 0L) ids += id
        }
        return ids
    }

    /** Due and new card ids, capped at [limit]. */
    fun dueCardIds(deckName: String, limit: Int): List<Long> {
        val ids = findCardIds("deck:\"$deckName\" (is:due OR is:new)")
        if (limit <= 0) return emptyList()
        return ids.take(limit)
    }

    /**
     * Reviews for [deck] and every subdeck, after [startId] (exclusive).
     * AnkiConnect's `cardReviews` only covers the exact deck it is given, so
     * this lists all decks, keeps the ones matching [deck] (or a subdeck of
     * it), unions their rows, and sorts by [StudyReview.atMs]. It throws on
     * AnkiConnect failure so callers can surface the error.
     */
    override fun cardReviewsTree(deck: String, startId: Long): List<StudyReview> {
        val names = deckNames()
        val out = ArrayList<StudyReview>()
        for (name in names) {
            if (!StudyTools.deckMatches(name, deck)) continue
            out.addAll(cardReviews(name, startId).map { it.copy(deck = name) })
        }
        return out.sortedBy { it.atMs }
    }

    /**
     * Reviews after [startId] (exclusive). Each row is a 9-tuple from revlog.
     * Returns an empty list only when the add-on returns no result; a
     * connection failure throws so callers can surface the error.
     */
    fun cardReviews(deck: String, startId: Long): List<StudyReview> {
        val r = post("cardReviews", mapOf("deck" to deck, "startID" to startId)) as? JSONArray
            ?: return emptyList()
        return StudyTools.parseCardReviews(r)
    }

    override fun noteTags(ids: List<Long>): Map<Long, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        val r = try {
            post("notesInfo", mapOf("notes" to ids)) as? JSONArray
        } catch (_: Exception) {
            null
        } ?: return emptyMap()
        val out = LinkedHashMap<Long, List<String>>()
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val id = o.optLong("noteId", 0L)
            if (id == 0L) continue
            val tags = o.optJSONArray("tags")
            val list = ArrayList<String>()
            if (tags != null) {
                for (t in 0 until tags.length()) {
                    val s = tags.optString(t).trim()
                    if (s.isNotEmpty()) list += s
                }
            }
            out[id] = list
        }
        return out
    }

    override fun reportFields(ids: List<Long>): Map<Long, ReportFields> {
        if (ids.isEmpty()) return emptyMap()
        val r = post("cardsInfo", mapOf("cards" to ids)) as? JSONArray ?: return emptyMap()
        val out = LinkedHashMap<Long, ReportFields>()
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            if (id == 0L) continue
            out[id] = ReportData.fieldsOf(o)
        }
        return out
    }

    override fun cardsInfo(ids: List<Long>): List<OfflineCard> {
        if (ids.isEmpty()) return emptyList()
        val r = post("cardsInfo", mapOf("cards" to ids)) as? JSONArray ?: return emptyList()
        val out = ArrayList<OfflineCard>(r.length())
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            if (id == 0L) continue
            val fields = o.optJSONObject("fields")
            val memos = CardMemo.of(fields)
            out += OfflineCard(
                cardId = id,
                front = o.optString("question", ""),
                back = o.optString("answer", ""),
                buttons = listOf(1, 2, 3, 4),
                noteId = noteIdOf(o),
                correct = AnkiFields.correct(fields),
                memoFront = memos.front,
                memoBack = memos.back,
                memoAvailable = memos.available,
                memoHint = memos.showHint
            )
        }
        return out
    }

    /**
     * A snapshot of every card matching [query]: type, queue, interval, lapses,
     * deck and note tags. Read-only (findCards + cardsInfo).
     */
    override fun cardSnaps(query: String): List<CardSnap> {
        val ids = findCardIds(query)
        if (ids.isEmpty()) return emptyList()
        val r = try {
            post("cardsInfo", mapOf("cards" to ids)) as? JSONArray
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        val noteOf = HashMap<Long, Long>()
        val snaps = ArrayList<CardSnap>(r.length())
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            if (id == 0L) continue
            val noteId = noteIdOf(o)
            if (noteId != null) noteOf[id] = noteId
            snaps += CardSnap(
                cardId = id,
                deck = o.optString("deckName", ""),
                type = o.optInt("type", 0),
                queue = o.optInt("queue", 0),
                ivl = o.optInt("ivl", 0),
                lapses = o.optInt("lapses", 0)
            )
        }
        val tags = try {
            if (noteOf.isEmpty()) emptyMap() else noteTags(noteOf.values.distinct())
        } catch (_: Exception) {
            emptyMap()
        }
        // noteTags is keyed by note id, so map each card through its note.
        return snaps.map { it.copy(tags = noteOf[it.cardId]?.let { n -> tags[n] }.orEmpty()) }
    }

    /**
     * Reviews for [deck] and every subdeck as [RevRow] (keeps lastIvl), after
     * [startMs] (exclusive). Mirrors [cardReviewsTree] but returns the richer
     * row so retention math can use lastIvl.
     */
    override fun revRows(deck: String, startMs: Long): List<RevRow> {
        val names = deckNames()
        val out = ArrayList<RevRow>()
        for (name in names) {
            if (!StudyTools.deckMatches(name, deck)) continue
            out.addAll(cardReviewsRev(name, startMs).map { it.copy(deck = name) })
        }
        return out.sortedBy { it.atMs }
    }

    fun cardReviewsRev(deck: String, startId: Long): List<RevRow> {
        val r = post("cardReviews", mapOf("deck" to deck, "startID" to startId)) as? JSONArray
            ?: return emptyList()
        return StudyTools.parseRevRows(r)
    }

    /**
     * @return one boolean per grade, true when Anki accepted it.
     * Each answer may include [AnswerCardsRequest] `time` (ms). Stock
     * AnkiConnect ignores extra fields and starts the card timer immediately
     * before answering, so revlog.time would be ~0. Answer time is kept
     * locally in [AnswerTimeLog]; the extra `time` field is harmless.
     * If the server rejects the extra field, the grade is retried
     * without it; a duration miss never fails the grade.
     */
    fun answerCards(grades: List<PendingGrade>): List<Boolean> {
        if (grades.isEmpty()) return emptyList()
        return try {
            postAnswerCards(AnswerCardsRequest.body(grades, includeTime = true), grades.size)
        } catch (e: AnkiConnectException) {
            if (!AnswerCardsRequest.timeRejected(e.message) ||
                grades.none { it.durationMs > 0L }
            ) {
                throw e
            }
            Log.w(TAG, "answerCards rejected time; retrying without duration")
            postAnswerCards(AnswerCardsRequest.body(grades, includeTime = false), grades.size)
        }
    }

    private fun postAnswerCards(answers: JSONArray, n: Int): List<Boolean> {
        val r = post("answerCards", mapOf("answers" to answers)) as? JSONArray
            ?: return List(n) { false }
        return List(n) { index -> index < r.length() && r.optBoolean(index, false) }
    }

    fun suspendCards(ids: List<Long>) {
        if (ids.isEmpty()) return
        post("suspend", mapOf("cards" to ids, "suspend" to true))
    }

    fun unsuspendCards(ids: List<Long>) {
        if (ids.isEmpty()) return
        post("unsuspend", mapOf("cards" to ids))
    }

    /** Every card in the deck, for the overnight explanation pass. */
    fun deckCardIds(deckName: String): List<Long> {
        val r = post("findCards", mapOf("query" to "deck:\"$deckName\"")) as? JSONArray
            ?: return emptyList()
        val ids = ArrayList<Long>(r.length())
        for (i in 0 until r.length()) {
            val id = r.optLong(i, 0L)
            if (id != 0L) ids += id
        }
        return ids
    }

    fun cardsToNotes(ids: List<Long>): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val r = post("cardsToNotes", mapOf("cards" to ids)) as? JSONArray ?: return emptyList()
        val out = ArrayList<Long>(r.length())
        for (i in 0 until r.length()) {
            val id = r.optLong(i, 0L)
            if (id != 0L) out += id
        }
        return out
    }

    /**
     * Tag the note behind [cardId] with [FLAG_TAG] ("voice-srs-flag") so it shows in a
     * follow-up review deck. No scheduling change. Returns true when Anki accepted it.
     */
    fun flagCard(cardId: Long): Boolean {
        val notes = cardsToNotes(listOf(cardId))
        if (notes.isEmpty()) return false
        post("addTags", addTagsParams(notes))
        return true
    }

    fun addNote(
        deckName: String,
        modelName: String,
        fields: Map<String, String>,
        tags: List<String>
    ): Long {
        val fieldObj = JSONObject()
        for ((name, value) in fields) fieldObj.put(name, value)
        val tagArr = JSONArray()
        for (tag in tags) tagArr.put(tag)
        val note = JSONObject()
            .put("deckName", deckName)
            .put("modelName", modelName)
            .put("fields", fieldObj)
            .put("tags", tagArr)
        val r = post("addNote", mapOf("note" to note))
        return (r as? Number)?.toLong() ?: 0L
    }

    fun updateNoteFields(noteId: Long, fields: Map<String, String>) {
        if (noteId == 0L || fields.isEmpty()) return
        val fieldObj = JSONObject()
        for ((name, value) in fields) fieldObj.put(name, value)
        val note = JSONObject().put("id", noteId).put("fields", fieldObj)
        post("updateNoteFields", mapOf("note" to note))
    }

    /**
     * Deck counts for the header: [DueCounts.due] = is:due reviews available
     * now, [DueCounts.new] = new cards available today, capped at the deck's
     * new-per-day limit once [DueCounts.newCapped] is set. Read-only.
     * [introducedToday] and [perDay] are null when the backing query or deck
     * config cannot be read, in which case new is reported uncapped.
     */
    fun dueCounts(deckName: String): DueCounts {
        val due = (post("findCards", mapOf("query" to "deck:\"$deckName\" is:due")) as? JSONArray)?.length() ?: 0
        val unseen = (post("findCards", mapOf("query" to "deck:\"$deckName\" is:new")) as? JSONArray)?.length() ?: 0
        val introducedToday = runCatching {
            (post("findCards", mapOf("query" to "deck:\"$deckName\" introduced:1")) as? JSONArray)?.length()
        }.getOrNull()
        val perDay = runCatching {
            getDeckConfig(deckName).optJSONObject("new")?.optInt("perDay")
        }.getOrNull()
        return DueSplit.counts(due, unseen, perDay, introducedToday)
    }

    /** The full preset object for [deck], or throw on an unexpected result. */
    fun getDeckConfig(deck: String): JSONObject {
        val r = post("getDeckConfig", DeckConfigRequest.getDeckConfigParams(deck))
        return (r as? JSONObject)
            ?: throw AnkiConnectException("unexpected getDeckConfig response: $r")
    }

    /** Persist an edited preset object (must carry its "id"). */
    fun saveDeckConfig(config: JSONObject): Boolean {
        val r = post("saveDeckConfig", DeckConfigRequest.saveDeckConfigParams(config))
        return (r as? Boolean)
            ?: throw AnkiConnectException("unexpected saveDeckConfig response: $r")
    }

    /** Create a preset copy, or null when Anki refused it. */
    fun cloneDeckConfigId(name: String, cloneFrom: Long): Long? {
        val r = post("cloneDeckConfigId", DeckConfigRequest.cloneParams(name, cloneFrom))
        return DeckConfigRequest.parseCloneId(r)
    }

    /** Assign [configId] to each deck, or false when Anki refused it. */
    fun setDeckConfigId(decks: List<String>, configId: Long): Boolean {
        val r = post("setDeckConfigId", DeckConfigRequest.setDeckConfigIdParams(decks, configId))
        return (r as? Boolean)
            ?: throw AnkiConnectException("unexpected setDeckConfigId response: $r")
    }

    /** Push [cardIds] out to [days] from today. */
    fun setDueDate(cardIds: List<Long>, days: String): Boolean {
        val r = post("setDueDate", DeckConfigRequest.setDueDateParams(cardIds, days))
        return (r as? Boolean)
            ?: throw AnkiConnectException("unexpected setDueDate response: $r")
    }

    /** Review "due" day numbers (queue 2 or 3) by card id; other cards are left out. */
    fun dueDayNumbers(ids: List<Long>): Map<Long, Int> {
        if (ids.isEmpty()) return emptyMap()
        val r = post("cardsInfo", mapOf("cards" to ids)) as? JSONArray ?: return emptyMap()
        val out = LinkedHashMap<Long, Int>()
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            val q = o.optInt("queue", -99)
            if (id == 0L || (q != 2 && q != 3)) continue
            out[id] = o.optInt("due", 0)
        }
        return out
    }

    /**
     * A media file's bytes as base64, or null when the file does not exist
     * (AnkiConnect returns false) or the result is unexpected.
     */
    fun retrieveMediaFile(filename: String): ByteArray? {
        val r = post("retrieveMediaFile", DeckConfigRequest.retrieveMediaParams(filename))
        return DeckConfigRequest.parseMedia(r)
    }

    private fun noteIdOf(o: JSONObject): Long? {
        val raw = when {
            o.has("note") && !o.isNull("note") -> o.optLong("note", 0L)
            o.has("noteId") && !o.isNull("noteId") -> o.optLong("noteId", 0L)
            else -> 0L
        }
        return if (raw == 0L) null else raw
    }

    private fun parseButtons(arr: JSONArray?): List<Int> {
        if (arr == null) return listOf(1, 2, 3, 4)
        val out = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            val n = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
            if (n != null && n in 1..4) out += n
        }
        return out.ifEmpty { listOf(1, 2, 3, 4) }
    }
}
