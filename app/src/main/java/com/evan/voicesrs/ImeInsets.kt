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
}
