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
 *
 * Effective max is 2.0×, which the server sends as "+100%".
 * The configured speech server clamps the float multiplier to
 * 0.5–2.0, then `pct = round((r - 1) * 100)` with a sign (`0.5×` is "-50%",
 * `1.0×` is "+0%", `2.0×` is "+100%"). The edge-tts library only checks
 * `^[+-]\d+%$` and has no numeric ceiling. Its maintainer documents Microsoft's
 * prosody range as 0.5×–2× the original audio, and the server enforces that.
 * A custom rate above 2.0× is therefore spoken at 2.0× on the neural path.
 * On-device [android.speech.tts.TextToSpeech.setSpeechRate] uses [TtsRate.MAX]
 * (4.0×) and is not limited here. The client clamp matches the server so the
 * cache key is the rate that is actually synthesized.
 */
object NeuralTts {
    const val DEFAULT_VOICE = "en-US-AriaNeural"
    const val MIN_RATE = 0.5f
    /** Server `rate_str` ceiling. 2.0× → "+100%". See the class KDoc. */
    const val MAX_RATE = 2.0f
    const val CONNECT_TIMEOUT_MS = 3_000
    const val READ_TIMEOUT_MS = 15_000
    /** GET /health budget. Connect and read both use this. */
    const val HEALTH_TIMEOUT_MS = 3_000
    /** After a failed check, try again on a later speak instead of every utterance. */
    const val HEALTH_RECHECK_MS = 45_000L

    /**
     * Engine to *try* for the next utterance. Neural only when the user asked
     * for it and /health succeeded. A failed check stays on-device until a
     * later check passes. The home-card label is not this value: it is set
     * from [EngineLabel.afterStart] when audio actually begins.
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

    /**
     * Rate sent to POST /speak. Clamped to the server's 0.5–2.0×.
     * Above [MAX_RATE] the server would reduce the value before synthesis.
     */
    fun serverRate(slider: Float): Float {
        if (slider.isNaN() || slider.isInfinite()) return 1f
        val clamped = slider.coerceIn(MIN_RATE, MAX_RATE)
        return (clamped * 100f).roundToInt() / 100f
    }

    /**
     * Percent string the Edge server's `rate_str` produces for [slider].
     * Effective range is "-50%".."+100%".
     */
    fun serverPercent(slider: Float): String {
        val pct = ((serverRate(slider) - 1f) * 100f).roundToInt()
        return String.format(Locale.US, "%+d%%", pct)
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

    /** 58E: /speak2 marks JSON array `[{"o":ms,"d":ms,"t":"word"}]` -> marks. Bad or missing input -> empty list. */
    fun parseMarks(json: String): List<ReadingTimeline.Mark> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val t = o.optString("t", "")
            if (t.isBlank()) null else ReadingTimeline.Mark(o.optLong("o", 0), o.optLong("d", 0), t)
        }
    } catch (_: Exception) { emptyList() }

    /** 58E: marks -> the same JSON array text, for the .marks.json cache file. */
    fun marksJson(marks: List<ReadingTimeline.Mark>): String {
        val arr = org.json.JSONArray()
        for (m in marks) arr.put(org.json.JSONObject().put("o", m.offsetMs).put("d", m.durationMs).put("t", m.word))
        return arr.toString()
    }
}

data class NeuralVoice(val name: String, val locale: String, val gender: String)
