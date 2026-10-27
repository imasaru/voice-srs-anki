package com.evan.voicesrs

/**
 * Pure helper for the bottom-padding math used by ThreadSheet's IME handling.
 * Kept free of Android types so it is unit-testable on the JVM.
 */
object ImeInsets {
    /**
     * Extra bottom padding to apply on the sheet root view.
     *
     * @param base the layout's original bottom padding (captured before the inset listener runs).
     * @param imeBottom the keyboard height reported by `WindowInsetsCompat.Type.ime()`.
     * @param navBottom the navigation-bar height from `WindowInsetsCompat.Type.navigationBars()`.
     * @return base + max(imeBottom, navBottom), so the input clears whichever edge overlay is taller.
     */
    fun bottomPadding(base: Int, imeBottom: Int, navBottom: Int): Int =
        base + maxOf(imeBottom, navBottom)

    /**
     * Bounds of the focused input in its own coordinate space, as
     * [left, top, right, bottom]. Used to build the non-empty Rect passed to
     * `View.requestRectangleOnScreen` so the input is scrolled into view above
     * the keyboard. Kept free of Android types so it is unit-testable on the JVM.
     */
    fun inputRect(width: Int, height: Int): IntArray = intArrayOf(0, 0, width, height)
}

/**
 * Decides which header rows to hide while the IME is up so the chat clears the
 * keyboard. Pure (no Android types) so it is unit-testable on the JVM. The deck
 * line (deckName/dueCount) is never touched and stays visible either way.
 */
object ImeLayoutDecision {
    /**
     * @param headerMeta hide the narrow-header meta line.
     * @param timerRow hide the timer row (only when false; otherwise keep whatever
     *   visibility it already had).
     * @param bottomBar hide the Again/Hard/Good/Easy + Stop/Settings bar.
     */
    data class Visibility(
        val headerMeta: Boolean,
        val timerRow: Boolean,
        val bottomBar: Boolean,
    )

    /**
     * @param imeVisible whether the keyboard is currently shown.
     * @param timerRowWanted when the IME is hidden, whether the timer row should
     *   stay visible (false keeps it hidden). Ignored while the IME is visible.
     */
    fun decide(imeVisible: Boolean, timerRowWanted: Boolean): Visibility =
        if (imeVisible) {
            Visibility(headerMeta = false, timerRow = false, bottomBar = false)
        } else {
            Visibility(headerMeta = true, timerRow = timerRowWanted, bottomBar = true)
        }

    /**
     * Which of the wide-layout (unfolded / w600dp) rows to hide while the IME is
     * up so the tall right chat pane clears the keyboard. The only rows that free
     * vertical space here are the toolbar and the session cue; the status card
     * lives in a side column and is irrelevant to chat height. Pure (no Android
     * types) so it is unit-testable on the JVM.
     */
    data class WideVisibility(
        val toolbar: Boolean,
        val sessionCue: Boolean,
    )

    /**
     * @param imeVisible whether the keyboard is currently shown.
     * @param cueWanted when the IME is hidden, whether the session cue should
     *   stay visible (false keeps it hidden). Ignored while the IME is visible.
     */
    fun decideWide(imeVisible: Boolean, cueWanted: Boolean): WideVisibility =
        if (imeVisible) {
            WideVisibility(toolbar = false, sessionCue = false)
        } else {
            WideVisibility(toolbar = true, sessionCue = cueWanted)
        }
}
