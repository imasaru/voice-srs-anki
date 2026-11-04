package com.evan.voicesrs
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class H135Test {
    @Test
    fun noBakedInServerUrlDefaults() {
        val gradle = File("build.gradle.kts").readText()
        for (bad in listOf(
            "DEFAULT_ANKI_URL",
            "DEFAULT_NEURAL_URL",
            "DEFAULT_AI_URL",
            "DEFAULT_DECISION_URL",
            "voicesrs.ankiUrl",
            "voicesrs.neuralUrl",
            "voicesrs.aiUrl",
            "voicesrs.decisionUrl"
        )) {
            assertFalse("build.gradle.kts must not reference $bad", gradle.contains(bad))
        }

        val prefs = File("src/main/java/com/evan/voicesrs/Prefs.kt").readText()
        for (bad in listOf("DEFAULT_URL", "DEFAULT_NEURAL_URL", "DEFAULT_AI_URL", "DEFAULT_DECISION_URL")) {
            assertFalse("Prefs.kt must not reference $bad", prefs.contains(bad))
        }

        val settings = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        for (bad in listOf("DEFAULT_URL", "DEFAULT_NEURAL_URL", "DEFAULT_AI_URL", "DEFAULT_DECISION_URL")) {
            assertFalse("SettingsActivity.kt must not reference $bad", settings.contains(bad))
        }
        assertTrue(
            "SettingsActivity.kt should short-circuit a blank connection URL",
            settings.contains("if (url.isBlank()) { showResult(testResultView, getString(R.string.settings_url_missing), false)")
        )
    }
}
