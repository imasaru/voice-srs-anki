package com.evan.voicesrs

/** Headset / car media button -> drill action. Pure, no Android imports. */
object MediaKeys {
    enum class Action { TOGGLE, IGNORE }

    // Same values as android.view.KeyEvent
    const val KEYCODE_HEADSETHOOK = 79
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val DEBOUNCE_MS = 600L

    private val TOGGLE_KEYS = setOf(KEYCODE_HEADSETHOOK, KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_PLAY, KEYCODE_MEDIA_PAUSE)

    fun actionFor(keyCode: Int, isDown: Boolean, nowMs: Long, lastToggleMs: Long): Action {
        if (!isDown) return Action.IGNORE
        if (keyCode !in TOGGLE_KEYS) return Action.IGNORE
        if (lastToggleMs > 0L && nowMs - lastToggleMs < DEBOUNCE_MS) return Action.IGNORE
        return Action.TOGGLE
    }
}
