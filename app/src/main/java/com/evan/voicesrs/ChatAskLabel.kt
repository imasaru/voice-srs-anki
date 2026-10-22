package com.evan.voicesrs

/**
 * The grey line above the Ask the AI field.
 * Blank when no card is on screen, so the view can hide.
 */
object ChatAskLabel {
    fun line(questionNumber: Int, deckName: String): String? {
        if (questionNumber <= 0) return null
        val deck = deckName.trim()
        if (deck.isEmpty()) return null
        return "Asking about: Question $questionNumber · $deck"
    }
}
