package com.evan.voicesrs

import java.time.ZoneId

object StudyBlockPrompt {
    const val NEW_LABEL = "New block"

    data class Offer(
        val continueLabel: String,   // e.g. "Continue block (34 of 90)"
        val newLabel: String,        // always NEW_LABEL
    )

    /** Whole minutes of StudyBlockStore.elapsedAt(s, nowMs), rounded DOWN (59_999 ms -> 0, 60_000 ms -> 1). */
    fun minutes(s: StudyBlockStore.State, nowMs: Long): Int = TODO()

    /**
     * Offer shown when a drill starts. null unless StudyBlockStore.hasUnfinished(s, nowMs, zone) is true.
     * continueLabel = "Continue block (M of G)" when s.goalMin > 0, else "Continue block (M min)", where M = minutes(s, nowMs), G = s.goalMin.
     */
    fun offer(s: StudyBlockStore.State?, nowMs: Long, zone: ZoneId): Offer? = TODO()

    /** "Block N: M of G min" when goalMin > 0, else "Block N: M min". N = s.index, M = minutes(s, nowMs). */
    fun blockLine(s: StudyBlockStore.State, nowMs: Long): String = TODO()

    /**
     * Applies the user's choice: continueChosen = true resumes (StudyBlockStore.start(s, nowMs, zone, goalMin, newBlock = false)),
     * false starts a new block (StudyBlockStore.start(s, nowMs, zone, goalMin, newBlock = true)).
     */
    fun choose(s: StudyBlockStore.State?, nowMs: Long, zone: ZoneId, goalMin: Int, continueChosen: Boolean): StudyBlockStore.State = TODO()
}
