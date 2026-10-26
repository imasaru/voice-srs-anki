package com.evan.voicesrs

import org.json.JSONObject

/**
 * Plain-map view of voice-srs prefs for [SettingsJson]. Secret keys live in a
 * separate map and are merged only for export/import.
 */
object PrefsMap {
    /** Typed placeholders so [SettingsJson.parse] knows each key's type. */
    fun typedDefaults(): Map<String, Any?> = linkedMapOf(
        "anki_url" to "",
        "anki_key" to "",
        "deck_name" to "",
        "tts_rate" to 0f,
        "tts_voice" to "",
        "tts_engine" to "",
        "neural_url" to "",
        "neural_token" to "",
        "neural_voice" to "",
        "speed_mode" to false,
        "listen_wait_sec" to 0,
        "end_silence_ms" to 0,
        "listen_attempts" to 0,
        "think_time_sec" to 0,
        "show_card" to false,
        "highlight_sentence" to false,
        "shuffle_options" to false,
        "silent_mode" to false,
        "hands_free" to false,
        "hands_free_ready" to false,
        "offline_batch" to false,
        "offline_batch_size" to 0,
        "goal_mode" to "",
        "goal_cards" to 0,
        "goal_minutes" to 0,
        "ai_enabled" to false,
        "ai_base_url" to "",
        "ai_model" to "",
        "ai_api_key" to "",
        "ai_lookahead" to 0,
        "ai_batch" to false,
        "auto_explain_eases" to "",
        "decision_enabled" to false,
        "decision_url" to "",
        "decision_model" to "",
        "detect_code" to false,
        "generated_deck" to "",
        "keep_screen_on" to false
    )

    fun snapshot(plain: Map<String, *>, secrets: Map<String, *>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out.putAll(typedDefaults())
        copyInto(out, plain, secretsOnly = false)
        copyInto(out, secrets, secretsOnly = true)
        return out
    }

    fun knownForParse(current: Map<String, Any?>, fileText: String): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out.putAll(typedDefaults())
        out.putAll(current)
        try {
            val prefs = JSONObject(fileText).optJSONObject("prefs") ?: return out
            val keys = prefs.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (ExamStore.isExamKey(key) && key !in out) out[key] = ""
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun applyDelta(
        plain: MutableMap<String, Any?>,
        secrets: MutableMap<String, Any?>,
        delta: Map<String, Any?>
    ) {
        for ((key, value) in delta) {
            if (value == null) continue
            if (key in SettingsJson.SECRET_KEYS) secrets[key] = value else plain[key] = value
        }
    }

    private fun copyInto(out: MutableMap<String, Any?>, source: Map<String, *>, secretsOnly: Boolean) {
        for ((key, value) in source) {
            if (value == null) continue
            val secret = key in SettingsJson.SECRET_KEYS
            if (secretsOnly != secret) continue
            out[key] = value
        }
    }
}
