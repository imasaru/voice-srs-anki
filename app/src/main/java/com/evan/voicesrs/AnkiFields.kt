package com.evan.voicesrs

import org.json.JSONObject

/**
 * AnkiConnect note fields. [cardsInfo] and [guiCurrentCard] return
 * `{ "Correct": { "value": "B", "order": 2 } }`. Missing names are blank.
 */
object AnkiFields {
    const val CORRECT = "Correct"

    fun value(fields: JSONObject?, name: String): String {
        if (fields == null || name.isBlank() || !fields.has(name)) return ""
        val raw = fields.opt(name) ?: return ""
        return when (raw) {
            is JSONObject -> raw.optString("value", "").trim()
            is String -> raw.trim()
            else -> raw.toString().trim()
        }
    }

    fun has(fields: JSONObject?, name: String): Boolean {
        if (fields == null || name.isBlank()) return false
        return fields.has(name)
    }

    fun correct(fields: JSONObject?): String = value(fields, CORRECT)
}
