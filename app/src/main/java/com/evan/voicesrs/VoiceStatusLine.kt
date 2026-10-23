package com.evan.voicesrs

/**
 * The one plain line on the home card. Not a chip and not tappable.
 * Names the engine that will actually speak: Edge only when it is selected
 * and healthy, otherwise the on-device voice.
 */
object VoiceStatusLine {
    const val FALLBACK_NOTE = "· falls back to device voice offline"
    const val DEVICE_DEFAULT = "System default"

    /** en-US-AriaNeural -> Aria. An unrecognised id is shown as stored. */
    fun edgeDisplayName(voiceId: String): String {
        val id = voiceId.trim().ifBlank { NeuralTts.DEFAULT_VOICE }
        val tail = id.substringAfterLast('-')
        val short = tail.removeSuffix("Neural").removeSuffix("Multilingual")
        return short.ifBlank { id }
    }

    fun deviceDisplayName(stored: String): String =
        stored.trim().ifBlank { DEVICE_DEFAULT }

    /** Edge only when the user asked for it and /health succeeded. */
    fun engine(preferred: EngineChoice, edgeHealthy: Boolean): EngineChoice =
        NeuralTts.sessionEngine(preferred, edgeHealthy)

    /**
     * Edge when it is selected and not known to be down.
     * A failed probe, or an on-device preference, uses the device voice.
     */
    fun resolve(preferred: EngineChoice, edgeHealthy: Boolean?): EngineChoice {
        if (preferred != EngineChoice.NEURAL) return EngineChoice.ON_DEVICE
        if (edgeHealthy == false) return EngineChoice.ON_DEVICE
        return EngineChoice.NEURAL
    }

    fun format(engine: EngineChoice, voiceDisplayName: String): String {
        val name = if (engine == EngineChoice.NEURAL) {
            edgeDisplayName(voiceDisplayName)
        } else {
            deviceDisplayName(voiceDisplayName)
        }
        return if (engine == EngineChoice.NEURAL) {
            "🔊 Voice: $name (Edge, natural) $FALLBACK_NOTE"
        } else {
            "🔊 Voice: $name (device)"
        }
    }

    /** "Done 4 · Left 16" from a session line. Blank when the drill is idle. */
    fun compactCounts(progressLine: String): String {
        if (progressLine.isBlank()) return ""
        val parts = progressLine.split(" · ")
        val done = parts.firstOrNull { it.startsWith("Done ") }
        val left = parts.firstOrNull { it.startsWith("Left ") }
        return listOfNotNull(done, left).joinToString(" · ")
    }

    /** Narrow header: counts, session goal, then the voice line. */
    fun headerLine(progressLine: String, voiceLine: String, goalLine: String = ""): String {
        return listOf(compactCounts(progressLine), goalLine.trim(), voiceLine.trim())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}
