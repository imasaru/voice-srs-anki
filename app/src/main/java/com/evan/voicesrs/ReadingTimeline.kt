package com.evan.voicesrs

/** 58E: Edge word marks -> char ranges in the spoken text, and the sentence/word playing at a position. Pure, no Android. */
object ReadingTimeline {
    /** One Edge WordBoundary mark: start [offsetMs], [durationMs], the spoken [word]. */
    data class Mark(val offsetMs: Long, val durationMs: Long, val word: String)

    /** A mark placed in the text: plays [startMs] until [endMs], covers [chars] of the spoken text. */
    data class Timed(val startMs: Long, val endMs: Long, val chars: IntRange)

    /** Places each mark's word in [text] in order (case-insensitive, searching forward from the previous match). Words not found are skipped. */
    fun align(text: String, marks: List<Mark>): List<Timed> {
        val out = mutableListOf<Timed>()
        var cursor = 0
        for (m in marks) {
            val w = m.word.trim()
            if (w.isEmpty()) continue
            val idx = text.indexOf(w, cursor, ignoreCase = true)
            if (idx < 0) continue
            out.add(Timed(m.offsetMs, m.offsetMs + m.durationMs, idx until idx + w.length))
            cursor = idx + w.length
        }
        return out
    }

    /** Index of the SentenceSplit sentence of [text] playing at [ms]. With no [timed] marks, falls back to the position's share of [totalMs] over the text length. Blank text -> -1. */
    fun sentenceAt(text: String, timed: List<Timed>, totalMs: Long, ms: Long): Int {
        val ranges = SentenceSplit.ranges(text)
        if (ranges.isEmpty()) return -1
        val offset = if (timed.isEmpty()) {
            if (totalMs <= 0) return 0
            (ms.coerceIn(0, totalMs) * text.length / totalMs).toInt()
        } else {
            timed.lastOrNull { it.startMs <= ms }?.chars?.first ?: 0
        }
        val i = SentenceSplit.indexAt(ranges, offset)
        return if (i < 0) ranges.lastIndex else i
    }

    /** Char range of the word playing at [ms] (the last word started by then), or null before the first word or with no marks. Hook for #59 word highlight. */
    fun wordAt(timed: List<Timed>, ms: Long): IntRange? =
        timed.lastOrNull { it.startMs <= ms }?.chars
}
