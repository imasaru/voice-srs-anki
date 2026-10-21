package com.evan.voicesrs

/**
 * Pure auto-save decisions. No Android types.
 *
 * [decide] keeps the last good value when the field is invalid or unchanged.
 * [Debounce] folds real saves that land inside [DEBOUNCE_MS] into one notice.
 * Text fields must not call this per keystroke; the screen commits them on
 * focus loss, IME Done, and leaving.
 */
object AutoSave {
    const val DEBOUNCE_MS = 800L

    data class Decision<T>(
        /** True only when [value] should be written. */
        val save: Boolean,
        /** Value to keep. Invalid input leaves the previous good value. */
        val value: T,
        /** True when [parsed] was rejected. The caller shows the field's existing error. */
        val invalid: Boolean
    )

    /**
     * [parsed] is null when validation failed.
     * A value equal to [lastGood] is not a save.
     */
    fun <T> decide(lastGood: T, parsed: T?): Decision<T> {
        if (parsed == null) return Decision(save = false, value = lastGood, invalid = true)
        if (parsed == lastGood) return Decision(save = false, value = lastGood, invalid = false)
        return Decision(save = true, value = parsed, invalid = false)
    }

    /**
     * Trailing window for the "Saved" notice.
     * The first save in a burst arms [deadline]. Later saves before [take]
     * push that deadline out and do not start a second notice.
     */
    class Debounce(private val windowMs: Long = DEBOUNCE_MS) {
        private var pendingAtMs: Long? = null

        /**
         * @return the notice time when this save starts a burst, or null when
         * it joins the notice already pending. [deadline] still moves either way.
         */
        fun onSaved(nowMs: Long): Long? {
            val showAt = nowMs + windowMs
            val starting = pendingAtMs == null
            pendingAtMs = showAt
            return if (starting) showAt else null
        }

        fun deadline(): Long? = pendingAtMs

        /** True once, after [nowMs] reaches the coalesced deadline. */
        fun take(nowMs: Long): Boolean {
            val due = pendingAtMs ?: return false
            if (nowMs < due) return false
            pendingAtMs = null
            return true
        }
    }

    /**
     * Arm [debounce] only when a save actually happened.
     * @return the single pending notice time, or null when nothing should be announced.
     */
    fun schedule(debounce: Debounce, nowMs: Long, save: Boolean): Long? {
        if (!save) return null
        debounce.onSaved(nowMs)
        return debounce.deadline()
    }
}
