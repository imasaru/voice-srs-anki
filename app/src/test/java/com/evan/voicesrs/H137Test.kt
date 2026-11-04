package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H137Test {

    @Test
    fun forgetRemovesTheMatchingUrl() {
        val result = SavedUrls.forget(listOf("http://a:1", "http://b:2"), "http://a:1")
        assertEquals(listOf("http://b:2"), result)
    }

    @Test
    fun forgetNormalizesTrailingSlashAndSpaces() {
        val result = SavedUrls.forget(listOf("http://a:1", "http://b:2"), "  http://a:1/  ")
        assertEquals(listOf("http://b:2"), result)
    }

    @Test
    fun forgetOfUnknownUrlIsUnchanged() {
        val saved = listOf("http://a:1", "http://b:2")
        assertEquals(saved, SavedUrls.forget(saved, "http://c:3"))
    }

    @Test
    fun settingsActivityWiresForget() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt")
            .takeIf { it.isFile } ?: File("app/src/main/java/com/evan/voicesrs/SettingsActivity.kt")
        val text = src.readText()
        assertTrue(text.contains("forget: (List<String>) -> Unit, save: (String) -> Unit): () -> Unit"))
        assertTrue(text.contains("setOnLongClickListener"))
        assertTrue(text.contains("R.string.settings_server_forget_q"))
        for (key in listOf(
            "ankiServers = l)",
            "neuralServers = l)",
            "asrServers = l)",
            "aiBaseServers = l)",
            "decisionServers = l)"
        )) {
            assertTrue("missing $key", text.contains(key))
        }
    }

    @Test
    fun stringsHaveForgetLabels() {
        val xml = File("src/main/res/values/strings.xml")
            .takeIf { it.isFile } ?: File("app/src/main/res/values/strings.xml")
        assertTrue(xml.readText().contains("settings_server_forget_q"))
    }
}
