package com.evan.voicesrs

import java.time.LocalDate

/**
 * Per-deck exam prefs as a plain map. Keys are `exam_*:&lt;deck&gt;`.
 * SharedPreferences / Settings wiring lives in [Prefs].
 */
object ExamStore {
    const val PREFIX_DATE = "exam_date:"
    const val PREFIX_TARGET = "exam_target:"
    const val PREFIX_CLONE = "exam_clone_id:"
    const val PREFIX_APPLIED = "exam_applied:"
    const val PREFIX_COVERAGE = "exam_coverage:"

    data class State(
        val date: LocalDate? = null,
        val target: Double? = null,
        val cloneId: Long? = null,
        val lastApplied: LocalDate? = null,
        val coverage: Int? = null
    )

    fun isExamKey(key: String): Boolean =
        key.startsWith(PREFIX_DATE) ||
            key.startsWith(PREFIX_TARGET) ||
            key.startsWith(PREFIX_CLONE) ||
            key.startsWith(PREFIX_APPLIED) ||
            key.startsWith(PREFIX_COVERAGE)

    fun dateKey(deck: String) = PREFIX_DATE + deck
    fun targetKey(deck: String) = PREFIX_TARGET + deck
    fun cloneKey(deck: String) = PREFIX_CLONE + deck
    fun appliedKey(deck: String) = PREFIX_APPLIED + deck
    fun coverageKey(deck: String) = PREFIX_COVERAGE + deck

    fun read(map: Map<String, *>, deck: String): State {
        return State(
            date = parseDate(asString(map[dateKey(deck)])),
            target = parseTarget(asString(map[targetKey(deck)])),
            cloneId = parseLong(map[cloneKey(deck)]),
            lastApplied = parseDate(asString(map[appliedKey(deck)])),
            coverage = parseInt(map[coverageKey(deck)])
        )
    }

    fun write(map: MutableMap<String, Any?>, deck: String, state: State) {
        putOrRemove(map, dateKey(deck), state.date?.toString())
        putOrRemove(map, targetKey(deck), formatTarget(state.target))
        putOrRemove(map, cloneKey(deck), state.cloneId?.toString())
        putOrRemove(map, appliedKey(deck), state.lastApplied?.toString())
        putOrRemove(map, coverageKey(deck), state.coverage?.toString())
    }

    /** Drop the date and ramp override; keep a clone id so a later date reuses it. */
    fun disable(state: State): State =
        state.copy(date = null, target = null, lastApplied = null, coverage = null)

    /**
     * Retention override. Blank is none. Values above 1 are treated as percents
     * (90 → 0.90). Result is rounded to 2 decimals in 0.70..0.99, or null.
     */
    fun parseTarget(raw: String?): Double? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val n = t.toDoubleOrNull() ?: return null
        val frac = if (n > 1.0) n / 100.0 else n
        if (frac !in 0.70..0.99) return null
        return Math.round(frac * 100.0) / 100.0
    }

    fun formatTarget(target: Double?): String? {
        if (target == null) return null
        return String.format(java.util.Locale.US, "%.2f", target)
    }

    fun parseDate(raw: String?): LocalDate? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t)
        } catch (_: Exception) {
            null
        }
    }

    private fun putOrRemove(map: MutableMap<String, Any?>, key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    private fun asString(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        else -> value.toString()
    }

    private fun parseLong(value: Any?): Long? {
        return when (value) {
            null -> null
            is Long -> value.takeIf { it != 0L }
            is Int -> value.toLong().takeIf { it != 0L }
            is Number -> value.toLong().takeIf { it != 0L }
            is String -> value.trim().toLongOrNull()?.takeIf { it != 0L }
            else -> null
        }
    }

    private fun parseInt(value: Any?): Int? {
        return when (value) {
            null -> null
            is Int -> value
            is Long -> value.toInt()
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
}
