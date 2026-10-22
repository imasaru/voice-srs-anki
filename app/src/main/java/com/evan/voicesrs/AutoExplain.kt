package com.evan.voicesrs

/**
 * Which grades generate an explanation on their own.
 * Again and Hard are on until the learner changes the chips.
 * A legacy on/off switch, when that is all that was saved, maps to every grade
 * or to none. Ratings left off keep an Explain button.
 */
object AutoExplain {
    const val NOTE = "Other ratings get an Explain button instead."
    val CHIPS = listOf("Again", "Hard", "Good", "Easy")
    val DEFAULT = setOf(1, 2)

    fun shouldAuto(selected: Set<Int>, ease: Int): Boolean = ease in selected && ease in 1..4

    /** Hide the button while a reply exists or is on the way. An error can be asked again. */
    fun showButton(state: ExplState): Boolean {
        return state is ExplState.None || state is ExplState.Error
    }

    /**
     * A saved set wins. Otherwise the old boolean means every grade, or none.
     * Nothing saved is [DEFAULT].
     */
    fun migrate(stored: String?, legacy: Boolean?): Set<Int> {
        if (stored != null) return decode(stored)
        return when (legacy) {
            true -> setOf(1, 2, 3, 4)
            false -> emptySet()
            null -> DEFAULT
        }
    }

    fun decode(raw: String): Set<Int> {
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..4 }
            .toSet()
    }

    fun encode(eases: Set<Int>): String {
        return eases.filter { it in 1..4 }.toSortedSet().joinToString(",")
    }
}