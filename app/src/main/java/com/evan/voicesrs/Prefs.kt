package com.evan.voicesrs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AnkiConfig(
    val url: String,
    val apiKey: String,
    val deck: String,
    /** Speech rate, clamped to [TtsRate.MIN]..[TtsRate.MAX]. Slider covers up to [TtsRate.SLIDER_MAX]. */
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
    /** Seconds to wait for speech to start. One of [ListenWindow.WAIT_SEC_CHOICES]. */
    val listenWaitSec: Int = ListenWindow.DEFAULT_WAIT_SEC,
    /** End-of-speech silence hint, in milliseconds. One of [ListenWindow.SILENCE_MS_CHOICES]. */
    val endSilenceMs: Int = ListenWindow.DEFAULT_SILENCE_MS,
    /** How many times to listen for an answer or a grade before auto-silent. 1–5, default 3. */
    val listenAttempts: Int = ListenWindow.DEFAULT_ATTEMPTS,
    /** Seconds to think before the answer is read. 0 is Off. Default 20. Not used in Silent mode. */
    val thinkTimeSec: Int = ThinkTime.DEFAULT_SEC,
    /** Show the card front and back on the main screen. */
    val showCard: Boolean = true,
    /** Highlight the sentence currently being read. Settings-only; no highlight logic yet. */
    val highlightSentence: Boolean = HighlightSentence.SETTING_DEFAULT,
    /** Mix MCQ rows. Letters stay on their original text. On by default. */
    val shuffleOptions: Boolean = true,
    /** Tap-only review. Read once at session start. The microphone stays closed. */
    val silentMode: Boolean = false,
    /** Fetch a batch and grade locally. Off by default. */
    val offlineBatch: Boolean = false,
    val offlineBatchSize: Int = 20,
    /** [GoalModes.OFF], [GoalModes.CARDS], or [GoalModes.MINUTES]. */
    val goalMode: String = GoalModes.OFF,
    val goalCards: Int = 20,
    val goalMinutes: Int = 10,
    /** True when the Anki key is blank after migration. Settings should say so. */
    val needsApiKey: Boolean = false,
    val needsNeuralToken: Boolean = false,
    /** Master switch for AI explanations. Off until the user turns it on. */
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = "http://example.local/v1",
    val aiModel: String = "scottlowry/Ornith-1.5-35B-A3B-oQ5e-mtp:precise",
    /** Optional. Stored only in encrypted prefs. Never logged. */
    val aiApiKey: String = "",
    val aiLookahead: Int = 2,
    val aiBatch: Boolean = false,
    /** Ease values (1–4) that generate an explanation after a grade. */
    val autoExplainEases: Set<Int> = AutoExplain.DEFAULT,
    /** Wrap detected code on card faces. Display only. */
    val detectCode: Boolean = true
)

/** SharedPreferences-backed settings. The API key and neural token live in encrypted prefs. */
object Prefs {
    private const val TAG = "Prefs"
    private const val FILE = "voicesrs"
    private const val SECRET_FILE = "voicesrs_secrets"
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
    private const val KEY_LISTEN_WAIT_SEC = "listen_wait_sec"
    private const val KEY_END_SILENCE_MS = "end_silence_ms"
    private const val KEY_RELISTEN_ONCE = "relisten_once"
    private const val KEY_LISTEN_ATTEMPTS = "listen_attempts"
    private const val KEY_THINK_TIME_SEC = "think_time_sec"
    private const val KEY_SHOW_CARD = "show_card"
    private const val KEY_HIGHLIGHT_SENTENCE = "highlight_sentence"
    private const val KEY_SHUFFLE = "shuffle_options"
    private const val KEY_SILENT = "silent_mode"
    private const val KEY_OFFLINE = "offline_batch"
    private const val KEY_BATCH_SIZE = "offline_batch_size"
    private const val KEY_GOAL_MODE = "goal_mode"
    private const val KEY_GOAL_CARDS = "goal_cards"
    private const val KEY_GOAL_MINUTES = "goal_minutes"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_AI_URL = "ai_base_url"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_AI_KEY = "ai_api_key"
    private const val KEY_AI_LOOKAHEAD = "ai_lookahead"
    private const val KEY_AI_BATCH = "ai_batch"
    private const val KEY_AUTO_EXPLAIN_EASES = "auto_explain_eases"
    private const val KEY_AUTO_EXPLAIN = "auto_explain"
    private const val KEY_DETECT_CODE = "detect_code"

    const val DEFAULT_URL = "http://192.0.2.10:8765"
    const val DEFAULT_DECK = "VoiceSRS-Test"
    const val DEFAULT_NEURAL_URL = "http://192.0.2.10:8766"
    const val DEFAULT_BATCH_SIZE = 20
    const val DEFAULT_GOAL_CARDS = 20
    const val DEFAULT_GOAL_MINUTES = 10
    const val DEFAULT_AI_URL = "http://example.local/v1"
    const val DEFAULT_AI_MODEL = "scottlowry/Ornith-1.5-35B-A3B-oQ5e-mtp:precise"
    const val DEFAULT_AI_LOOKAHEAD = 2

    /**
     * Build-time default from the gitignored local-api-key.txt.
     * Empty when that file is missing (CI). Not a source literal.
     */
    val DEFAULT_API_KEY: String
        get() = BuildConfig.ANKI_API_KEY

    fun load(ctx: Context): AnkiConfig {
        val plain = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val secrets = openSecrets(ctx)
        val decision = SecretMigration.decide(
            SecretInputs(
                encryptedApiKey = stored(secrets, KEY_API_KEY),
                encryptedNeuralToken = stored(secrets, KEY_NEURAL_TOKEN),
                plainApiKey = stored(plain, KEY_API_KEY),
                plainNeuralToken = stored(plain, KEY_NEURAL_TOKEN),
                buildApiKey = BuildConfig.ANKI_API_KEY,
                buildNeuralToken = BuildConfig.EDGE_TTS_TOKEN
            )
        )
        if (secrets != null) {
            val editor = secrets.edit()
            var secretDirty = false
            if (decision.writeApiKey) {
                editor.putString(KEY_API_KEY, decision.apiKey)
                secretDirty = true
            }
            if (decision.writeNeuralToken) {
                editor.putString(KEY_NEURAL_TOKEN, decision.neuralToken)
                secretDirty = true
            }
            if (secretDirty) editor.apply()
            val plainEditor = plain.edit()
            var plainDirty = false
            if (decision.deletePlainApiKey && plain.contains(KEY_API_KEY)) {
                plainEditor.remove(KEY_API_KEY)
                plainDirty = true
            }
            if (decision.deletePlainNeuralToken && plain.contains(KEY_NEURAL_TOKEN)) {
                plainEditor.remove(KEY_NEURAL_TOKEN)
                plainDirty = true
            }
            if (plainDirty) plainEditor.apply()
        }
        return read(plain).copy(
            apiKey = decision.apiKey,
            neuralToken = decision.neuralToken,
            needsApiKey = decision.promptForApiKey,
            needsNeuralToken = decision.promptForNeuralToken,
            aiApiKey = stored(secrets, KEY_AI_KEY).orEmpty()
        )
    }

    fun read(p: SharedPreferences): AnkiConfig = readPlain(p)

    fun write(p: SharedPreferences, cfg: AnkiConfig) {
        p.edit()
            .putString(KEY_URL, cfg.url)
            .putString(KEY_DECK, cfg.deck)
            .putFloat(KEY_TTS_RATE, TtsRate.clamp(cfg.ttsRate))
            .putString(KEY_TTS_VOICE, cfg.ttsVoice)
            .putString(KEY_TTS_ENGINE, cfg.ttsEngine.ifBlank { TtsEngines.NEURAL })
            .putString(KEY_NEURAL_URL, cfg.neuralUrl.ifBlank { DEFAULT_NEURAL_URL })
            .putString(KEY_NEURAL_VOICE, cfg.neuralVoice.ifBlank { NeuralTts.DEFAULT_VOICE })
            .putBoolean(KEY_SPEED_MODE, cfg.speedMode)
            .putInt(KEY_LISTEN_WAIT_SEC, ListenWindow.normalizeWaitSec(cfg.listenWaitSec))
            .putInt(KEY_END_SILENCE_MS, ListenWindow.normalizeSilenceMs(cfg.endSilenceMs))
            .putInt(KEY_LISTEN_ATTEMPTS, ListenWindow.clampAttempts(cfg.listenAttempts))
            .putInt(KEY_THINK_TIME_SEC, ThinkTime.normalize(cfg.thinkTimeSec))
            .remove(KEY_RELISTEN_ONCE)
            .putBoolean(KEY_SHOW_CARD, cfg.showCard)
            .putBoolean(KEY_HIGHLIGHT_SENTENCE, cfg.highlightSentence)
            .putBoolean(KEY_SHUFFLE, cfg.shuffleOptions)
            .putBoolean(KEY_SILENT, cfg.silentMode)
            .putBoolean(KEY_OFFLINE, cfg.offlineBatch)
            .putInt(KEY_BATCH_SIZE, cfg.offlineBatchSize.coerceIn(DrillSettings.MIN_BATCH, DrillSettings.MAX_BATCH))
            .putString(KEY_GOAL_MODE, GoalModes.normalize(cfg.goalMode))
            .putInt(KEY_GOAL_CARDS, cfg.goalCards.coerceIn(1, 500))
            .putInt(KEY_GOAL_MINUTES, cfg.goalMinutes.coerceIn(1, 180))
            .putBoolean(KEY_AI_ENABLED, cfg.aiEnabled)
            .putString(KEY_AI_URL, cfg.aiBaseUrl.ifBlank { DEFAULT_AI_URL })
            .putString(KEY_AI_MODEL, cfg.aiModel.ifBlank { DEFAULT_AI_MODEL })
            .putInt(KEY_AI_LOOKAHEAD, cfg.aiLookahead.coerceIn(0, 5))
            .putBoolean(KEY_AI_BATCH, cfg.aiBatch)
            .putString(KEY_AUTO_EXPLAIN_EASES, AutoExplain.encode(cfg.autoExplainEases))
            .putBoolean(KEY_DETECT_CODE, cfg.detectCode)
            .remove(KEY_AUTO_EXPLAIN)
            .remove(KEY_API_KEY)
            .remove(KEY_NEURAL_TOKEN)
            .apply()
    }

    fun save(ctx: Context, cfg: AnkiConfig) {
        val plain = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        write(plain, cfg)
        val secrets = openSecrets(ctx)
        if (secrets == null) {
            Log.e(TAG, "encrypted prefs unavailable; secrets not written")
        } else {
            secrets.edit()
                .putString(KEY_API_KEY, cfg.apiKey.trim())
                .putString(KEY_NEURAL_TOKEN, cfg.neuralToken.trim())
                .putString(KEY_AI_KEY, cfg.aiApiKey.trim())
                .apply()
        }
        try {
            ExplainBatchSchedule.sync(ctx, cfg)
        } catch (_: Exception) {
        }
    }

    private fun readPlain(p: SharedPreferences): AnkiConfig {
        return AnkiConfig(
            url = p.getString(KEY_URL, DEFAULT_URL).orEmpty().ifBlank { DEFAULT_URL },
            apiKey = "",
            deck = p.getString(KEY_DECK, DEFAULT_DECK).orEmpty().ifBlank { DEFAULT_DECK },
            ttsRate = TtsRate.clamp(p.getFloat(KEY_TTS_RATE, TtsRate.DEFAULT)),
            ttsVoice = p.getString(KEY_TTS_VOICE, "").orEmpty(),
            ttsEngine = p.getString(KEY_TTS_ENGINE, TtsEngines.NEURAL).orEmpty().ifBlank { TtsEngines.NEURAL },
            neuralUrl = p.getString(KEY_NEURAL_URL, DEFAULT_NEURAL_URL).orEmpty().ifBlank { DEFAULT_NEURAL_URL },
            neuralToken = "",
            neuralVoice = p.getString(KEY_NEURAL_VOICE, NeuralTts.DEFAULT_VOICE).orEmpty()
                .ifBlank { NeuralTts.DEFAULT_VOICE },
            speedMode = p.getBoolean(KEY_SPEED_MODE, false),
            listenWaitSec = ListenWindow.normalizeWaitSec(
                p.getInt(KEY_LISTEN_WAIT_SEC, ListenWindow.DEFAULT_WAIT_SEC)
            ),
            endSilenceMs = ListenWindow.normalizeSilenceMs(
                p.getInt(KEY_END_SILENCE_MS, ListenWindow.DEFAULT_SILENCE_MS)
            ),
            listenAttempts = ListenWindow.migrateAttempts(
                stored = if (p.contains(KEY_LISTEN_ATTEMPTS)) {
                    p.getInt(KEY_LISTEN_ATTEMPTS, ListenWindow.DEFAULT_ATTEMPTS)
                } else {
                    null
                },
                legacyOn = if (p.contains(KEY_RELISTEN_ONCE)) p.getBoolean(KEY_RELISTEN_ONCE, true) else null
            ),
            thinkTimeSec = ThinkTime.normalize(
                p.getInt(KEY_THINK_TIME_SEC, ThinkTime.DEFAULT_SEC)
            ),
            showCard = p.getBoolean(KEY_SHOW_CARD, true),
            highlightSentence = p.getBoolean(KEY_HIGHLIGHT_SENTENCE, HighlightSentence.SETTING_DEFAULT),
            shuffleOptions = p.getBoolean(KEY_SHUFFLE, true),
            silentMode = p.getBoolean(KEY_SILENT, false),
            offlineBatch = p.getBoolean(KEY_OFFLINE, false),
            offlineBatchSize = p.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE)
                .coerceIn(DrillSettings.MIN_BATCH, DrillSettings.MAX_BATCH),
            goalMode = GoalModes.normalize(p.getString(KEY_GOAL_MODE, GoalModes.OFF)),
            goalCards = p.getInt(KEY_GOAL_CARDS, DEFAULT_GOAL_CARDS).coerceIn(1, 500),
            goalMinutes = p.getInt(KEY_GOAL_MINUTES, DEFAULT_GOAL_MINUTES).coerceIn(1, 180),
            aiEnabled = p.getBoolean(KEY_AI_ENABLED, false),
            aiBaseUrl = p.getString(KEY_AI_URL, DEFAULT_AI_URL).orEmpty().ifBlank { DEFAULT_AI_URL },
            aiModel = p.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL).orEmpty().ifBlank { DEFAULT_AI_MODEL },
            aiLookahead = p.getInt(KEY_AI_LOOKAHEAD, DEFAULT_AI_LOOKAHEAD).coerceIn(0, 5),
            aiBatch = p.getBoolean(KEY_AI_BATCH, false),
            autoExplainEases = AutoExplain.migrate(
                stored = if (p.contains(KEY_AUTO_EXPLAIN_EASES)) p.getString(KEY_AUTO_EXPLAIN_EASES, null) else null,
                legacy = if (p.contains(KEY_AUTO_EXPLAIN)) p.getBoolean(KEY_AUTO_EXPLAIN, true) else null
            ),
            detectCode = p.getBoolean(KEY_DETECT_CODE, CodeDetect.SETTING_DEFAULT)
        )
    }

    /** Null when the preference is absent. Blank is a stored empty value. */
    private fun stored(prefs: SharedPreferences?, key: String): String? {
        if (prefs == null || !prefs.contains(key)) return null
        return prefs.getString(key, "").orEmpty()
    }

    private fun openSecrets(ctx: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                SECRET_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "encrypted prefs unavailable", e)
            null
        }
    }
}
