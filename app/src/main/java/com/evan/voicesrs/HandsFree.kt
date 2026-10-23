package com.evan.voicesrs

/**
 * Hands-free auto-advance after a grade. Optional "Ready?" prompt.
 */
object HandsFree {
    const val SETTING_DEFAULT = true
    const val READY_DEFAULT = false
    const val READY_PROMPT = "Ready?"
    const val READY_WAIT_MS = 4_000L

    enum class Wait { NONE, READY_PROMPT, UNTIL_NEXT }

    fun autoAdvance(enabled: Boolean, chatPaused: Boolean): Boolean = enabled && !chatPaused

    fun prompt(enabled: Boolean, readyOn: Boolean): String? {
        if (!enabled || !readyOn) return null
        return READY_PROMPT
    }

    fun waitAfterGrade(handsFree: Boolean, readyPrompt: Boolean): Wait {
        if (!handsFree) return Wait.UNTIL_NEXT
        if (readyPrompt) return Wait.READY_PROMPT
        return Wait.NONE
    }

    fun waitMs(wait: Wait): Long = when (wait) {
        Wait.NONE -> 0L
        Wait.READY_PROMPT -> READY_WAIT_MS
        Wait.UNTIL_NEXT -> 30_000L
    }

    fun spokenFor(wait: Wait): String? = when (wait) {
        Wait.NONE -> null
        Wait.READY_PROMPT -> READY_PROMPT
        Wait.UNTIL_NEXT -> "Say next when you are ready."
    }

    fun accepted(heard: String?): Boolean {
        val text = heard?.trim()?.lowercase().orEmpty()
        if (text.isEmpty()) return false
        return text in setOf("yes", "yeah", "yep", "ready", "ok", "okay", "next", "go", "sure")
    }
}
