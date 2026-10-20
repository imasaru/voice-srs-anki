package com.evan.voicesrs

import java.util.Locale
import kotlin.math.abs

/**
 * Pure-Kotlin mapping of spoken grade words to Anki ease values (1..4),
 * respecting the reviewer buttons actually offered for the card.
 */
object GradeMapper {
    private val EXACT_GRADE_WORDS = setOf("again", "hard", "good", "easy")

    /**
     * Exact grade word for the debug TEST_GRADE hook.
     * Returns again/hard/good/easy, or null if [raw] is anything else.
     */
    fun canonicalGradeWord(raw: String?): String? {
        val word = raw?.trim()?.lowercase(Locale.US) ?: return null
        return if (word in EXACT_GRADE_WORDS) word else null
    }

    /** Returns 1..4 for a recognized grade word, or null if not a grade. "repeat" is handled by the caller. */
    fun wordToEase(text: String): Int? = when {
        "again" in text -> 1
        "hard" in text -> 2
        "good" in text -> 3
        "easy" in text -> 4
        else -> null
    }

    fun isRepeat(text: String): Boolean = "repeat" in text

    /**
     * New/learning cards don't always offer all four eases. If the mapped ease
     * isn't offered, use the closest available button (e.g. easy->3 when
     * buttons=[1,2,3]; again->2 when buttons=[2,3,4]).
     */
    fun toAvailableEase(ease: Int, buttons: List<Int>): Int {
        if (buttons.isEmpty()) return ease.coerceIn(1, 4)
        if (ease in buttons) return ease
        return buttons.minByOrNull { abs(it - ease) } ?: ease
    }
}
