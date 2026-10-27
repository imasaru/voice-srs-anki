package com.evan.voicesrs

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object StudyBlockStore {
    const val KEY_INDEX = "block_index"          // Int, stored as Int
    const val KEY_ELAPSED = "block_elapsed_ms"   // Long, stored as Long
    const val KEY_GOAL = "block_goal_min"        // Int
    const val KEY_DAY = "block_day"              // String "yyyy-MM-dd" (Anki day)
    const val KEY_RUN_START = "block_run_start_ms" // Long; key ABSENT when not running
    const val KEY_DONE = "block_done"            // Boolean
    const val DAY_START_HOUR = 4                 // Anki day starts at 04:00 local time

    data class State(
        val index: Int,          // 1-based block number within the Anki day
        val elapsedMs: Long,     // banked time, excluding the current running stretch
        val goalMin: Int,        // 0 = no goal
        val dayKey: String,      // Anki day this block belongs to
        val runStartMs: Long?,   // wall-clock ms when the current stretch started; null = paused
        val done: Boolean,       // block finished (goal reached or finish() called)
    )

    /** Anki day for a wall-clock time: local date of (now minus 4 hours), formatted yyyy-MM-dd. */
    fun dayKey(nowMs: Long, zone: ZoneId): String {
        val shifted = Instant.ofEpochMilli(nowMs - DAY_START_HOUR * 3600_000L)
            .atZone(zone).toLocalDate()
        return shifted.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    /** null when KEY_INDEX is missing. Numbers may come back as Int or Long (use (x as Number).toLong()/toInt()). */
    fun read(map: Map<String, *>): State? {
        if (map[KEY_INDEX] == null) return null
        val index = (map[KEY_INDEX] as Number).toInt()
        val elapsed = (map[KEY_ELAPSED] as Number?)?.toLong() ?: 0L
        val goal = (map[KEY_GOAL] as Number?)?.toInt() ?: 0
        val day = (map[KEY_DAY] as String?).orEmpty()
        val runStart = (map[KEY_RUN_START] as Number?)?.toLong()
        val done = (map[KEY_DONE] as Boolean?) ?: false
        return State(index, elapsed, goal, day, runStart, done)
    }

    /** Writes all keys; REMOVES KEY_RUN_START when runStartMs is null. */
    fun write(map: MutableMap<String, Any?>, s: State) {
        map[KEY_INDEX] = s.index
        map[KEY_ELAPSED] = s.elapsedMs
        map[KEY_GOAL] = s.goalMin
        map[KEY_DAY] = s.dayKey
        map[KEY_DONE] = s.done
        if (s.runStartMs == null) map.remove(KEY_RUN_START) else map[KEY_RUN_START] = s.runStartMs
    }

    /** elapsedMs plus the running stretch (now - runStartMs, never negative). */
    fun elapsedAt(s: State, nowMs: Long): Long {
        val run = s.runStartMs
        return if (run == null) s.elapsedMs else {
            val delta = nowMs - run
            s.elapsedMs + if (delta > 0) delta else 0L
        }
    }

    /** Elapsed ms the drill's goal check uses: the block's elapsedAt when a block exists, else the session's own elapsed ms. */
    fun goalElapsedMs(s: State?, nowMs: Long, sessionElapsedMs: Long): Long =
        if (s == null) sessionElapsedMs else elapsedAt(s, nowMs)

    /** True when s != null, !s.done, s.dayKey == dayKey(nowMs, zone) and elapsedAt(s, nowMs) > 0. Used to offer "Continue block (34 of 90)". */
    fun hasUnfinished(s: State?, nowMs: Long, zone: ZoneId): Boolean {
        if (s == null) return false
        if (s.done) return false
        if (s.dayKey != dayKey(nowMs, zone)) return false
        return elapsedAt(s, nowMs) > 0
    }

    /**
     * Start or resume.
     * Resume (return s with runStartMs = nowMs, or s unchanged if already running) when newBlock == false AND hasUnfinished-style
     * conditions hold: s != null, !s.done, s.dayKey == dayKey(nowMs, zone). (elapsed 0 is fine here.)
     * Otherwise a NEW block: State(index = if (s != null && s.dayKey == today) s.index + 1 else 1, elapsedMs = 0,
     * goalMin = goalMin, dayKey = today, runStartMs = nowMs, done = false).
     */
    fun start(s: State?, nowMs: Long, zone: ZoneId, goalMin: Int, newBlock: Boolean): State {
        val today = dayKey(nowMs, zone)
        if (!newBlock && s != null && !s.done && s.dayKey == today) {
            if (s.runStartMs != null) return s
            return s.copy(runStartMs = nowMs)
        }
        val index = if (s != null && s.dayKey == today) s.index + 1 else 1
        return State(
            index = index,
            elapsedMs = 0L,
            goalMin = goalMin,
            dayKey = today,
            runStartMs = nowMs,
            done = false
        )
    }

    /** Bank the running stretch: if runStartMs == null return s; else s.copy(elapsedMs = elapsedAt(s, nowMs), runStartMs = null). */
    fun pause(s: State, nowMs: Long): State {
        if (s.runStartMs == null) return s
        return s.copy(elapsedMs = elapsedAt(s, nowMs), runStartMs = null)
    }

    /** pause(s, nowMs).copy(done = true) */
    fun finish(s: State, nowMs: Long): State = pause(s, nowMs).copy(done = true)

    /** If s.goalMin > 0 and elapsedAt(s, nowMs) >= s.goalMin * 60_000L return finish(s, nowMs); else s. */
    fun tick(s: State, nowMs: Long): State {
        if (s.goalMin > 0 && elapsedAt(s, nowMs) >= s.goalMin * 60_000L) return finish(s, nowMs)
        return s
    }
}
