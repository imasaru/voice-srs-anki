package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H137Test {
    @Test fun forgetRemovesMatching() =
        assertEquals(listOf("http://b:2"), SavedUrls.forget(listOf("http://a:1", "http://b:2"), "http://a:1"))

    @Test fun forgetNormalizesSlashAndSpaces() =
        assertEquals(listOf("http://b:2"), SavedUrls.forget(listOf("http://a:1", "http://b:2"), "  http://a:1/  "))

    @Test fun forgetUnknownUnchanged() {
        val s = listOf("http://a:1", "http://b:2")
        assertEquals(s, SavedUrls.forget(s, "http://c:3"))
    }

    @Test fun settingsWiresForget() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt")
            .takeIf { it.isFile } ?: File("app/src/main/java/com/evan/voicesrs/SettingsActivity.kt")
        val t = src.readText()
        assertTrue(t.contains("forget: (List<String>) -> Unit, save: (String) -> Unit): () -> Unit"))
        assertTrue(t.contains("setOnLongClickListener"))
        assertTrue(t.contains("R.string.settings_server_forget_q"))
        for (k in listOf("ankiServers = l)", "neuralServers = l)", "asrServers = l)", "aiBaseServers = l)", "decisionServers = l)"))
            assertTrue("missing $k", t.contains(k))
    }

    @Test fun stringsHaveForgetLabels() {
        val x = File("src/main/res/values/strings.xml")
            .takeIf { it.isFile } ?: File("app/src/main/res/values/strings.xml")
        assertTrue(x.readText().contains("settings_server_forget_q"))
    }
}
