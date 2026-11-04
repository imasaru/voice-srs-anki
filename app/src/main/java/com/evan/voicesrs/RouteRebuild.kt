package com.evan.voicesrs

/** Pure: what to do with the recognizer when the mic route may have changed. */
object RouteRebuild {
    enum class Action { REBUILD, NONE }

    fun onListen(routeChanged: Boolean): Action =
        if (routeChanged) Action.REBUILD else Action.NONE

    /**
     * Whether to cancel and re-open a live listen once when the mic route
     * changed underneath it (e.g. a BT headset plugged in mid-answer). Only
     * ANSWER and RATING listens re-route the comm device; COMMAND/READ_ALOUD
     * never do. Capped at one reopen per listen: [alreadyReopened] short-circuits.
     */
    fun reopenOnRouteChange(
        kind: MicRouteTiming.Kind,
        changed: Boolean,
        alreadyReopened: Boolean,
    ): Boolean =
        (kind == MicRouteTiming.Kind.ANSWER || kind == MicRouteTiming.Kind.RATING) &&
            changed && !alreadyReopened
}
