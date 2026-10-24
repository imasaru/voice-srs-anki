package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Body for AnkiConnect `answerCards`.
 *
 * Stock AnkiConnect takes `{cardId, ease}` only and starts the card timer
 * immediately before answering. Extra `time` (review duration in ms) is
 * ignored there (harmless); real duration lives in [AnswerTimeLog]. Duration 0
 * is omitted so Anki keeps its own timer.
 */
object AnswerCardsRequest {
    const val TIME_KEY = "time"

    fun body(grades: List<PendingGrade>, includeTime: Boolean = true): JSONArray {
        val arr = JSONArray()
        for (grade in grades) {
            val o = JSONObject()
            o.put("cardId", grade.cardId)
            o.put("ease", grade.ease)
            if (includeTime && grade.durationMs > ThinkTime.UNKNOWN_DURATION_MS) {
                o.put(TIME_KEY, grade.durationMs)
            }
            arr.put(o)
        }
        return arr
    }

    /** True when the server complained about the extra time field. */
    fun timeRejected(message: String?): Boolean {
        val m = message.orEmpty().lowercase()
        if (m.isEmpty()) return false
        val mentionsTime = m.contains("time") || m.contains("duration")
        if (!mentionsTime) return false
        return m.contains("unexpected") ||
            m.contains("unknown") ||
            m.contains("invalid") ||
            m.contains("extra") ||
            m.contains("unexpected keyword")
    }
}
