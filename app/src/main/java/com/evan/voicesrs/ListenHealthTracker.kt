package com.evan.voicesrs

/**
 * Counts consecutive real microphone faults. Pure logic, no Android dependencies.
 *
 * A null or blank transcript is silence, no-match, or a timeout. Those never
 * count, never trip a warning, and never end the drill. Only [recordFault]
 * (a recognizer error that is not silence or no-match) can ask the caller to
 * warn. The caller must keep the session running. Only Stop ends a drill.
 */
class ListenHealthTracker(private val maxConsecutiveFaults: Int = 1) {
    private var consecutiveFaults = 0

    /**
     * Record silence or a transcript.
     * @return always false. Silence is not a microphone failure.
     */
    fun recordHeard(transcript: String?): Boolean {
        if (!transcript.isNullOrBlank()) consecutiveFaults = 0
        return false
    }

    /**
     * Record one real recognizer fault.
     * @return true when the caller should warn. This does not end the drill.
     */
    fun recordFault(): Boolean {
        consecutiveFaults += 1
        return consecutiveFaults >= maxConsecutiveFaults
    }

    fun reset() {
        consecutiveFaults = 0
    }
}

/**
 * Kept so a stray failure can still be recognized. Silence, no-match, and a
 * missed grade must not throw this. A thrown instance must not be the way a
 * session ends; only Stop does that.
 */
class MicDeadException : Exception()
