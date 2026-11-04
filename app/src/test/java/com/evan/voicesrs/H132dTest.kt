package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class H132dTest {
    @Test
    fun neuralServersInTypedDefaults() {
        assertEquals("", PrefsMap.typedDefaults()["neural_servers"])
    }

    @Test
    fun neuralUrlFieldBecomesExposedDropdown() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        val idx = xml.indexOf("@+id/neuralUrlInput")
        assertTrue("neuralUrlInput present", idx > 0)
        val before = xml.substring((idx - 400).coerceAtLeast(0), idx)
        assertTrue("MaterialAutoCompleteTextView in neuralUrlLayout block", before.contains("MaterialAutoCompleteTextView"))
        assertTrue("ExposedDropdownMenu style in neuralUrlLayout block", before.contains("ExposedDropdownMenu"))
        assertTrue("neuralUrlLayout id in neuralUrlLayout block", before.contains("@+id/neuralUrlLayout"))
    }

    @Test
    fun neuralUrlUsesBindSavedUrlField() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue("bindSavedUrlField wired for neural url", src.contains("bindSavedUrlField(neuralUrlView, neuralUrlLayout"))
        assertFalse("commitNeuralUrl removed", src.contains("fun commitNeuralUrl"))
    }

    @Test
    fun neuralServersRoundTrip() {
        val list = listOf("http://a/", "https://b")
        assertEquals(list, SavedUrls.decode(SavedUrls.encode(list)))
    }
}
