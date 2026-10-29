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
    const val PREFIX_ORIG = "exam_orig_cfg:"
    const val PREFIX_APPLIED = "exam_applied:"
    const val PREFIX_COVERAGE = "exam_coverage:"
    const val PREFIX_SNAP_CFG = "exam_snap_cfg:"
    const val PREFIX_SNAP_DUE = "exam_snap_due:"
    const val PREFIX_SNAP_TODAY = "exam_snap_today:"

    data class State(
        val date: LocalDate? = null,
        val target: Double? = null,
        val cloneId: Long? = null,
        val origCfg: Long? = null,
        val lastApplied: LocalDate? = null,
        val coverage: Int? = null
        ,
        /** Full getDeckConfig JSON of the original preset, saved once before the clone is first assigned. */
        val snapConfig: String? = null,
        /** card id -> ORIGINAL ABSOLUTE Anki due day number (cardsInfo "due", review cards), for cards the sweep moved. */
        val snapDue: Map<Long, Int> = emptyMap(),
        /** Absolute Anki today day number when snapDue was captured. */
        val snapToday: Int? = null
    )

    fun isExamKey(key: String): Boolean =
        key.startsWith(PREFIX_DATE) ||
            key.startsWith(PREFIX_TARGET) ||
            key.startsWith(PREFIX_CLONE) ||
            key.startsWith(PREFIX_ORIG) ||
            key.startsWith(PREFIX_APPLIED) ||
            key.startsWith(PREFIX_COVERAGE) ||
            key.startsWith(PREFIX_SNAP_CFG) || key.startsWith(PREFIX_SNAP_DUE) || key.startsWith(PREFIX_SNAP_TODAY)

    fun dateKey(deck: String) = PREFIX_DATE + deck
    fun targetKey(deck: String) = PREFIX_TARGET + deck
    fun cloneKey(deck: String) = PREFIX_CLONE + deck
    fun origKey(deck: String) = PREFIX_ORIG + deck
    fun appliedKey(deck: String) = PREFIX_APPLIED + deck
    fun coverageKey(deck: String) = PREFIX_COVERAGE + deck
    fun snapCfgKey(deck: String) = PREFIX_SNAP_CFG + deck
    fun snapDueKey(deck: String) = PREFIX_SNAP_DUE + deck
    fun snapTodayKey(deck: String) = PREFIX_SNAP_TODAY + deck

    fun read(map: Map<String, *>, deck: String): State {
        return State(
            date = parseDate(asString(map[dateKey(deck)])),
            target = parseTarget(asString(map[targetKey(deck)])),
            cloneId = parseLong(map[cloneKey(deck)]),
            origCfg = parseLong(map[origKey(deck)]),
            lastApplied = parseDate(asString(map[appliedKey(deck)])),
            coverage = parseInt(map[coverageKey(deck)]),
            snapConfig = asString(map[snapCfgKey(deck)])?.takeIf { it.isNotBlank() },
            snapDue = parseDue(asString(map[snapDueKey(deck)])),
            snapToday = parseInt(map[snapTodayKey(deck)])
        )
    }

    fun write(map: MutableMap<String, Any?>, deck: String, state: State) {
        putOrRemove(map, dateKey(deck), state.date?.toString())
        putOrRemove(map, targetKey(deck), formatTarget(state.target))
        putOrRemove(map, cloneKey(deck), state.cloneId?.toString())
        putOrRemove(map, origKey(deck), state.origCfg?.toString())
        putOrRemove(map, appliedKey(deck), state.lastApplied?.toString())
        putOrRemove(map, coverageKey(deck), state.coverage?.toString())
        putOrRemove(map, snapCfgKey(deck), state.snapConfig)
        putOrRemove(map, snapDueKey(deck), formatDue(state.snapDue))
        putOrRemove(map, snapTodayKey(deck), state.snapToday?.toString())
    }

    /** Drop the date and ramp override; keep clone/orig so a later restore reuses them. */
    fun disable(state: State): State =
        state.copy(date = null, target = null, lastApplied = null, coverage = null)

    /** Snapshot present when we have both the original preset id and its saved config JSON. */
    fun hasSnapshot(state: State): Boolean = state.origCfg != null && state.snapConfig != null

    /** Drop the exam date and the snapshot after a revert; keep cloneId so the clone preset is reused. */
    fun afterRevert(state: State): State =
        disable(state).copy(origCfg = null, snapConfig = null, snapDue = emptyMap(), snapToday = null)

    /** "id:days,id:days"; null when empty. */
    fun formatDue(due: Map<Long, Int>): String? =
        if (due.isEmpty()) null else due.entries.joinToString(",") { "${it.key}:${it.value}" }

    fun parseDue(raw: String?): Map<Long, Int> {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Long, Int>()
        for (part in t.split(',')) {
            val kv = part.split(':')
            if (kv.size != 2) continue
            val id = kv[0].trim().toLongOrNull() ?: continue
            val d = kv[1].trim().toIntOrNull() ?: continue
            out[id] = d
        }
        return out
    }

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
