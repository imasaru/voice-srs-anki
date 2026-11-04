package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class H132cTest {
    @Test
    fun decisionServersInTypedDefaults() {
        assertEquals("", PrefsMap.typedDefaults()["decision_servers"])
    }

    @Test
    fun decisionUrlFieldBecomesExposedDropdown() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        val idx = xml.indexOf("@+id/decisionUrlInput")
        assertTrue("decisionUrlInput present", idx > 0)
        val before = xml.substring((idx - 400).coerceAtLeast(0), idx)
        assertTrue("MaterialAutoCompleteTextView in decisionUrlLayout block", before.contains("MaterialAutoCompleteTextView"))
        assertTrue("ExposedDropdownMenu style in decisionUrlLayout block", before.contains("ExposedDropdownMenu"))
    }

    @Test
    fun decisionUrlUsesBindSavedUrlField() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue("bindSavedUrlField wired for decision url", src.contains("bindSavedUrlField(decisionUrlInput, decisionUrlLayout"))
        assertFalse("commitDecisionUrl removed", src.contains("fun commitDecisionUrl"))
    }

    @Test
    fun decisionServersRoundTrip() {
        val list = listOf("http://a/", "https://b")
        assertEquals(list, SavedUrls.decode(SavedUrls.encode(list)))
    }
}
