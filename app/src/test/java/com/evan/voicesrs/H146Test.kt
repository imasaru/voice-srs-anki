package com.evan.voicesrs

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** h146: Test voice short-circuits a blank neural URL; AI testing line clears the result icon. */
class H146Test {
    private fun read(rel: String): String =
        listOf("src/main/$rel", "app/src/main/$rel").first { File(it).exists() }.let { File(it).readText() }
    private fun settings(): String = read("java/com/evan/voicesrs/SettingsActivity.kt")
    private val guard = "if (engine == TtsEngines.NEURAL && neuralUrl.isBlank()) { testVoiceStatus.text = getString(R.string.settings_url_missing); return@setOnClickListener }"

    @Test fun `a blank neural url short-circuits`() {
        val s = settings()
        assertTrue("missing blank-URL guard", s.contains(guard))
    }

    @Test fun `b guard runs before testing is set true`() {
        val s = settings()
        val trimIdx = s.indexOf("val neuralUrl = neuralUrlView.text.toString().trim()")
        val guardIdx = s.indexOf(guard)
        assertTrue("guard after neuralUrl read", trimIdx >= 0 && guardIdx > trimIdx)
        assertTrue("guard runs before testing = true", guardIdx < s.indexOf("testing = true", guardIdx))
    }

    @Test fun `c ai testing clears the result icon`() {
        val s = settings()
        assertTrue("showResult(aiTestResult, settings_ai_testing, null)",
            s.contains("showResult(aiTestResult, getString(R.string.settings_ai_testing), null)"))
        assertFalse("still sets .text on aiTestResult",
            s.contains("aiTestResult.text = getString(R.string.settings_ai_testing)"))
    }
}
