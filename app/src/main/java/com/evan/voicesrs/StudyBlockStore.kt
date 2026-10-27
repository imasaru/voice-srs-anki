package com.evan.voicesrs

import java.time.Instant
import java.time.ZoneId

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
    fun dayKey(nowMs: Long, zone: ZoneId): String = TODO()
    /** null when KEY_INDEX is missing. Numbers may come back as Int or Long (use (x as Number).toLong()/toInt()). */
    fun read(map: Map<String, *>): State? = TODO()
    /** Writes all keys; REMOVES KEY_RUN_START when runStartMs is null. */
    fun write(map: MutableMap<String, Any?>, s: State): Unit = TODO()
    /** elapsedMs plus the running stretch (now - runStartMs, never negative). */
    fun elapsedAt(s: State, nowMs: Long): Long = TODO()
    /** True when s != null, !s.done, s.dayKey == dayKey(nowMs, zone) and elapsedAt(s, nowMs) > 0. Used to offer "Continue block (34 of 90)". */
    fun hasUnfinished(s: State?, nowMs: Long, zone: ZoneId): Boolean = TODO()
    /**
     * Start or resume.
     * Resume (return s with runStartMs = nowMs, or s unchanged if already running) when newBlock == false AND hasUnfinished-style
     * conditions hold: s != null, !s.done, s.dayKey == dayKey(nowMs, zone). (elapsed 0 is fine here.)
     * Otherwise a NEW block: State(index = if (s != null && s.dayKey == today) s.index + 1 else 1, elapsedMs = 0,
     * goalMin = goalMin, dayKey = today, runStartMs = nowMs, done = false).
     */
    fun start(s: State?, nowMs: Long, zone: ZoneId, goalMin: Int, newBlock: Boolean): State = TODO()
    /** Bank the running stretch: if runStartMs == null return s; else s.copy(elapsedMs = elapsedAt(s, nowMs), runStartMs = null). */
    fun pause(s: State, nowMs: Long): State = TODO()
    /** pause(s, nowMs).copy(done = true) */
    fun finish(s: State, nowMs: Long): State = TODO()
    /** If s.goalMin > 0 and elapsedAt(s, nowMs) >= s.goalMin * 60_000L return finish(s, nowMs); else s. */
    fun tick(s: State, nowMs: Long): State = TODO()
}
