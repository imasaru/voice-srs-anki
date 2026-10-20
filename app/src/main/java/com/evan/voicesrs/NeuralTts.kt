package com.evan.voicesrs

import org.json.JSONArray
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/** Stored engine preference. Anything other than [DEVICE] means neural. */
object TtsEngines {
    const val NEURAL = "neural"
    const val DEVICE = "device"
    const val LABEL_NEURAL = "Neural"
    const val LABEL_DEVICE = "On-device"
    const val CHOICE_NEURAL = "Neural (Edge, via example.local)"
    const val CHOICE_DEVICE = "On-device"

    fun preferred(stored: String?): EngineChoice {
        return if (stored?.trim() == DEVICE) EngineChoice.ON_DEVICE else EngineChoice.NEURAL
    }

    /** Per-utterance fallback. On-device has nowhere else to go. */
    fun fallback(failed: EngineChoice): EngineChoice? {
        return if (failed == EngineChoice.NEURAL) EngineChoice.ON_DEVICE else null
    }
}

enum class EngineChoice { NEURAL, ON_DEVICE }

/**
 * Pure helpers for the Edge neural server.
 * The server maps a float multiplier to edge "+N%" and clamps 0.5–2.0.
 * The slider value is sent as-is after the same clamp.
 */
object NeuralTts {
    const val DEFAULT_VOICE = "en-US-AriaNeural"
    const val MIN_RATE = 0.5f
    const val MAX_RATE = 2.0f
    const val CONNECT_TIMEOUT_MS = 3_000
    const val READ_TIMEOUT_MS = 15_000
    /** GET /health budget. Connect and read both use this. */
    const val HEALTH_TIMEOUT_MS = 3_000
    /** After a failed check, try again on a later speak instead of every utterance. */
    const val HEALTH_RECHECK_MS = 45_000L

    /**
     * Neural is used only when the user asked for it and /health succeeded.
     * A failed check stays on-device until a later check passes.
     */
    fun sessionEngine(preferred: EngineChoice, healthOk: Boolean): EngineChoice {
        return if (preferred == EngineChoice.NEURAL && healthOk) {
            EngineChoice.NEURAL
        } else {
            EngineChoice.ON_DEVICE
        }
    }

    /**
     * Probe at drill start, and again after [recheckMs] while the session is
     * stuck on-device because the last check failed.
     */
    fun shouldProbeNeural(
        preferred: EngineChoice,
        healthKnown: Boolean,
        blocked: Boolean,
        elapsedSinceProbeMs: Long,
        recheckMs: Long = HEALTH_RECHECK_MS
    ): Boolean {
        if (preferred != EngineChoice.NEURAL) return false
        if (!healthKnown) return true
        return blocked && elapsedSinceProbeMs >= recheckMs
    }
    const val MAX_TEXT = 3000
    const val MAX_CACHE_FILES = 24

    val FALLBACK_VOICES = listOf(
        "en-US-AriaNeural",
        "en-US-GuyNeural",
        "en-GB-SoniaNeural",
        "en-GB-RyanNeural",
        "ja-JP-NanamiNeural",
        "ja-JP-KeitaNeural"
    )

    /** Rate sent to POST /speak. Identity for the 0.5–1.5 slider. */
    fun serverRate(slider: Float): Float {
        if (slider.isNaN()) return 1f
        val clamped = slider.coerceIn(MIN_RATE, MAX_RATE)
        return (clamped * 100f).roundToInt() / 100f
    }

    /** Stable token so 0.9 and 0.90 share a cache entry. */
    fun rateToken(slider: Float): String =
        String.format(Locale.US, "%.2f", serverRate(slider))

    fun cacheKey(text: String, voice: String, rate: Float): String {
        val payload = "$voice|${rateToken(rate)}|$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            hex.append(String.format(Locale.US, "%02x", b))
        }
        return hex.toString()
    }

    fun parseVoices(json: String): List<NeuralVoice> {
        val arr = try {
            JSONArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<NeuralVoice>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            out.add(NeuralVoice(name, o.optString("locale"), o.optString("gender")))
        }
        return out
    }

    /** Names for the picker. Falls back to the built-in list and always includes the default. */
    fun pickerNames(remote: List<NeuralVoice>): List<String> {
        val names = remote.map { it.name }.filter { it.isNotBlank() }.distinct()
        val base = if (names.isEmpty()) FALLBACK_VOICES else names
        return if (DEFAULT_VOICE in base) base else listOf(DEFAULT_VOICE) + base
    }
}

data class NeuralVoice(val name: String, val locale: String, val gender: String)
