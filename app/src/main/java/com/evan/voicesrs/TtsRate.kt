package com.evan.voicesrs

import kotlin.math.roundToInt

/**
 * Speech-rate mapping for the settings slider.
 * Range is 0.5x–1.5x in 0.1 steps. Default is 0.9x.
 * Pure Kotlin so the mapping can be unit-tested without Android.
 */
object TtsRate {
    const val MIN = 0.5f
    const val MAX = 1.5f
    const val DEFAULT = 0.9f
    const val STEP = 0.1f
    const val STEPS = 10

    fun clamp(rate: Float): Float {
        if (rate.isNaN()) return DEFAULT
        return rate.coerceIn(MIN, MAX)
    }

    /** Slider progress 0..[STEPS] for a speech rate. */
    fun toProgress(rate: Float): Int {
        val steps = ((clamp(rate) - MIN) / STEP).roundToInt()
        return steps.coerceIn(0, STEPS)
    }

    /** Speech rate for a slider progress, rounded to one decimal. */
    fun fromProgress(progress: Int): Float {
        val steps = progress.coerceIn(0, STEPS)
        val raw = MIN + steps * STEP
        return (raw * 10f).roundToInt() / 10f
    }
}
