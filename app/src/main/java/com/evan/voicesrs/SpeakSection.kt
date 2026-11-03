package com.evan.voicesrs

/** Section ids for whole-bubble read-aloud (negative so they never clash with explanation section indexes 0..n). */
object SpeakSection {
    const val CARD = -1
    const val USER = -2
    const val AI = -3
    const val ASK_QUESTION = -4
    const val ASK_REPLY = -5
}
