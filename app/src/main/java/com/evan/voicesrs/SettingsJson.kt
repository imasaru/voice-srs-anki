package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain-map import/export for voice-srs preferences.
 *
 * Operates on a `Map<String, Any?>` of pref key -> value (String, Boolean, Int,
 * Long, Float, Set<String>). No Android framework, no SharedPreferences: the
 * SharedPreferences / SAF wiring happens in a later run. Stock AnkiConnect only.
 */
object SettingsJson {
    const val SCHEMA_VERSION = 1
    val SECRET_KEYS = setOf("anki_key", "neural_token", "ai_api_key")

    sealed class ImportResult {
        data class Ok(
            val prefs: Map<String, Any?>,
            val skipped: List<String>,
            val hadSecrets: Boolean
        ) : ImportResult()

        data class Error(val reason: String) : ImportResult()
    }

    /**
     * Serialize prefs to JSON text. Top-level and prefs keys are emitted sorted
     * for stable output. Secret keys are dropped unless [includeSecrets]; blank
     * secrets are dropped even with opt-in. Null values are skipped.
     */
    fun export(
        prefs: Map<String, Any?>,
        includeSecrets: Boolean = false,
        appVersion: String = ""
    ): String {
        val filtered = LinkedHashMap<String, Any?>()
        for ((key, value) in prefs) {
            if (value == null) continue
            if (key in SECRET_KEYS) {
                if (!includeSecrets) continue
                if (value is String && value.isBlank()) continue
            }
            filtered[key] = value
        }
        val root = linkedMapOf<String, Any?>(
            "app" to "voice-srs",
            "appVersion" to appVersion,
            "includesSecrets" to includeSecrets,
            "prefs" to filtered,
            "schemaVersion" to SCHEMA_VERSION
        )
        val sb = StringBuilder()
        writeObject(sb, root)
        return sb.toString()
    }

    /**
     * Parse exported text against [known] (the current prefs map, which fixes the
     * expected type per key). Returns [Ok] with only the file's valid entries (a
     * delta: keys absent from the file are simply absent from Ok.prefs) or [Error]
     * with a short reason.
     */
    fun parse(text: String, known: Map<String, Any?>): ImportResult {
        val root: JSONObject = try {
            JSONObject(text)
        } catch (e: Exception) {
            return ImportResult.Error("invalid JSON")
        }
        if (root.optString("app", "") != "voice-srs") {
            return ImportResult.Error("unexpected app")
        }
        val schemaVersion = if (root.has("schemaVersion")) root.optInt("schemaVersion", 0) else 0
        if (schemaVersion > SCHEMA_VERSION) {
            return ImportResult.Error("schema version too new")
        }
        if (!root.has("prefs") || root.get("prefs") !is JSONObject) {
            return ImportResult.Error("missing prefs object")
        }
        val prefsObj = root.getJSONObject("prefs")
        val skipped = mutableListOf<String>()
        val out = LinkedHashMap<String, Any?>()
        var hadSecrets = false
        val keys: Iterator<String> = prefsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next() as String
            val expected = known[key]
            if (expected == null) {
                skipped.add(key)
                continue
            }
            val value = coerce(prefsObj.get(key), expected)
            if (value == null) {
                skipped.add(key)
                continue
            }
            if (key in SECRET_KEYS) {
                if (value is String && value.isBlank()) {
                    skipped.add(key)
                    continue
                }
                hadSecrets = true
            }
            out[key] = value
        }
        return ImportResult.Ok(out, skipped, hadSecrets)
    }

    private fun coerce(raw: Any?, expected: Any?): Any? = when (expected) {
        is Boolean -> if (raw is Boolean) raw else null
        is String -> if (raw is String) raw else null
        is Int -> numberMatch(raw, "int")
        is Long -> numberMatch(raw, "long")
        is Float -> numberMatch(raw, "float")
        is Double -> numberMatch(raw, "double")
        is Set<*> -> setMatch(raw, expected)
        else -> null
    }

    private fun numberMatch(raw: Any?, kind: String): Any? {
        if (raw !is Number) return null
        val d = raw.toDouble()
        if (d.isNaN() || d.isInfinite()) return null
        return when (kind) {
            "int" -> if (d == Math.floor(d)) d.toInt() else null
            "long" -> if (d == Math.floor(d)) d.toLong() else null
            "float" -> d.toFloat()
            "double" -> d
            else -> null
        }
    }

    private fun setMatch(raw: Any?, expected: Set<*>): Any? {
        if (raw !is JSONArray) return null
        val expectedElem = expected.firstOrNull()
        val result = LinkedHashSet<Any>()
        for (i in 0 until raw.length()) {
            val e = raw.get(i)
            if (!elemMatches(e, expectedElem)) return null
            result.add(e)
        }
        return result
    }

    private fun elemMatches(e: Any?, expectedElem: Any?): Boolean {
        return when (expectedElem) {
            null -> e is String
            is String -> e is String
            is Boolean -> e is Boolean
            is Int, is Long -> e is Number && (e as Number).toDouble() == Math.floor((e as Number).toDouble())
            is Float, is Double -> e is Number
            else -> false
        }
    }

    private fun writeObject(sb: StringBuilder, map: Map<String, Any?>) {
        sb.append('{')
        val keys = map.keys.sorted()
        for ((i, key) in keys.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append(jsonString(key)).append(':')
            writeValue(sb, map[key])
        }
        sb.append('}')
    }

    private fun writeValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> {}
            is String -> sb.append(jsonString(value))
            is Boolean -> sb.append(value.toString())
            is Int, is Long -> sb.append(value.toString())
            is Float, is Double -> sb.append(value.toString())
            is Map<*, *> -> writeObject(sb, value as Map<String, Any?>)
            is Set<*> -> writeArray(sb, value)
            else -> sb.append(jsonString(value.toString()))
        }
    }

    private fun writeArray(sb: StringBuilder, set: Set<*>) {
        sb.append('[')
        val sorted = set.sortedWith(compareBy { it.toString() })
        for ((i, item) in sorted.withIndex()) {
            if (i > 0) sb.append(',')
            writeValue(sb, item)
        }
        sb.append(']')
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
