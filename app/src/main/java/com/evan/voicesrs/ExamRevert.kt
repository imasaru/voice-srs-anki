package com.evan.voicesrs

import org.json.JSONObject

/** Pure plan for "Revert exam mode": original preset back, its config if it changed, swept cards back. */
object ExamRevert {
    sealed class Step {
        data class AssignPreset(val configId: Long) : Step()
        data class SaveConfig(val json: String) : Step()
        data class SetDue(val cardIds: List<Long>, val days: String) : Step()
    }

    /**
     * [currentConfigJson] is the live getDeckConfig JSON of the original preset (by id), or null if unknown.
     * [todayAbs] is Anki's absolute today day number at revert time (null = unknown: fall back to state.snapToday).
     * Each swept card gets days = origAbs - todayAbs, or 0 if negative (due today).
     */
    fun plan(state: ExamStore.State, currentConfigJson: String?, todayAbs: Int?): List<Step> {
        val orig = state.origCfg ?: return emptyList()
        val snap = state.snapConfig ?: return emptyList()
        val steps = ArrayList<Step>()
        steps += Step.AssignPreset(orig)
        if (currentConfigJson == null || !sameConfig(snap, currentConfigJson)) steps += Step.SaveConfig(snap)
        val byDays = LinkedHashMap<Int, MutableList<Long>>()
        val today = todayAbs ?: state.snapToday
        for ((id, abs) in state.snapDue) {
            if (today == null) break
            val left = (abs - today).coerceAtLeast(0)
            byDays.getOrPut(left) { ArrayList() } += id
        }
        for ((d, ids) in byDays.toSortedMap()) steps += Step.SetDue(ids, d.toString())
        return steps
    }

    /** Compare as JSON (key order and whitespace ignored), ignoring "mod" and "usn" which Anki bumps on save. */
    fun sameConfig(a: String, b: String): Boolean = try {
        val ja = JSONObject(a).apply { remove("mod"); remove("usn") }
        val jb = JSONObject(b).apply { remove("mod"); remove("usn") }
        canon(ja) == canon(jb)
    } catch (_: Exception) {
        false
    }

    private fun canon(v: Any?): String = when (v) {
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { "$it:${canon(v.opt(it))}" }
        is org.json.JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
        is Number -> v.toDouble().toString()
        else -> v.toString()
    }

    /** Absolute Anki today day number from a probe card's absolute due [probeAbs] and its relative due [probeRel]. */
    fun todayAbs(probeAbs: Int, probeRel: Int): Int = probeAbs - probeRel
}
