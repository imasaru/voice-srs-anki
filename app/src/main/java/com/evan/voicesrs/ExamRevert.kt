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

    /**
     * p75a: largest relative due m in -36500..36500 with [hasDueAtLeast] (m) true, by binary search.
     * Negative = overdue. Null if even -36500 is false (card has no due).
     */
    fun searchRelDue(hasDueAtLeast: (Int) -> Boolean): Int? {
        var lo = -36500
        var hi = 36500
        if (!hasDueAtLeast(lo)) return null
        while (lo < hi) {
            val mid = Math.floorDiv(lo + hi + 1, 2)
            if (hasDueAtLeast(mid)) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** p75b: [moved] = cards whose setDueDate returned true; [failed] = steps that returned false. */
    data class Outcome(val moved: Int, val failed: Int)

    /** p75b: runs each step with [run] (true = Anki accepted). AssignPreset is skipped (the caller assigns it first). */
    fun runSteps(steps: List<Step>, run: (Step) -> Boolean): Outcome {
        var moved = 0
        var failed = 0
        for (step in steps) {
            if (step is Step.AssignPreset) continue
            if (!run(step)) failed++ else if (step is Step.SetDue) moved += step.cardIds.size
        }
        return Outcome(moved, failed)
    }

    /** Absolute Anki today day number from a probe card's absolute due [probeAbs] and its relative due [probeRel]. */
    fun todayAbs(probeAbs: Int, probeRel: Int): Int = probeAbs - probeRel
}
