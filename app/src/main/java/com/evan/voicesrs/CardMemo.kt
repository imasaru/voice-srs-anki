package com.evan.voicesrs

import org.json.JSONObject

/**
 * MemoFront / MemoBack on the note. The app never adds those fields.
 * Missing names hide the editors and show [MISSING_HINT].
 */
object CardMemo {
    const val FRONT = "MemoFront"
    const val BACK = "MemoBack"
    const val MISSING_HINT = "This note type has no MemoFront/MemoBack fields."

    data class State(
        val available: Boolean,
        val showHint: Boolean,
        val front: String,
        val back: String
    ) {
        val hint: String get() = if (showHint) MISSING_HINT else ""
    }

    fun of(fields: JSONObject?): State {
        if (fields == null) return State(available = false, showHint = false, "", "")
        val hasFront = AnkiFields.has(fields, FRONT)
        val hasBack = AnkiFields.has(fields, BACK)
        val available = hasFront && hasBack
        return State(
            available = available,
            showHint = !available,
            front = AnkiFields.value(fields, FRONT),
            back = AnkiFields.value(fields, BACK)
        )
    }

    fun showsBack(revealed: Boolean): Boolean = revealed

    fun fieldsToSave(front: String, back: String): Map<String, String> {
        return mapOf(FRONT to front, BACK to back)
    }
}
