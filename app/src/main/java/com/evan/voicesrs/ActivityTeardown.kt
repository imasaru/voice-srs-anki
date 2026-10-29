package com.evan.voicesrs

/**
 * What MainActivity.onDestroy tears down. A configuration-change recreate
 * (split-screen resize, fold/unfold) must keep read-aloud playing: the
 * EdgeSpeaker player is shared and outlives the activity.
 */
object ActivityTeardown {
    /** h81 + p81a: keep read-aloud on a config-change recreate, and while DrillService drills (it owns that playback). */
    fun shouldStopPlayback(isChangingConfigurations: Boolean, drilling: Boolean = false): Boolean =
        !isChangingConfigurations && !drilling
}
