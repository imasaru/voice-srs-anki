package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H153lTest {
    private val files = listOf(
        "AnkiConnect", "AudioRouteWatcher", "ChatDictation", "ExamSync", "ExplainBatch", "ExplainLog",
        "ExplainQueue", "GeneralChat", "GradeFix", "GradeSync", "LiveThread", "MainActivity", "Prefs",
        "SettingsActivity", "ThreadAsk", "VoiceSrsApp", "VoskModelFetcher", "VoskRecognizer"
    )
    private fun src(name: String) = File("src/main/java/com/evan/voicesrs/$name.kt").readText()

    @Test fun a_noRawInfoWarnErrorLeft() {
        val raw = Regex("""(^|[^A-Za-z.])Log\.[iwe]\(""", RegexOption.MULTILINE)
        val left = files.filter { raw.containsMatchIn(src(it)) }
        assertEquals(emptyList<String>(), left)
    }

    @Test fun b_routedThroughDLog() {
        val n = files.sumOf { Regex("""DLog\.[iwe]\(""").findAll(src(it)).count() }
        assertTrue("DLog count $n", n >= 45)
    }

    @Test fun c_aiClientAndDLogUntouched() {
        assertTrue(src("AiClient").contains("android.util.Log.i(\"AiClient\", line)"))
        assertTrue(!src("AiClient").contains("DLog."))
        assertTrue(!src("DLog").contains("DLog.i(") && !src("DLog").contains("DLog.w("))
    }
}
