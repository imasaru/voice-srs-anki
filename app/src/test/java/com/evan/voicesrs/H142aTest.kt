package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * h142a: a filler-only answer transcript ("huh", configured phrases) is noise, not an answer.
 * (a)-(b) FillerFilter pure logic; (c)-(d) Prefs round-trip and typedDefaults; (e) source scan.
 */
class H142aTest {

    // (a) isFiller: whole-transcript match only, enabled.
    @Test
    fun `isFiller matches configured phrases when enabled`() {
        val d = FillerFilter.DEFAULT_PHRASES
        assertTrue(FillerFilter.isFiller("huh", true, d))
        assertTrue(FillerFilter.isFiller("Huh?", true, d))
        assertTrue(FillerFilter.isFiller("  UM. ", true, d))
        assertTrue(FillerFilter.isFiller("Thank you.", true, d))
        assertTrue(FillerFilter.isFiller("Thanks for watching!", true, d))
        assertTrue(FillerFilter.isFiller("you", true, d))
    }

    // (a) isFiller is false for prose, empty, disabled, and a custom list.
    @Test
    fun `isFiller is false for prose, empty, disabled, and custom lists`() {
        val d = FillerFilter.DEFAULT_PHRASES
        assertFalse(FillerFilter.isFiller("uh the index", true, d))
        assertFalse(FillerFilter.isFiller("a hash join", true, d))
        assertFalse(FillerFilter.isFiller("Thank you is polite", true, d))
        assertFalse(FillerFilter.isFiller("B", true, d))
        assertFalse(FillerFilter.isFiller("", true, d))
        assertFalse(FillerFilter.isFiller("huh", false, d))
        assertTrue(FillerFilter.isFiller("So.", true, listOf("so")))
        assertFalse(FillerFilter.isFiller("huh", true, listOf("so")))
    }

    // (b) decode falls back to defaults and round-trips a custom list.
    @Test
    fun `decode falls back to defaults and round-trips a custom list`() {
        assertEquals(FillerFilter.DEFAULT_PHRASES, FillerFilter.decode(null))
        assertEquals(FillerFilter.DEFAULT_PHRASES, FillerFilter.decode(""))
        assertEquals(listOf("so", "er"), FillerFilter.decode(FillerFilter.encode(listOf("so", "er"))))
    }

    // (c) Prefs: fresh defaults, explicit round-trip.
    @Test
    fun `prefs defaults and round trips the filler settings`() {
        val fresh = Prefs.read(FakeSharedPreferences())
        assertTrue(fresh.ignoreFillers)
        assertEquals(FillerFilter.DEFAULT_PHRASES, fresh.fillerPhrases)

        val cfg: AnkiConfig = AnkiConfig(url = "", apiKey = "", deck = "")
        val p = FakeSharedPreferences()
        Prefs.write(p, cfg.copy(ignoreFillers = false, fillerPhrases = listOf("so", "er")))
        val r = Prefs.read(p)
        assertFalse(r.ignoreFillers)
        assertEquals(listOf("so", "er"), r.fillerPhrases)
    }

    // (c) backup/export text round-trips both filler keys into a fresh prefs.
    @Test
    fun `backup text round trips the filler keys`() {
        val snap = PrefsMap.snapshot(
            linkedMapOf("ignore_fillers" to false, "filler_phrases" to FillerFilter.encode(listOf("so", "er"))),
            emptyMap<String, Any?>()
        )
        val json = SettingsJson.export(snap)
        val known = PrefsMap.knownForParse(PrefsMap.typedDefaults(), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        val newPlain = LinkedHashMap<String, Any?>()
        PrefsMap.applyDelta(newPlain, linkedMapOf(), parsed.prefs)
        assertEquals(false, newPlain["ignore_fillers"])
        assertEquals(FillerFilter.encode(listOf("so", "er")), newPlain["filler_phrases"])
    }

    // (d) typedDefaults keeps the filler keys alongside the other listen keys.
    @Test
    fun `typedDefaults keeps the filler keys next to the other listen keys`() {
        val d = PrefsMap.typedDefaults()
        assertTrue(d.containsKey("ignore_fillers"))
        assertTrue(d.containsKey("filler_phrases"))
        assertTrue(d.containsKey("listen_wait_sec"))
        assertTrue(d.containsKey("end_silence_ms"))
        assertTrue(d.containsKey("answer_silence_sec"))
    }

    // (e) source scan: the wiring actually uses FillerFilter and the notice string.
    @Test
    fun `wiring references FillerFilter and the notice string`() {
        val drill = readFile("app/src/main/java/com/evan/voicesrs/DrillService.kt")
        assert(drill.contains("FillerFilter.isFiller(")) { "DrillService must call FillerFilter.isFiller" }
        assert(drill.contains("R.string.heard_noise_listening")) { "DrillService must show the notice" }
        val judge = readFile("app/src/main/java/com/evan/voicesrs/OwnWordsJudge.kt")
        assert(judge.contains("FillerFilter.HESITATIONS")) { "OwnWordsJudge must share the hesitation list" }
        assertTrue(OwnWordsJudge.isNonAnswer("again"))
        assertTrue(OwnWordsJudge.isNonAnswer("huh"))
    }

    private fun readFile(rel: String): String {
        var dir = File(".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()) {
                val f = File(dir, rel)
                require(f.exists()) { "missing $rel" }
                return f.readText()
            }
            val parent = dir.parentFile
            requireNotNull(parent) { "project root not found from $dir" }
            dir = parent
        }
        throw IllegalStateException("project root not found from ${File(".").absoluteFile}")
    }
}
