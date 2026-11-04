package com.evan.voicesrs

import android.content.SharedPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * h141: the answer-listen silence cutoff is a configurable slider (seconds, 1–10, default 4).
 *
 * (b) reuses the InMemoryPrefs round-trip pattern from HighlightSettingTest instead of
 * Robolectric: Prefs.save/load touch EncryptedSharedPreferences, but Prefs.write/read over a
 * plain SharedPreferences verify the exact same persist+normalize path with no Android framework.
 */
class H141Test {

    // (a) silenceFor now takes the configured answer silence (seconds) and REPLACES the floor.
    @Test
    fun `answer silence honours the configured seconds and clamps the range`() {
        assertEquals(2000L, ListenWindow.silenceFor(1500L, true, 2))
        assertEquals(10000L, ListenWindow.silenceFor(1500L, true, 10))
        assertEquals(1000L, ListenWindow.silenceFor(1500L, true, 0))
        assertEquals(10000L, ListenWindow.silenceFor(1500L, true, 99))
        // non-answer listens keep the configured silence unchanged
        assertEquals(1500L, ListenWindow.silenceFor(1500L, false, 7))
        // default arg keeps today's 4 s behaviour
        assertEquals(4000L, ListenWindow.silenceFor(1500L, true))
    }

    @Test
    fun `answer silence constants bound the slider`() {
        assertEquals(1, ListenWindow.MIN_ANSWER_SILENCE_SEC)
        assertEquals(10, ListenWindow.MAX_ANSWER_SILENCE_SEC)
        assertEquals(4, ListenWindow.DEFAULT_ANSWER_SILENCE_SEC)
        assertEquals(4000L, ListenWindow.ANSWER_SILENCE_MS)
    }

    // (b) Prefs save/load round trip: 7 -> 7, 25 clamps to 10, fresh -> 4.
    @Test
    fun `prefs round trips the answer silence seconds`() {
        val cfg: AnkiConfig = AnkiConfig(url = "", apiKey = "", deck = "")
        val p = InMemoryPrefs()
        Prefs.write(p, cfg.copy(answerSilenceSec = 7))
        assertEquals(7, Prefs.read(p).answerSilenceSec)
        Prefs.write(p, cfg.copy(answerSilenceSec = 25))
        assertEquals(10, Prefs.read(p).answerSilenceSec)
        assertEquals(4, Prefs.read(InMemoryPrefs()).answerSilenceSec)
    }

    // (c) backup/export text round-trips answer_silence_sec into a fresh prefs.
    @Test
    fun `backup text round trips answer_silence_sec`() {
        val snap = PrefsMap.snapshot(
            linkedMapOf("answer_silence_sec" to 6),
            emptyMap<String, Any?>()
        )
        val json = SettingsJson.export(snap)
        val known = PrefsMap.knownForParse(PrefsMap.typedDefaults(), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        val newPlain = LinkedHashMap<String, Any?>()
        PrefsMap.applyDelta(newPlain, linkedMapOf(), parsed.prefs)
        assertEquals(6, newPlain["answer_silence_sec"])
    }

    // (e) typedDefaults keeps answer_silence_sec alongside the other listen keys with unchanged types.
    @Test
    fun `typedDefaults keeps answer silence next to the other listen keys`() {
        val d = PrefsMap.typedDefaults()
        assertEquals(0, d["answer_silence_sec"])
        assertEquals(0, d["listen_wait_sec"])
        assertEquals(0, d["end_silence_ms"])
    }

    // (e) importing a backup without answer_silence_sec leaves the current value untouched.
    @Test
    fun `import without answer_silence_sec leaves it untouched`() {
        val existing = linkedMapOf("answer_silence_sec" to 9)
        val json = SettingsJson.export(mapOf("deck_name" to "Other"))
        val known = PrefsMap.knownForParse(PrefsMap.snapshot(existing, emptyMap<String, Any?>()), json)
        val parsed = SettingsJson.parse(json, known) as SettingsJson.ImportResult.Ok
        val newPlain = LinkedHashMap<String, Any?>(existing)
        PrefsMap.applyDelta(newPlain, linkedMapOf(), parsed.prefs)
        assertEquals(9, newPlain["answer_silence_sec"])
    }

    // (d) source scan: the wiring actually uses the new field and slider.
    @Test
    fun `wiring references the new field and slider`() {
        val drill = readFile("app/src/main/java/com/evan/voicesrs/DrillService.kt")
        assert(drill.contains("answerSilenceSec")) { "DrillService must pass answerSilenceSec" }
        val layout = readFile("app/src/main/res/layout/activity_settings.xml")
        assert(layout.contains("answerSilenceSlider")) { "layout must define answerSilenceSlider" }
    }

    private fun readFile(rel: String): String {
        // Robolectric test workers run with the module dir as CWD; walk up to the Gradle
        // project root (marked by settings.gradle[kts]) so project-relative paths resolve.
        var dir = File(".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle").exists() ||
                File(dir, "settings.gradle.kts").exists()
            ) {
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

/**
 * In-memory [SharedPreferences] backed by a HashMap. Overrides every stubbed method so it never
 * calls the Android framework (mirrors HighlightSettingTest).
 */
private class InMemoryPrefs : SharedPreferences {
    private val store = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(store)

    override fun getString(key: String?, defValue: String?): String? =
        store[key] as? String ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (store[key] as? Boolean) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (store[key] as? Float) ?: defValue

    override fun getInt(key: String?, defValue: Int): Int =
        (store[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (store[key] as? Long) ?: defValue

    override fun getStringSet(key: String?, defValue: MutableSet<String>?): MutableSet<String>? =
        (store[key] as? MutableSet<String>) ?: defValue

    override fun contains(key: String?): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(store)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) { /* no-op */ }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) { /* no-op */ }

    private class FakeEditor(
        private val store: HashMap<String, Any?>
    ) : SharedPreferences.Editor {

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) store.remove(key) else store[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            store[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            store[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            store[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            store[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            store[key] = values
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            store.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            store.clear()
            return this
        }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
