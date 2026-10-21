package com.evan.voicesrs

/**
 * Mid-drill failure copy. The emulator dump joins node text truncated to 80
 * characters, so every visible string that the suite matches must lead with
 * [STOPPED] and stay short.
 */
object DrillErrors {
    const val STOPPED = "Drill stopped"
    const val ANKI = "Drill stopped: can't reach Anki"
    const val GENERIC = "Drill stopped. Something went wrong."
}
