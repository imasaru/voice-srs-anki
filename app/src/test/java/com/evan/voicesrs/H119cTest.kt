package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H119cTest {

    @Test
    fun keepPreviousDetectsBlankRightAfterAdd() {
        assertTrue(AsrServers.keepPrevious("", true))
        assertTrue(AsrServers.keepPrevious("   ", true))
        assertFalse(AsrServers.keepPrevious("", false))
        assertFalse(AsrServers.keepPrevious("https://x", true))
    }

    @Test
    fun settingsActivityTracksAddingNew() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue(src.contains("var addingNew = false"))
        assertTrue(src.contains("SavedUrls.keepPrevious(raw, addingNew)"))
        assertTrue(src.contains("view.setText(current(), false)"))
        val addTrue = src.indexOf("addingNew = true")
        val clear = src.indexOf("view.setText(\"\", false)")
        assertTrue(addTrue > 0)
        assertTrue(addTrue < clear)
    }
}
