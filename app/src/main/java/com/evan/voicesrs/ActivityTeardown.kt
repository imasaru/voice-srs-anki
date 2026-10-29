package com.evan.voicesrs

/**
 * What MainActivity.onDestroy tears down. A configuration-change recreate
 * (split-screen resize, fold/unfold) must keep read-aloud playing: the
 * EdgeSpeaker player is shared and outlives the activity.
 */
object ActivityTeardown {
    fun shouldStopPlayback(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations
}
