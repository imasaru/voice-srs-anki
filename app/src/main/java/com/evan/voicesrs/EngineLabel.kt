package com.evan.voicesrs

/**
 * Short engine names for logs. The home card does not show these as a chip.
 * It uses [VoiceStatusLine].
 *
 * [of] is the pre-playback guess from the stored preference and /health.
 * Once audio begins, [afterStart] names the engine that actually started
 * this utterance. A failed engine that never starts leaves the label as it
 * was, so a neural miss followed by on-device start shows on-device, and the
 * next utterance that neural starts shows neural again.
 */
object EngineLabel {
    const val NEURAL = "Neural"
    const val DEVICE = "Device voice"
    const val CHECKING = "Checking…"

    fun of(configured: EngineChoice, healthy: Boolean?): String {
        if (configured != EngineChoice.NEURAL) return DEVICE
        return when (healthy) {
            null -> CHECKING
            true -> NEURAL
            false -> DEVICE
        }
    }

    /** Label for the engine that began this utterance. */
    fun forPlayback(engine: EngineChoice): String =
        if (engine == EngineChoice.NEURAL) NEURAL else DEVICE

    /**
     * Engine that actually started audio for this utterance.
     * [attempted] is tried first. When it never starts, the fallback from
     * [TtsEngines.fallback] is used only if [fallbackStarted] is true.
     * Both missing → null, so the previous label is kept.
     */
    fun startedEngine(
        attempted: EngineChoice,
        started: Boolean,
        fallbackStarted: Boolean = false
    ): EngineChoice? {
        if (started) return attempted
        if (!fallbackStarted) return null
        return TtsEngines.fallback(attempted)
    }

    /** Apply a playback-start. [started] null means no engine began audio. */
    fun afterStart(current: String, started: EngineChoice?): String {
        if (started == null) return current
        return forPlayback(started)
    }
}
