package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Pure request builders and response parsers for AnkiConnect deck-config and
 * maintenance actions. Stock AnkiConnect only — no add-on support or detection.
 *
 * Every request is a body of {"action","version":6,"key","params"}; [post] in
 * [AnkiConnect] handles the envelope. The methods here only shape `params` and
 * interpret the `result`, so they are trivially unit-testable with org.json.
 */
object DeckConfigRequest {

    /** Anki due-days syntax: a single day, an optional "!" reset, or a "low-high" range. */
    private val DUE_DAYS_REGEX = Regex("^\\d+!?$|^\\d+-\\d+!?$")

    /**
     * params for `getDeckConfig`.
     */
    fun getDeckConfigParams(deck: String): Map<String, Any?> {
        return mapOf("deck" to deck)
    }

    /**
     * Returns a copy of [config] with "desiredRetention" set to [retention].
     * The original is left untouched and its "id" is preserved by the copy.
     * Anki retention lives in 0.70..0.99.
     */
    fun withDesiredRetention(config: JSONObject, retention: Double): JSONObject {
        require(retention in 0.70..0.99) { "desiredRetention must be in 0.70..0.99, was $retention" }
        val copy = JSONObject(config.toString())
        copy.put("desiredRetention", retention)
        return copy
    }

    /**
     * Reads the desired-retention preset key, or null when the preset omits it.
     */
    fun desiredRetention(config: JSONObject): Double? {
        if (!config.has("desiredRetention")) return null
        return config.optDouble("desiredRetention", 0.0)
    }

    /**
     * params for `saveDeckConfig`. The preset object must carry its "id".
     */
    fun saveDeckConfigParams(config: JSONObject): Map<String, Any?> {
        require(config.has("id")) { "deck config requires an id" }
        return mapOf("config" to config)
    }

    /**
     * params for `cloneDeckConfigId`.
     */
    fun cloneParams(name: String, cloneFrom: Long): Map<String, Any?> {
        return mapOf("name" to name, "cloneFrom" to cloneFrom)
    }

    /**
     * params for `setDeckConfigId`. [decks] is wrapped in a JSONArray so it
     * serialises as an array inside the params object.
     */
    fun setDeckConfigIdParams(decks: List<String>, configId: Long): Map<String, Any?> {
        val arr = JSONArray()
        for (d in decks) arr.put(d)
        return mapOf("decks" to arr, "configId" to configId)
    }

    /**
     * params for `setDueDate`. [cardIds] is wrapped in a JSONArray; [days] must
     * match Anki's due-days syntax and describe a non-inverted range.
     */
    fun setDueDateParams(cardIds: List<Long>, days: String): Map<String, Any?> {
        require(cardIds.isNotEmpty()) { "setDueDate needs at least one card" }
        require(DUE_DAYS_REGEX.matches(days)) { "invalid due-days syntax: '$days'" }
        val (low, high) = dueRange(days)
        require(low <= high) { "invalid due-days range: '$days'" }
        val arr = JSONArray()
        for (id in cardIds) arr.put(id)
        return mapOf("cards" to arr, "days" to days)
    }

    /**
     * params for `retrieveMediaFile`.
     */
    fun retrieveMediaParams(filename: String): Map<String, Any?> {
        require(filename.isNotBlank()) { "media filename must not be blank" }
        return mapOf("filename" to filename)
    }

    /**
     * Interprets a `retrieveMediaFile` result: base64 string decodes to bytes;
     * false / null / JSON NULL / anything else yields null.
     */
    fun parseMedia(result: Any?): ByteArray? {
        val raw = when (result) {
            null -> return null
            is String -> result
            else -> return null
        }
        return try {
            Base64.getDecoder().decode(raw)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Interprets a `cloneDeckConfigId` result: a number becomes the new preset
     * id, false / null / JSON NULL / anything else yields null.
     */
    fun parseCloneId(result: Any?): Long? {
        return when (result) {
            null, JSONObject.NULL -> null
            is Boolean -> null
            is Number -> result.toLong()
            else -> null
        }
    }

    /** Splits a due-days string into its [low, high] day bounds. */
    private fun dueRange(days: String): Pair<Int, Int> {
        val core = days.removeSuffix("!")
        return if (core.contains('-')) {
            val parts = core.split('-')
            parts[0].trim().toInt() to parts[1].trim().toInt()
        } else {
            val n = core.toInt()
            n to n
        }
    }
}
