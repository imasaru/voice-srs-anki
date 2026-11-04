package com.evan.voicesrs

/** h149a (item 61): "Rate Ns" countdown for the rating listen window. */
object RateCountdown {
    /** Whole seconds left, rounded up; null when there is no deadline or it has passed. */
    fun secondsLeft(deadlineMs: Long?, nowMs: Long): Int? {
        if (deadlineMs == null) return null
        val left = deadlineMs - nowMs
        if (left <= 0L) return null
        return ((left + 999L) / 1000L).toInt()
    }

    fun text(seconds: Int): String = "Rate ${seconds}s"

    /** Shown only while drilling, not silent, not chat-paused, answer showing, and time left. */
    fun visible(drilling: Boolean, silentOn: Boolean, chatPaused: Boolean, showBack: Boolean, secondsLeft: Int?): Boolean =
        drilling && !silentOn && !chatPaused && showBack && secondsLeft != null
}
