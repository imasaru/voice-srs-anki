package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class H132fTest {
    @Test
    fun ankiServersInTypedDefaults() {
        assertEquals("", PrefsMap.typedDefaults()["anki_servers"])
    }

    @Test
    fun ankiUrlFieldBecomesExposedDropdown() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        val idx = xml.indexOf("@+id/urlInput")
        assertTrue("urlInput present", idx > 0)
        val before = xml.substring((idx - 400).coerceAtLeast(0), idx)
        assertTrue("MaterialAutoCompleteTextView in urlLayout block", before.contains("MaterialAutoCompleteTextView"))
        assertTrue("ExposedDropdownMenu style in urlLayout block", before.contains("ExposedDropdownMenu"))
        assertTrue("urlLayout id in urlLayout block", before.contains("@+id/urlLayout"))
    }

    @Test
    fun ankiUrlUsesBindSavedUrlField() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue("bindSavedUrlField wired for anki url", src.contains("bindSavedUrlField(urlView, urlLayout"))
        assertFalse("commitUrl removed", src.contains("fun commitUrl"))
    }

    @Test
    fun ankiServersRoundTrip() {
        val list = listOf("http://a/", "https://b")
        assertEquals(list, SavedUrls.decode(SavedUrls.encode(list)))
    }
}
