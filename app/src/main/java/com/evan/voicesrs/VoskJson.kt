package com.evan.voicesrs

import org.json.JSONObject

/** Pulls the hypothesis strings out of Vosk's JSON callbacks. */
object VoskJson {
    /** `"partial"` text, or null when it is missing or blank. */
    fun partial(json: String): String? = field(json, "partial")

    /** `"text"` of a result or final result, or null when it is missing or blank. */
    fun text(json: String): String? = field(json, "text")

    private fun field(json: String, key: String): String? {
        val value = try {
            JSONObject(json).optString(key, "")
        } catch (_: Exception) {
            return null
        }
        return value.ifBlank { null }
    }
}
