package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class H132a2Test {
    @Test
    fun bindSavedUrlFieldExtractedForSavedUrlFields() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue("bindSavedUrlField declared exactly once", src.split("private fun bindSavedUrlField(").size - 1 == 1)
        assertTrue(src.contains("bindSavedUrlField(asrUrlView, asrUrlLayout"))
        assertFalse("asrAddingNew removed", src.contains("asrAddingNew"))
        assertFalse("commitAsrUrl removed", src.contains("fun commitAsrUrl"))
    }
}
