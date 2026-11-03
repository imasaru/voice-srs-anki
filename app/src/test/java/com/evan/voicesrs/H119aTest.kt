package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H119aTest {

    @Test
    fun rememberIgnoresBlanksAndTrims() {
        assertEquals(emptyList<String>(), AsrServers.remember(emptyList(), "  "))
        assertEquals(listOf("https://a/"), AsrServers.remember(emptyList(), " https://a/ "))
    }

    @Test
    fun rememberDedupesIgnoringTrailingSlashAndPutsNewestFirst() {
        assertEquals(
            listOf("https://a/", "https://b"),
            AsrServers.remember(listOf("https://b", "https://a"), "https://a/")
        )
    }

    @Test
    fun rememberCapsAtMaxEightNewestFirst() {
        var list = emptyList<String>()
        for (i in 0..9) list = AsrServers.remember(list, "https://s$i")
        assertEquals(8, list.size)
        assertEquals("https://s9", list.first())
    }

    @Test
    fun encodeDecodeRoundTripAndEdgeCases() {
        assertEquals(
            listOf("https://a", "https://b"),
            AsrServers.decode(AsrServers.encode(listOf("https://a", "https://b")))
        )
        assertEquals(emptyList<String>(), AsrServers.decode(null))
        assertEquals(emptyList<String>(), AsrServers.decode(""))
        assertEquals(listOf("https://a"), AsrServers.decode("https://a\n\nhttps://a\n"))
    }

    @Test
    fun ankiConfigDefaultAsrServersIsEmpty() {
        assertEquals(emptyList<String>(), AnkiConfig(url = "", apiKey = "", deck = "").asrServers)
    }

    @Test
    fun commitAsrUrlUsesAutoSaveDecideAndRemembersSavedUrl() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue(src.contains("AutoSave.decide(held.asrUrl, raw)"))
        assertTrue(src.contains("AsrServers.remember(it.asrServers, raw)"))
    }
}
