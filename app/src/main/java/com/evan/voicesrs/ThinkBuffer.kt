package com.evan.voicesrs

/**
 * Grows the partial transcript as open-mic windows arrive during think time.
 * Windows are appended in order; an overlapping window is de-duplicated so a
 * word straddling two windows is not repeated. Blank windows are ignored.
 */
class ThinkBuffer {
    private val parts = ArrayList<String>()

    fun append(window: String?) {
        val w = window?.trim() ?: return
        if (w.isEmpty()) return
        if (parts.isNotEmpty()) {
            val kept = dropOverlap(text(), w)
            if (kept.isNotEmpty()) parts.add(kept)
        } else {
            parts.add(w)
        }
    }

    fun text(): String = parts.joinToString(" ")

    fun isEmpty(): Boolean = parts.isEmpty()

    fun clear() { parts.clear() }

    /**
     * If [needle] starts with the longest whole-word suffix of [hay], drop that
     * prefix and return the rest. Otherwise return [needle] unchanged.
     */
    private fun dropOverlap(hay: String, needle: String): String {
        val hayWords = hay.split(" ")
        for (i in hayWords.indices) {
            val suffix = hayWords.subList(i, hayWords.size).joinToString(" ")
            if (suffix.isNotBlank() && needle.startsWith(suffix, ignoreCase = true)) {
                return needle.substring(suffix.length).trim()
            }
        }
        return needle
    }
}
