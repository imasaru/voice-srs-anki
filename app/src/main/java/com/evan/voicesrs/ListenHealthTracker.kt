package com.evan.voicesrs

/**
 * Counts consecutive listen() calls that returned null (timeout, silence, or
 * recognizer error). Pure logic, no Android dependencies — unit-testable.
 *
 * The drill must fail LOUD instead of silently rolling through cards of dead
 * air: when [maxConsecutiveNulls] nulls happen in a row, the caller speaks a
 * warning and aborts via [MicDeadException].
 */
class ListenHealthTracker(private val maxConsecutiveNulls: Int = 3) {
    private var consecutiveNulls = 0

    /**
     * Record one listen result.
     * @return true once [maxConsecutiveNulls] nulls have occurred consecutively.
     */
    fun recordHeard(transcript: String?): Boolean {
        consecutiveNulls = if (transcript == null) consecutiveNulls + 1 else 0
        return consecutiveNulls >= maxConsecutiveNulls
    }

    fun reset() {
        consecutiveNulls = 0
    }
}

/** Thrown from listen() when the mic is judged dead; aborts the drill. */
class MicDeadException : Exception()
