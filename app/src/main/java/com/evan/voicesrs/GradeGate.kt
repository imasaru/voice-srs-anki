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
    /** False while only the question is showing: grades are ignored until [reveal]. */
    private var revealed = false

    @Synchronized fun newCard() { claimed = false; claimedEase = null; open = true; revealed = false }

    /** The answer is now shown/spoken: rating controls appear and grades are accepted. */
    @Synchronized fun reveal() { if (open) revealed = true }

    @Synchronized fun isRevealed(): Boolean = open && revealed

    /** No card on screen (session ended or not started yet). */
    @Synchronized fun close() { open = false; claimed = false; claimedEase = null; revealed = false }

    /** Returns true only for the first claim on the current card. */
    @Synchronized fun tryClaim(ease: Int?): Boolean {
        if (!open || !revealed || claimed) return false
        claimed = true
        claimedEase = ease
        return true
    }

    /**
     * Silent mode: Show answer and rating are live as soon as the question is up,
     * so a rating may be claimed before [reveal]. Still one claim per card.
     */
    @Synchronized fun tryClaimInstant(ease: Int?): Boolean {
        if (!open || claimed) return false
        claimed = true
        claimedEase = ease
        return true
    }

    /**
     * Resolve a voice grade against an earlier claim: returns the ease that must be used.
     * The first claim wins, so a voice grade after a tap keeps the tapped ease.
     */
    @Synchronized fun resolveVoice(ease: Int): Int? {
        if (!open || !revealed) return null
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
