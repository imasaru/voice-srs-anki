package com.evan.voicesrs

/** FLAG_KEEP_SCREEN_ON while a drill runs. Off by default. */
object KeepScreenOn {
    const val SETTING_DEFAULT = false

    fun apply(drillRunning: Boolean, enabled: Boolean): Boolean = drillRunning && enabled
}
