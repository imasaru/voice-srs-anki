package com.evan.voicesrs

import java.time.ZoneId

/** h150a (item 63): text and state for the study block chip and the "Continue study block?" dialog. */
object StudyBlockChip {
    enum class Kind { RUNNING, PAUSED, DONE }

    const val SEP = " \u00B7 "
    const val DIALOG_TITLE = "Continue study block?"
    const val NEW_LABEL = "Start new block"

    /** null hides the chip: no block, or the block belongs to another Anki day. */
    fun kind(s: StudyBlockStore.State?, nowMs: Long, zone: ZoneId): Kind? {
        if (s == null || s.dayKey != StudyBlockStore.dayKey(nowMs, zone)) return null
        return when {
            s.done -> Kind.DONE
            s.runStartMs != null -> Kind.RUNNING
            else -> Kind.PAUSED
        }
    }

    /** "M/G min" with a goal, else "M min". M = StudyBlockPrompt.minutes. */
    private fun amount(s: StudyBlockStore.State, nowMs: Long): String {
        val m = StudyBlockPrompt.minutes(s, nowMs)
        return if (s.goalMin > 0) "$m/${s.goalMin} min" else "$m min"
    }

    /** RUNNING/PAUSED: "Block N · M/G min"; DONE: "Block N done · M min". */
    fun label(s: StudyBlockStore.State, nowMs: Long, kind: Kind): String =
        if (kind == Kind.DONE) "Block ${s.index} done$SEP${StudyBlockPrompt.minutes(s, nowMs)} min"
        else "Block ${s.index}$SEP${amount(s, nowMs)}"

    /** Goal progress 0..100 (whole percent, rounded down, capped at 100); 0 when there is no goal. */
    fun progress(s: StudyBlockStore.State, nowMs: Long): Int {
        if (s.goalMin <= 0) return 0
        val pct = StudyBlockStore.elapsedAt(s, nowMs) * 100L / (s.goalMin * 60_000L)
        return if (pct > 100L) 100 else pct.toInt()
    }

    /** "Block N today · M of G min", or "Block N today · M min" without a goal. */
    fun dialogBody(s: StudyBlockStore.State, nowMs: Long): String {
        val m = StudyBlockPrompt.minutes(s, nowMs)
        val amt = if (s.goalMin > 0) "$m of ${s.goalMin} min" else "$m min"
        return "Block ${s.index} today$SEP$amt"
    }

    /** "Continue (M/G)", or "Continue (M min)" without a goal. */
    fun continueLabel(s: StudyBlockStore.State, nowMs: Long): String {
        val m = StudyBlockPrompt.minutes(s, nowMs)
        return if (s.goalMin > 0) "Continue ($m/${s.goalMin})" else "Continue ($m min)"
    }
}
