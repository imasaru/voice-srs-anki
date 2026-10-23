package com.evan.voicesrs

/**
 * Keep the live question still while something above it grows.
 * Follow mode is the question on screen and the user still at the bottom.
 * A hold or a manual scroll away does not move. Streaming paints share one clock.
 */
object ScrollAnchor {
    const val COALESCE_MS = 100L

    fun following(atBottom: Boolean, holdingOrdinal: Int, liveVisible: Boolean): Boolean {
        return atBottom && holdingOrdinal == 0 && liveVisible
    }

    /**
     * Pixels to [android.view.View.scrollBy] so the question's top returns to
     * [beforeTop]. Positive when content above the question grew.
     */
    fun scrollDelta(following: Boolean, beforeTop: Int, afterTop: Int): Int {
        if (!following) return 0
        return afterTop - beforeTop
    }

    /**
     * Pixels to scroll so the question's top sits at [viewportTop].
     * The same formula covers a short card and a tall one.
     */
    fun pinTopDelta(questionTop: Int, viewportTop: Int = 0): Int = questionTop - viewportTop

    /** A final, non-streaming update always paints. Tokens share a 100 ms bucket. */
    fun paintNow(nowMs: Long, lastPaintMs: Long, streamingOnly: Boolean): Boolean {
        if (!streamingOnly) return true
        if (lastPaintMs < 0L) return true
        return nowMs - lastPaintMs >= COALESCE_MS
    }
}