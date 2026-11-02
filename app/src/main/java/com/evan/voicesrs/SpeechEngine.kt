package com.evan.voicesrs

/**
 * Speech-engine preference. Named [SpeechChoice] because [EngineChoice] is the
 * TTS neural / on-device enum in [NeuralTts]. Stored as the enum name.
 */
enum class SpeechChoice { AUTO, WHISPER, VOSK, GOOGLE }

enum class EngineKind { WHISPER, VOSK, GOOGLE }

/**
 * Which speech engine to try, and how a listen is logged.
 * Pure: no Android types. [GOOGLE] is always last and always present.
 */
object SpeechEngine {
    const val PREF_KEY = "speech_engine"

    /** Unknown or null stored values are [SpeechChoice.AUTO]. */
    fun parse(stored: String?): SpeechChoice {
        val name = stored?.trim().orEmpty()
        if (name.isEmpty()) return SpeechChoice.AUTO
        return SpeechChoice.entries.firstOrNull { it.name == name } ?: SpeechChoice.AUTO
    }

    /**
     * Try order. AUTO and WHISPER share one chain: Whisper when the server was
     * reachable, then Vosk when its model is on disk, then Google.
     * VOSK skips Whisper. GOOGLE is Google alone.
     */
    fun order(choice: SpeechChoice, whisperReachable: Boolean, voskModelReady: Boolean): List<EngineKind> {
        val out = ArrayList<EngineKind>(3)
        when (choice) {
            SpeechChoice.GOOGLE -> Unit
            SpeechChoice.VOSK -> if (voskModelReady) out.add(EngineKind.VOSK)
            SpeechChoice.AUTO, SpeechChoice.WHISPER -> {
                if (whisperReachable) out.add(EngineKind.WHISPER)
                if (voskModelReady) out.add(EngineKind.VOSK)
            }
        }
        out.add(EngineKind.GOOGLE)
        return out
    }

    /** [tag] is the card tag, for example `n=9 id=123`. */
    fun logLine(kind: EngineKind, latencyMs: Long, tag: String, fellBackFrom: EngineKind?): String {
        val base = "engine=$kind latencyMs=$latencyMs $tag"
        return if (fellBackFrom == null) base else "$base fallbackFrom=$fellBackFrom"
    }

    /** Base URL for the ASR server, no trailing slash. Blank [asrUrl] = no server (null): there is no built-in default. */
    fun asrBase(asrUrl: String): String? = asrUrl.trim().trimEnd('/').ifEmpty { null }

    /** Vosk model download needs a configured server, a choice that uses Vosk, no model yet, and an unmetered network. */
    fun shouldFetchVosk(base: String?, choice: SpeechChoice, voskReady: Boolean, unmetered: Boolean): Boolean =
        base != null && !voskReady && unmetered && (choice == SpeechChoice.AUTO || choice == SpeechChoice.VOSK)
}
