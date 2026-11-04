package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class H132eTest {
    @Test
    fun aiBaseServersInTypedDefaults() {
        assertEquals("", PrefsMap.typedDefaults()["ai_base_servers"])
    }

    @Test
    fun aiUrlFieldBecomesExposedDropdown() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        val idx = xml.indexOf("@+id/aiBaseUrlInput")
        assertTrue("aiBaseUrlInput present", idx > 0)
        val before = xml.substring((idx - 400).coerceAtLeast(0), idx)
        assertTrue("MaterialAutoCompleteTextView in aiBaseUrlLayout block", before.contains("MaterialAutoCompleteTextView"))
        assertTrue("ExposedDropdownMenu style in aiBaseUrlLayout block", before.contains("ExposedDropdownMenu"))
        assertTrue("aiBaseUrlLayout id in aiBaseUrlLayout block", before.contains("@+id/aiBaseUrlLayout"))
    }

    @Test
    fun aiUrlUsesBindSavedUrlField() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue("bindSavedUrlField wired for ai url", src.contains("bindSavedUrlField(aiBaseUrlInput, aiBaseUrlLayout"))
        assertFalse("commitAiUrl removed", src.contains("fun commitAiUrl"))
    }

    @Test
    fun aiServersRoundTrip() {
        val list = listOf("http://a/", "https://b")
        assertEquals(list, SavedUrls.decode(SavedUrls.encode(list)))
    }
}
