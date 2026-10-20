package com.evan.voicesrs

/**
 * One grade per card. The first claim (chip tap or voice) wins; later taps or
 * voice grades for the same card are ignored until [newCard] is called.
 */
class GradeGate {
    private var claimed = false
    /** False until a card is on screen (and again after the session ends): taps are ignored. */
    private var open = false
    private var claimedEase: Int? = null

    @Synchronized fun newCard() { claimed = false; claimedEase = null; open = true }

    /** No card on screen (session ended or not started yet). */
    @Synchronized fun close() { open = false; claimed = false; claimedEase = null }

    /** Returns true only for the first claim on the current card. */
    @Synchronized fun tryClaim(ease: Int?): Boolean {
        if (!open || claimed) return false
        claimed = true
        claimedEase = ease
        return true
    }

    /**
     * Resolve a voice grade against an earlier claim: returns the ease that must be used.
     * The first claim wins, so a voice grade after a tap keeps the tapped ease.
     */
    @Synchronized fun resolveVoice(ease: Int): Int {
        if (!claimed) { claimed = true; claimedEase = ease; return ease }
        return claimedEase ?: ease
    }

    @Synchronized fun isClaimed(): Boolean = claimed
    @Synchronized fun ease(): Int? = claimedEase

    companion object {
        fun wordOf(ease: Int?): String? = when (ease) {
            1 -> "again"; 2 -> "hard"; 3 -> "good"; 4 -> "easy"; else -> null
        }

        fun easeOf(word: String?): Int? = when (word) {
            "again" -> 1; "hard" -> 2; "good" -> 3; "easy" -> 4; else -> null
        }
    }
}
