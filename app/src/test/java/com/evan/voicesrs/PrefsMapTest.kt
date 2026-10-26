package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsMapTest {

    private val plain = linkedMapOf<String, Any?>(
        "anki_url" to "http://anki.local:8091",
        "deck_name" to "VoiceSRS-Test",
        "speed_mode" to true,
        "listen_wait_sec" to 7,
        "tts_rate" to 1.25f,
        "show_card" to true,
        "exam_date:Driving" to "2026-11-07"
    )

    private val secrets = linkedMapOf<String, Any?>(
        "anki_key" to "secret-key",
        "neural_token" to "tok-123",
        "ai_api_key" to "ai-key"
    )

    @Test
    fun `export import round trip keeps types and leaves secrets in place`() {
        val snap = PrefsMap.snapshot(plain, secrets)
        val json = SettingsJson.export(snap, includeSecrets = false, appVersion = "0.1.22")
        assertFalse(json.contains("secret-key"))
        assertFalse(json.contains("tok-123"))
        assertFalse(json.contains("ai-key"))
        val known = PrefsMap.knownForParse(snap, json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        val newPlain = LinkedHashMap(plain)
        val newSecrets = LinkedHashMap(secrets)
        PrefsMap.applyDelta(newPlain, newSecrets, parsed.prefs)
        assertEquals("http://anki.local:8091", newPlain["anki_url"])
        assertEquals("VoiceSRS-Test", newPlain["deck_name"])
        assertEquals(true, newPlain["speed_mode"])
        assertEquals(7, newPlain["listen_wait_sec"])
        assertEquals(1.25f, newPlain["tts_rate"])
        assertEquals("2026-11-07", newPlain["exam_date:Driving"])
        assertEquals("secret-key", newSecrets["anki_key"])
        assertEquals("tok-123", newSecrets["neural_token"])
        assertEquals("ai-key", newSecrets["ai_api_key"])
        assertFalse(newPlain.containsKey("anki_key"))
    }

    @Test
    fun `a file without keys keeps the current secrets`() {
        val json = SettingsJson.export(
            mapOf("anki_url" to "http://other:8091", "anki_key" to "new-key"),
            includeSecrets = false
        )
        val known = PrefsMap.knownForParse(PrefsMap.snapshot(plain, secrets), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        assertFalse(parsed.prefs.containsKey("anki_key"))
        val newPlain = LinkedHashMap(plain)
        val newSecrets = LinkedHashMap(secrets)
        PrefsMap.applyDelta(newPlain, newSecrets, parsed.prefs)
        assertEquals("http://other:8091", newPlain["anki_url"])
        assertEquals("secret-key", newSecrets["anki_key"])
    }

    @Test
    fun `opt-in secrets in the file replace the current keys`() {
        val snap = PrefsMap.snapshot(plain, secrets)
        val json = SettingsJson.export(snap, includeSecrets = true)
        val parsed = SettingsJson.parse(json, PrefsMap.knownForParse(snap, json))
            as SettingsJson.ImportResult.Ok
        assertTrue(parsed.hadSecrets)
        val newSecrets = linkedMapOf<String, Any?>("anki_key" to "old")
        val newPlain = LinkedHashMap<String, Any?>()
        PrefsMap.applyDelta(newPlain, newSecrets, parsed.prefs)
        assertEquals("secret-key", newSecrets["anki_key"])
        assertEquals("tok-123", newSecrets["neural_token"])
    }

    @Test
    fun `exam keys in the file are kept even when the current map has none`() {
        val json = SettingsJson.export(
            mapOf(
                "anki_url" to "http://anki.local:8091",
                "exam_date:Driving" to "2026-11-07",
                "exam_target:Driving" to "0.93"
            )
        )
        val known = PrefsMap.knownForParse(PrefsMap.typedDefaults(), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        assertEquals("2026-11-07", parsed.prefs["exam_date:Driving"])
        assertEquals("0.93", parsed.prefs["exam_target:Driving"])
        val newPlain = LinkedHashMap<String, Any?>()
        val newSecrets = LinkedHashMap<String, Any?>()
        PrefsMap.applyDelta(newPlain, newSecrets, parsed.prefs)
        assertEquals("2026-11-07", newPlain["exam_date:Driving"])
        val state = ExamStore.read(newPlain, "Driving")
        assertEquals("2026-11-07", state.date.toString())
        assertEquals(0.93, state.target!!, 1e-9)
    }
}
