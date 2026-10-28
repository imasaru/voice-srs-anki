package com.evan.voicesrs

/** Pure: what to do with the recognizer when the mic route may have changed. */
object RouteRebuild {
    enum class Action { REBUILD, NONE }

    fun onListen(routeChanged: Boolean): Action =
        if (routeChanged) Action.REBUILD else Action.NONE
}
