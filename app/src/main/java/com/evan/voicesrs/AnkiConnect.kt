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
    val buttons: List<Int>
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
            if (code !in 200..299) throw AnkiConnectException("HTTP $code from AnkiConnect: $text")
            val resp = JSONObject(text)
            if (!resp.isNull("error")) throw AnkiConnectException("AnkiConnect error: ${resp.get("error")}")
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
        val r = post("guiCurrentCard") ?: return null
        if (r == JSONObject.NULL || r == false) return null
        val o = r as JSONObject
        return AnkiCard(
            questionHtml = o.optString("question", ""),
            answerHtml = o.optString("answer", ""),
            deckName = o.optString("deckName", ""),
            buttons = parseButtons(o.optJSONArray("buttons"))
        )
    }

    /** Number of cards due now in the named deck. */
    fun dueCount(deckName: String): Int {
        val r = post("findCards", mapOf("query" to "deck:\"$deckName\" (is:due OR is:new)"))
        return (r as? JSONArray)?.length() ?: 0
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
