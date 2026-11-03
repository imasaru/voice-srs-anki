package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** h107c: card start log + card tag appended to drill log lines. */
class H107cTest {
    @Test
    fun `helpers render card tags`() {
        assertEquals("n=9 id=123", DrillLog.tag(9, 123L))
        assertEquals("card start n=9 id=123 note=45 deck=DE", DrillLog.card(9, 123L, 45L, "DE"))
    }

    @Test
    fun `drill log lines carry the card tag`() {
        val f = listOf(
            File("src/main/java/com/evan/voicesrs/DrillService.kt"),
            File("app/src/main/java/com/evan/voicesrs/DrillService.kt")
        ).firstOrNull { it.isFile }
        requireNotNull(f) { "DrillService.kt not found" }
        val src = f.readText()
        val lines = src.lines()
        val listenOk = lines.count { it.contains("Log.i(TAG, \"listen ok:") }
        assertTrue("expected >=10 listen ok lines, found $listenOk", listenOk >= 10)
        val markers = listOf(
            "Log.i(TAG, \"listen ok:",
            "Log.i(TAG, \"voice action:",
            "Log.i(TAG, \"grade chip highlighted",
            "Log.i(TAG, \"queued ease=",
            "Log.i(TAG, \"graded ease=",
            "Log.i(TAG, \"own-words judge rating=fallback"
        )
        var checked = 0
        for (line in lines) {
            if (markers.any { line.contains(it) }) {
                assertTrue("missing tag: $line", line.contains("currentListenTag"))
                checked++
            }
        }
        assertTrue("expected tagged lines, checked $checked", checked >= 10)
        assertTrue("missing DrillLog.card call",
            src.contains("DrillLog.card(cardIndex + 1, cardId, noteId ?: 0L, sessionDeck)"))
        assertTrue("sessionDeck not set from cfg.deck", src.contains("sessionDeck = cfg.deck"))
        val idx = src.indexOf("OwnWordsJudge.verdictLog(")
        require(idx >= 0) { "verdictLog( not found" }
        val lineNo = src.substring(0, idx).lines().size
        val window = lines.subList(lineNo - 1, minOf(lineNo - 1 + 4, lines.size))
        assertTrue("verdictLog window missing tag: ${window.joinToString()}",
            window.joinToString().contains("currentListenTag"))
    }
}
