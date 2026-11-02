package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {

    private val samplePrefs = linkedMapOf(
        "anki_url" to "http://anki.local:8091",
        "deck_name" to "VoiceSRS-Test",
        "tts_rate" to 2.5f,
        "speed_mode" to true,
        "listen_wait_sec" to 7,
        "auto_explain_eases" to setOf("b", "a"),
        "neural_token" to "tok-123"
    )

    private fun nonSecret(prefs: Map<String, Any?>): Map<String, Any?> =
        prefs.filter { it.key !in SettingsJson.SECRET_KEYS }

    @Test
    fun `export then parse returns the same non-secret values with correct types`() {
        val json = SettingsJson.export(samplePrefs)
        val result = SettingsJson.parse(json, samplePrefs)
        assertTrue(result is SettingsJson.ImportResult.Ok)
        val ok = result as SettingsJson.ImportResult.Ok
        assertEquals(nonSecret(samplePrefs), ok.prefs)
        // Types survive the round trip.
        assertEquals("http://anki.local:8091", ok.prefs["anki_url"])
        assertTrue(ok.prefs["speed_mode"] as Boolean)
        assertEquals(7, ok.prefs["listen_wait_sec"] as Int)
        assertEquals(2.5f, ok.prefs["tts_rate"] as Float)
        assertEquals(setOf("a", "b"), ok.prefs["auto_explain_eases"] as Set<*>)
        assertFalse(ok.prefs.containsKey("neural_token"))
    }

    @Test
    fun `secrets are excluded by default`() {
        val json = SettingsJson.export(samplePrefs)
        assertFalse(json.contains("neural_token"))
        val ok = SettingsJson.parse(json, samplePrefs) as SettingsJson.ImportResult.Ok
        assertFalse(ok.hadSecrets)
        assertFalse(ok.prefs.containsKey("neural_token"))
    }

    @Test
    fun `secrets are included only with opt-in and report hadSecrets`() {
        val json = SettingsJson.export(samplePrefs, includeSecrets = true)
        assertTrue(json.contains("neural_token"))
        val ok = SettingsJson.parse(json, samplePrefs) as SettingsJson.ImportResult.Ok
        assertTrue(ok.hadSecrets)
        assertEquals("tok-123", ok.prefs["neural_token"])
    }

    @Test
    fun `blank secret is omitted even with opt-in`() {
        val prefs = linkedMapOf("neural_token" to "", "anki_url" to "http://anki.local:8091")
        val json = SettingsJson.export(prefs, includeSecrets = true)
        assertFalse(json.contains("neural_token"))
    }

    @Test
    fun `unknown keys are ignored and listed in skipped`() {
        val prefs = linkedMapOf(
            "anki_url" to "http://anki.local:8091",
            "mystery_key" to "value"
        )
        val known = linkedMapOf("anki_url" to "")
        val json = SettingsJson.export(prefs)
        val ok = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        assertTrue(ok.skipped.contains("mystery_key"))
        assertFalse(ok.prefs.containsKey("mystery_key"))
    }

    @Test
    fun `wrong type is skipped and listed in skipped`() {
        val prefs = linkedMapOf("speed_mode" to false)
        val bad = """{"schemaVersion":1,"app":"voice-srs","prefs":{"speed_mode":"yes"}}"""
        val ok = SettingsJson.parse(bad, prefs) as SettingsJson.ImportResult.Ok
        assertTrue(ok.skipped.contains("speed_mode"))
        assertFalse(ok.prefs.containsKey("speed_mode"))
    }

    @Test
    fun `bad input yields Error`() {
        val known = linkedMapOf("anki_url" to "")
        assertSame(SettingsJson.ImportResult.Error::class.java,
            (SettingsJson.parse("not json", known) as SettingsJson.ImportResult.Error).javaClass)
        assertTrue((SettingsJson.parse("not json", known) as SettingsJson.ImportResult.Error).reason.isNotBlank())

        val noPrefs = SettingsJson.parse("""{"schemaVersion":1,"app":"voice-srs"}""", known)
        assertTrue(noPrefs is SettingsJson.ImportResult.Error)

        val wrongApp = SettingsJson.parse("""{"schemaVersion":1,"app":"other","prefs":{}}""", known)
        assertTrue(wrongApp is SettingsJson.ImportResult.Error)

        val tooNew = SettingsJson.parse("""{"schemaVersion":2,"app":"voice-srs","prefs":{}}""", known)
        assertTrue(tooNew is SettingsJson.ImportResult.Error)
    }

    @Test
    fun `older file without schemaVersion is accepted`() {
        val older = """{"app":"voice-srs","prefs":{"anki_url":"http://old:8091"}}"""
        val known = linkedMapOf("anki_url" to "")
        val ok = SettingsJson.parse(older, known) as SettingsJson.ImportResult.Ok
        assertEquals("http://old:8091", ok.prefs["anki_url"])
    }

    @Test
    fun `speech_engine round trip`() {
        val prefs = linkedMapOf("speech_engine" to "WHISPER")
        val json = SettingsJson.export(prefs)
        val known = linkedMapOf("speech_engine" to "")
        val ok = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        assertEquals("WHISPER", ok.prefs["speech_engine"])
    }

    @Test
    fun `export JSON keys are sorted and stable`() {
        val prefs = linkedMapOf("zeta" to "1", "alpha" to "2", "mid" to true)
        val j1 = SettingsJson.export(prefs)
        val j2 = SettingsJson.export(prefs)
        assertEquals(j2, j1)
        assertTrue(j1.indexOf("\"app\"") < j1.indexOf("\"schemaVersion\""))
        assertTrue(j1.indexOf("\"alpha\"") < j1.indexOf("\"mid\""))
        assertTrue(j1.indexOf("\"mid\"") < j1.indexOf("\"zeta\""))
    }
}
