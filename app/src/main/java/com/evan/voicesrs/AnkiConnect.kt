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
    val cardId: Long = 0
)

/**
 * Minimal AnkiConnect (v6) client over HttpURLConnection.
 * Every call is a POST of {"action","version":6,"key","params"}.
 * All methods block; call off the main thread.
 */
class AnkiConnect(private val baseUrl: String, private val apiKey: String) {

    companion object {
        private const val TAG = "AnkiConnect"
        private const val VERSION = 6
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
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
            if (!resp.isNull("error")) throw AnkiConnectException("AnkiConnect refused the request")
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
        return AnkiCard(
            questionHtml = o.optString("question", ""),
            answerHtml = o.optString("answer", ""),
            deckName = o.optString("deckName", ""),
            buttons = parseButtons(o.optJSONArray("buttons")),
            cardId = o.optLong("cardId", 0L)
        )
    }

    fun preflight(deck: String): PreflightReport {
        return try {
            ConnectionPreflight.evaluate(version(), deckNames(), deck, null)
        } catch (e: Exception) {
            ConnectionPreflight.evaluate(null, null, deck, e.message)
        }
    }

    /** Due and new card ids, capped at [limit]. */
    fun dueCardIds(deckName: String, limit: Int): List<Long> {
        val r = post(
            "findCards",
            mapOf("query" to "deck:\"$deckName\" (is:due OR is:new)")
        ) as? JSONArray ?: return emptyList()
        val n = minOf(r.length(), limit.coerceAtLeast(0))
        val ids = ArrayList<Long>(n)
        for (i in 0 until n) {
            val id = r.optLong(i, 0L)
            if (id != 0L) ids += id
        }
        return ids
    }

    fun cardsInfo(ids: List<Long>): List<OfflineCard> {
        if (ids.isEmpty()) return emptyList()
        val r = post("cardsInfo", mapOf("cards" to ids)) as? JSONArray ?: return emptyList()
        val out = ArrayList<OfflineCard>(r.length())
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val id = o.optLong("cardId", 0L)
            if (id == 0L) continue
            out += OfflineCard(
                cardId = id,
                front = o.optString("question", ""),
                back = o.optString("answer", ""),
                buttons = listOf(1, 2, 3, 4),
                noteId = noteIdOf(o)
            )
        }
        return out
    }

    /** @return one boolean per grade, true when Anki accepted it. */
    fun answerCards(grades: List<PendingGrade>): List<Boolean> {
        if (grades.isEmpty()) return emptyList()
        val answers = grades.map { mapOf("cardId" to it.cardId, "ease" to it.ease) }
        val r = post("answerCards", mapOf("answers" to answers)) as? JSONArray
            ?: return List(grades.size) { false }
        return List(grades.size) { index -> index < r.length() && r.optBoolean(index, false) }
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

    /** Number of cards due now in the named deck. */
    fun dueCount(deckName: String): Int {
        val r = post("findCards", mapOf("query" to "deck:\"$deckName\" (is:due OR is:new)"))
        return (r as? JSONArray)?.length() ?: 0
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
