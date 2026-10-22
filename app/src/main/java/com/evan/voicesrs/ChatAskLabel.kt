package com.evan.voicesrs

/**
 * The grey line above the Ask the AI field.
 * [line] uses the same QUESTION / CARD label as the bubble.
 * With no card up, [general] names the deck, or just says it is general.
 */
object ChatAskLabel {
    fun line(label: String, deckName: String): String? {
        val name = label.trim()
        val deck = deckName.trim()
        if (name.isEmpty() || deck.isEmpty()) return null
        return "Asking about: $name · $deck"
    }

    fun general(deckName: String): String {
        val deck = deckName.trim()
        return if (deck.isEmpty()) "General question" else "General question · $deck"
    }
}
