package com.evan.voicesrs

import android.content.Context

data class AnkiConfig(
    val url: String,
    val apiKey: String,
    val deck: String,
    /** TextToSpeech rate, clamped to [TtsRate.MIN]..[TtsRate.MAX]. */
    val ttsRate: Float = TtsRate.DEFAULT,
    /** On-device TextToSpeech voice name. Blank means the engine's system default. */
    val ttsVoice: String = "",
    /** [TtsEngines.NEURAL] or [TtsEngines.DEVICE]. */
    val ttsEngine: String = TtsEngines.NEURAL,
    val neuralUrl: String = "",
    val neuralToken: String = "",
    val neuralVoice: String = NeuralTts.DEFAULT_VOICE,
    /** Skip spoken rating guides and the "I heard" readback. */
    val speedMode: Boolean = false,
    /** Show the card front and back on the main screen. */
    val showCard: Boolean = true
)

/** SharedPreferences-backed AnkiConnect settings. */
object Prefs {
    private const val FILE = "voicesrs"
    private const val KEY_URL = "anki_url"
    private const val KEY_API_KEY = "anki_key"
    private const val KEY_DECK = "deck_name"
    private const val KEY_TTS_RATE = "tts_rate"
    private const val KEY_TTS_VOICE = "tts_voice"
    private const val KEY_TTS_ENGINE = "tts_engine"
    private const val KEY_NEURAL_URL = "neural_url"
    private const val KEY_NEURAL_TOKEN = "neural_token"
    private const val KEY_NEURAL_VOICE = "neural_voice"
    private const val KEY_SPEED_MODE = "speed_mode"
    private const val KEY_SHOW_CARD = "show_card"

    const val DEFAULT_URL = "http://192.0.2.10:8765"
    const val DEFAULT_DECK = "VoiceSRS-Test"
    const val DEFAULT_NEURAL_URL = "http://192.0.2.10:8766"

    /**
     * Build-time default from the gitignored local-api-key.txt.
     * Empty when that file is missing (CI). Not a source literal.
     */
    val DEFAULT_API_KEY: String
        get() = BuildConfig.ANKI_API_KEY

    fun load(ctx: Context): AnkiConfig {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return AnkiConfig(
            url = p.getString(KEY_URL, DEFAULT_URL).orEmpty().ifBlank { DEFAULT_URL },
            apiKey = p.getString(KEY_API_KEY, DEFAULT_API_KEY).orEmpty().ifBlank { DEFAULT_API_KEY },
            deck = p.getString(KEY_DECK, DEFAULT_DECK).orEmpty().ifBlank { DEFAULT_DECK },
            ttsRate = TtsRate.clamp(p.getFloat(KEY_TTS_RATE, TtsRate.DEFAULT)),
            ttsVoice = p.getString(KEY_TTS_VOICE, "").orEmpty(),
            ttsEngine = p.getString(KEY_TTS_ENGINE, TtsEngines.NEURAL).orEmpty().ifBlank { TtsEngines.NEURAL },
            neuralUrl = p.getString(KEY_NEURAL_URL, DEFAULT_NEURAL_URL).orEmpty().ifBlank { DEFAULT_NEURAL_URL },
            neuralToken = p.getString(KEY_NEURAL_TOKEN, BuildConfig.EDGE_TTS_TOKEN).orEmpty()
                .ifBlank { BuildConfig.EDGE_TTS_TOKEN },
            neuralVoice = p.getString(KEY_NEURAL_VOICE, NeuralTts.DEFAULT_VOICE).orEmpty()
                .ifBlank { NeuralTts.DEFAULT_VOICE },
            speedMode = p.getBoolean(KEY_SPEED_MODE, false),
            showCard = p.getBoolean(KEY_SHOW_CARD, true)
        )
    }

    fun save(ctx: Context, cfg: AnkiConfig) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, cfg.url)
            .putString(KEY_API_KEY, cfg.apiKey)
            .putString(KEY_DECK, cfg.deck)
            .putFloat(KEY_TTS_RATE, TtsRate.clamp(cfg.ttsRate))
            .putString(KEY_TTS_VOICE, cfg.ttsVoice)
            .putString(KEY_TTS_ENGINE, cfg.ttsEngine.ifBlank { TtsEngines.NEURAL })
            .putString(KEY_NEURAL_URL, cfg.neuralUrl.ifBlank { DEFAULT_NEURAL_URL })
            .putString(KEY_NEURAL_TOKEN, cfg.neuralToken.ifBlank { BuildConfig.EDGE_TTS_TOKEN })
            .putString(KEY_NEURAL_VOICE, cfg.neuralVoice.ifBlank { NeuralTts.DEFAULT_VOICE })
            .putBoolean(KEY_SPEED_MODE, cfg.speedMode)
            .putBoolean(KEY_SHOW_CARD, cfg.showCard)
            .apply()
    }
}
