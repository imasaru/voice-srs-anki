package com.evan.voicesrs

import java.net.URI

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
    const val ASR_PORT = 8791

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

    /**
     * Base URL for the ASR server, with no trailing slash.
     * A non-blank [asrUrl] wins. Otherwise the scheme and host of [neuralUrl]
     * with port [ASR_PORT]. Both blank, or an unusable neural URL, is null.
     */
    fun asrBase(asrUrl: String, neuralUrl: String): String? {
        val explicit = asrUrl.trim().trimEnd('/')
        if (explicit.isNotEmpty()) return explicit
        val neural = neuralUrl.trim()
        if (neural.isEmpty()) return null
        return try {
            val uri = URI(neural)
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            if (scheme.isBlank() || host.isBlank()) return null
            "$scheme://$host:$ASR_PORT"
        } catch (_: Exception) {
            null
        }
    }
}
