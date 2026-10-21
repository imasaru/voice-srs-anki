package com.evan.voicesrs

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Speech rate for the settings slider and the custom field.
 *
 * The slider covers 0.5×–1.5× in 0.1 steps (10 steps, 11 dots). A typed value
 * may go up to 4.0×. Above 1.5× the slider stays pinned at its end and the
 * label is marked custom. Stored and spoken on-device as a float in
 * [MIN]..[MAX]. Default is 0.9×. Pure Kotlin so the mapping can be
 * unit-tested without Android.
 *
 * The slider end is visual only. On-device speech still uses [MAX] (4.0×).
 * Edge neural does not speak the full custom range. See [NeuralTts.MAX_RATE]
 * (2.0×).
 */
object TtsRate {
    const val MIN = 0.5f
    /** Top of the slider. Rates above this are custom. */
    const val SLIDER_MAX = 1.5f
    /** Highest rate the custom field accepts, and the on-device speech cap. */
    const val MAX = 4.0f
    const val DEFAULT = 0.9f
    const val STEP = 0.1f
    /** Progress steps from [MIN] to [SLIDER_MAX]. 0..10 is 11 dots. */
    const val STEPS = 10

    private val NUMBER = Regex("""[+]?(?:\d+(?:\.\d+)?|\.\d+)""")

    /** Persist and speak this. Out of range snaps to the ends. NaN uses [DEFAULT]. */
    fun clamp(rate: Float): Float {
        if (rate.isNaN() || rate.isInfinite()) return DEFAULT
        return rate.coerceIn(MIN, MAX)
    }

    /** True when the saved rate sits past the slider and the label should say custom. */
    fun isCustom(rate: Float): Boolean = clamp(rate) > SLIDER_MAX

    /** Slider progress 0..[STEPS]. Rates above [SLIDER_MAX] pin at the end. */
    fun toProgress(rate: Float): Int {
        val pinned = if (rate.isNaN() || rate.isInfinite()) DEFAULT else rate.coerceIn(MIN, SLIDER_MAX)
        val steps = ((pinned - MIN) / STEP).roundToInt()
        return steps.coerceIn(0, STEPS)
    }

    /** Speech rate for a slider progress, rounded to one decimal. Never above [SLIDER_MAX]. */
    fun fromProgress(progress: Int): Float {
        val steps = progress.coerceIn(0, STEPS)
        val raw = MIN + steps * STEP
        return (raw * 10f).roundToInt() / 10f
    }

    /**
     * Typed custom rate, or null when it is not a number in [MIN]..[MAX].
     * Accepts a dot or a comma. Rounds to 0.1. A value that rounds outside the
     * range is rejected so the caller can keep the previous rate.
     */
    fun parse(raw: String): Float? {
        val text = raw.trim().replace(',', '.')
        if (!NUMBER.matches(text)) return null
        val value = text.toFloatOrNull() ?: return null
        if (!value.isFinite()) return null
        if (value < MIN - 0.001f || value > MAX + 0.001f) return null
        val rounded = (value * 10f).roundToInt() / 10f
        if (rounded < MIN || rounded > MAX) return null
        return rounded
    }

    /** One decimal, US dot, for the custom field. */
    fun format(rate: Float): String = String.format(Locale.US, "%.1f", clamp(rate))

    /** Floating value bubble for a slider step. US locale, one decimal, e.g. "1.2x". */
    fun label(progress: Int): String = format(fromProgress(progress)) + "x"
}
