package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h146: Test voice short-circuits a blank neural URL; AI testing line clears the result icon. */
class H146Test {
    private fun exists(rel: String): Boolean =
        listOf("src/main/$rel", "app/src/main/$rel").any { File(it).exists() }
    private fun read(rel: String): String =
        listOf("src/main/$rel", "app/src/main/$rel").first { File(it).exists() }.let { File(it).readText() }

    @Test fun `a blank neural url short-circuits before testing`() {
        val s = read("java/com/evan/voicesrs/SettingsActivity.kt")
        val needle = "if (engine == TtsEngines.NEURAL && neuralUrl.isBlank()) { testVoiceStatus.text = getString(R.string.settings_url_missing); return@setOnClickListener }"
        assertTrue("missing blank-URL guard", s.contains(needle))
    }

    @Test fun `b the guard runs before testing is set true`() {
        val s = read("java/com/evan/voicesrs/SettingsActivity.kt")
        val trimLine = "val neuralUrl = neuralUrlView.text.toString().trim()"
        val trimIdx = s.indexOf(trimLine)
        assertTrue("missing the neuralUrl read", trimIdx >= 0)
        val guard = "if (engine == TtsEngines.NEURAL && neuralUrl.isBlank()) { testVoiceStatus.text = getString(R.string.settings_url_missing); return@setOnClickListener }"
        val guardIdx = s.indexOf(guard)
        assertTrue("guard not after the read", guardIdx > trimIdx)
        val firstTesting = s.indexOf("testing = true", guardIdx)
        assertTrue("no testing = true after the guard", firstTesting >= 0)
        assertTrue("guard must run before testing = true", guardIdx < firstTesting)
    }

    @Test fun `c ai testing clears the result icon`() {
        val s = read("java/com/evan/voicesrs/SettingsActivity.kt")
        assertTrue("missing showResult(aiTestResult, settings_ai_testing, null)",
            s.contains("showResult(aiTestResult, getString(R.string.settings_ai_testing), null)"))
        assertFalse("still sets .text on aiTestResult",
            s.contains("aiTestResult.text = getString(R.string.settings_ai_testing)"))
    }
}
