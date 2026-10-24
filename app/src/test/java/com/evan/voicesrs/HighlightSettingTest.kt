package com.evan.voicesrs

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real save-and-reload test for the "Highlight sentence while reading" setting.
 *
 * No Robolectric and no new dependencies: Prefs.save/load only take a Context, so
 * Prefs exposes internal write/read over a [SharedPreferences]. The test drives those
 * with an in-memory FakeSharedPreferences (no Android framework calls, so it works
 * even with unitTests.returnDefaultValues off).
 */
class HighlightSettingTest {

    private fun baseCfg(): AnkiConfig = AnkiConfig(
        url = Prefs.DEFAULT_URL,
        apiKey = "",
        deck = Prefs.DEFAULT_DECK,
        highlightSentence = true,
        showCard = false,
        shuffleOptions = false,
        offlineBatchSize = 7
    )

    @Test
    fun `default is true when nothing is stored`() {
        val cfg = Prefs.read(FakeSharedPreferences())
        assertEquals(true, cfg.highlightSentence)
        assertEquals(true, HighlightSentence.SETTING_DEFAULT)
    }

    @Test
    fun `stored false reads back false and the key is false`() {
        val prefs = FakeSharedPreferences()
        Prefs.write(prefs, baseCfg().copy(highlightSentence = false))
        assertEquals(false, Prefs.read(prefs).highlightSentence)
        assertEquals(false, prefs.getBoolean("highlight_sentence", true))
    }

    @Test
    fun `stored true reads back true`() {
        val prefs = FakeSharedPreferences()
        Prefs.write(prefs, baseCfg().copy(highlightSentence = true))
        assertEquals(true, Prefs.read(prefs).highlightSentence)
    }

    @Test
    fun `other fields survive the round trip`() {
        val prefs = FakeSharedPreferences()
        Prefs.write(prefs, baseCfg())
        val cfg = Prefs.read(prefs)
        assertEquals(false, cfg.showCard)
        assertEquals(false, cfg.shuffleOptions)
        assertEquals(7, cfg.offlineBatchSize)
        assertEquals(false, cfg.decisionEnabled)
        assertEquals("", cfg.decisionUrl)
        assertEquals("", cfg.decisionModel)
    }

    @Test
    fun `decision model fields round trip`() {
        val prefs = FakeSharedPreferences()
        Prefs.write(
            prefs,
            baseCfg().copy(
                decisionEnabled = true,
                decisionUrl = "http://omlx.example.invalid",
                decisionModel = "clef"
            )
        )
        val cfg = Prefs.read(prefs)
        assertEquals(true, cfg.decisionEnabled)
        assertEquals("http://omlx.example.invalid", cfg.decisionUrl)
        assertEquals("clef", cfg.decisionModel)
    }
}

/**
 * In-memory [SharedPreferences] backed by a HashMap. Overrides every stubbed method so
 * it never calls the Android framework (no "Method ... not mocked" with returnDefaultValues off).
 */
class FakeSharedPreferences : SharedPreferences {
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
