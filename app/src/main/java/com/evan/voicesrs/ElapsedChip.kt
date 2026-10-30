package com.evan.voicesrs

/**
 * Pure helpers for the "Elapsed m:ss" chip that sits beside the timer row countdown.
 * No Android types here: the activity formats against [SystemClock.elapsedRealtime].
 */
object ElapsedChip {
    const val PREFIX = "Elapsed "

    /** "Elapsed M:SS" under an hour, "Elapsed H:MM:SS" after. Negative reads as 0:00. */
    fun format(ms: Long): String {
        val safe = if (ms < 0) 0 else ms
        val totalSeconds = safe / 1_000L
        if (totalSeconds < 3_600L) {
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$PREFIX$minutes:${pad(seconds)}"
        }
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return "$PREFIX$hours:${pad(minutes)}:${pad(seconds)}"
    }

    /** h94c: visible chip text without the prefix ("1:26:23"); [format] stays the spoken description. */
    fun short(ms: Long): String = format(ms).removePrefix(PREFIX)

    /** The chip is shown only while a drill session is running. */
    fun visible(drillRunning: Boolean): Boolean = drillRunning

    /**
     * Wall time minus the paused span. [pausedSinceMs] is the [SystemClock.elapsedRealtime]
     * at which the drill last paused (0 while running); [pausedTotalMs] is the cumulative
     * paused time already subtracted. Unstarted sessions (startMs == 0) read as 0.
     */
    fun elapsed(startMs: Long, pausedTotalMs: Long, pausedSinceMs: Long, nowMs: Long): Long {
        if (startMs <= 0L) return 0
        var elapsed = nowMs - startMs - pausedTotalMs
        if (pausedSinceMs > 0L) {
            elapsed -= nowMs - pausedSinceMs
        }
        return if (elapsed < 0) 0 else elapsed
    }

    private fun pad(value: Long): String = if (value < 10) "0$value" else value.toString()
}
