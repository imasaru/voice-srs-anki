package com.evan.voicesrs

/**
 * Spoken grade guides. Pure Kotlin so the first-cards vs short vs speed-mode
 * choice can be unit-tested without Android.
 *
 * [cardIndex] is 0-based: the first [LONG_CARDS] cards get the long prompt.
 */
object RatingGuides {
    const val LONG_CARDS = 3
    const val LONG = "How did you do? Say again, hard, good, or easy."
    const val SHORT = "Rate it: again, hard, good, or easy."
    const val REPROMPT = "Say a grade: again, hard, good or easy"

    /** Null when speed mode skips the opening guide. */
    fun initialPrompt(cardIndex: Int, speedMode: Boolean): String? {
        if (speedMode) return null
        return if (cardIndex < LONG_CARDS) LONG else SHORT
    }

    /** Null when speed mode skips the retry guide. The attempt still counts. */
    fun reprompt(speedMode: Boolean): String? = if (speedMode) null else REPROMPT
}
