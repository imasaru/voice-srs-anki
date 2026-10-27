package com.evan.voicesrs

/**
 * Display split for Anki hierarchical deck names (`Parent::Child::Leaf`).
 * Storage, Prefs, and AnkiConnect queries keep the full `A::B::C` path.
 */
object DeckName {
    /**
     * Parents joined with `" › "` and the leaf. Top-level decks have a blank
     * path. Empty parts after trim are ignored. Blank input is `("", "")`.
     */
    fun split(full: String): Pair<String, String> {
        val parts = full.split("::").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "" to ""
        if (parts.size == 1) return "" to parts[0]
        return parts.dropLast(1).joinToString(" › ") to parts.last()
    }

    /**
     * True only when a drill is currently running [running] and the deck the
     * drill was started against [drillDeck] differs from the deck now stored in
     * [currentDeck]. Compared after trimming, so a whitespace-only difference
     * is not a change. Returns false when [drillDeck] is null (the drill was
     * started before this activity began tracking it) or when no drill runs.
     */
    fun changedMidDrill(drillDeck: String?, currentDeck: String, running: Boolean): Boolean {
        return running && drillDeck != null && drillDeck.trim() != currentDeck.trim()
    }
}
