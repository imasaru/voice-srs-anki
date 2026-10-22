package com.evan.voicesrs

/**
 * Colours for the rating pill on the user bubble.
 * Again and Good are the approved pairs. Hard is orange. Easy is blue.
 */
object RatingPill {
    data class Colors(val background: Int, val text: Int)

    const val AGAIN_BG = 0xFFF2B8B5.toInt()
    const val AGAIN_FG = 0xFF601410.toInt()
    const val HARD_BG = 0xFFFFDCC2.toInt()
    const val HARD_FG = 0xFF5C3100.toInt()
    const val GOOD_BG = 0xFFC8F0D4.toInt()
    const val GOOD_FG = 0xFF0B3D1F.toInt()
    const val EASY_BG = 0xFFD3E4FE.toInt()
    const val EASY_FG = 0xFF062E6F.toInt()

    fun of(ease: Int?): Colors? = when (ease) {
        1 -> Colors(AGAIN_BG, AGAIN_FG)
        2 -> Colors(HARD_BG, HARD_FG)
        3 -> Colors(GOOD_BG, GOOD_FG)
        4 -> Colors(EASY_BG, EASY_FG)
        else -> null
    }
}
