package com.evan.voicesrs

/**
 * Short engine names for logs. The home card does not show these as a chip.
 * It uses [VoiceStatusLine]. "Neural" only when that engine is configured and
 * the Edge server answered /health. A null health value means the probe has
 * not finished. Device mode does not wait on that probe.
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
}
