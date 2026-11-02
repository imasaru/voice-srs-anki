package com.evan.voicesrs

/** What a media-button action does to a drill that is or is not chat-paused. */
object MediaBtnPlan {
    enum class Plan { PAUSE, RESUME, NONE }

    fun next(paused: Boolean, action: MediaKeys.Action): Plan = when (action) {
        MediaKeys.Action.IGNORE -> Plan.NONE
        MediaKeys.Action.TOGGLE -> if (paused) Plan.RESUME else Plan.PAUSE
    }
}
