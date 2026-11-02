package com.evan.voicesrs

import kotlin.math.sqrt

/**
 * Energy VAD for one answer recording.
 * Quiet frames before the first speech never end the listen, and speech
 * itself has no duration cap. The listen ends only after [silenceMs] of
 * quiet once speech has started. Frames are 20 ms (320 samples at 16 kHz).
 */
class SilenceVad(
    private val thresholdRms: Double,
    private val silenceMs: Long
) {
    private var heardSpeech = false
    private var quietMs = 0L

    fun heardSpeech(): Boolean = heardSpeech

    /** True when this frame should end the recording. */
    fun feed(frame: ShortArray): Boolean {
        val loud = rms(frame) >= thresholdRms
        if (!heardSpeech) {
            if (loud) heardSpeech = true
            return false
        }
        if (loud) {
            quietMs = 0L
            return false
        }
        quietMs += frameMs(frame)
        return quietMs >= silenceMs
    }

    companion object {
        /** PCM16 RMS. Speech is well above this; a quiet room is not. */
        const val DEFAULT_THRESHOLD_RMS = 500.0
        const val SAMPLE_RATE = 16000

        fun rms(frame: ShortArray): Double {
            if (frame.isEmpty()) return 0.0
            var sum = 0.0
            for (sample in frame) {
                val v = sample.toDouble()
                sum += v * v
            }
            return sqrt(sum / frame.size)
        }

        fun frameMs(frame: ShortArray): Long {
            if (frame.isEmpty()) return 0L
            return frame.size * 1000L / SAMPLE_RATE
        }
    }
}
