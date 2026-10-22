package com.evan.voicesrs

/**
 * On-device TTS completion. Neural playback blocks inside its own player.
 * The on-device engine reports done/error/stop for every utterance it has
 * ever started, so a late callback from the previous utterance must not
 * release the latch for the one that is playing now. Both engines then
 * return from speak and enter the same listen.
 */
object SpeakLatch {
    fun isCurrent(activeId: String?, callbackId: String?): Boolean {
        return !activeId.isNullOrEmpty() && activeId == callbackId
    }
}
