package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

class H127Test {
    @Test
    fun `typedDefaults has the newer keys with load defaults`() {
        val d = PrefsMap.typedDefaults()
        assertEquals("", d["asr_servers"]); assertEquals("EN", d["asr_language"])
        assertEquals("MEDIA", d["read_aloud_channel"]); assertEquals("", d["judge_strictness"])
        assertEquals(false, d["letter_listen"]); assertEquals(false, d["exam_apply_to_anki"])
        assertEquals(true, d["auto_accept_rating"]); assertEquals("", d["prompt_chips"])
        assertEquals("", d["asr_url"])
    }
    @Test
    fun `fresh install import keeps the newer keys`() {
        val plain = linkedMapOf<String, Any?>(
            "asr_servers" to "http://a:9000\nhttp://b:9000", "asr_language" to "JA",
            "read_aloud_channel" to "CALL", "judge_strictness" to "strict",
            "letter_listen" to true, "exam_apply_to_anki" to true,
            "auto_accept_rating" to false, "prompt_chips" to "[]"
        )
        val json = SettingsJson.export(PrefsMap.snapshot(plain, emptyMap<String, Any?>()))
        val known = PrefsMap.knownForParse(PrefsMap.typedDefaults(), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        for (key in plain.keys) { assertEquals(plain[key], parsed.prefs[key]); assertFalse(parsed.skipped.contains(key)) }
    }
    @Test
    fun `unknown keys are still skipped`() {
        val json = """{"app":"voice-srs","schemaVersion":1,"prefs":{"bogus_key":"x","asr_language":"AUTO"}}"""
        val known = PrefsMap.knownForParse(PrefsMap.typedDefaults(), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        assertTrue(parsed.skipped.contains("bogus_key"))
        assertEquals("AUTO", parsed.prefs["asr_language"])
        assertFalse(parsed.prefs.containsKey("bogus_key"))
    }
    @Test
    fun `wrong type is skipped`() {
        val json = """{"app":"voice-srs","schemaVersion":1,"prefs":{"letter_listen":"yes"}}"""
        val known = PrefsMap.knownForParse(PrefsMap.typedDefaults(), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        assertTrue(parsed.skipped.contains("letter_listen"))
    }
}
