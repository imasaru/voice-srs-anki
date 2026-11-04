package com.evan.voicesrs

import java.io.File
import org.junit.Test

/** h141b: title stays, value moves to a separate answerSilenceValue TextView; one-line answerSilenceHelp. Plain scan (like H141Test d); no Robolectric. */
class H141bTest {

    @Test
    fun `layout has value and help`() {
        val layout = readFile("app/src/main/res/layout/activity_settings.xml")
        assert(layout.contains("answerSilenceValue")) { "layout must define answerSilenceValue" }
        assert(layout.contains("answerSilenceHelp")) { "layout must define answerSilenceHelp" }
    }

    @Test
    fun `activity sets value not label`() {
        val src = readFile("app/src/main/java/com/evan/voicesrs/SettingsActivity.kt")
        assert(src.contains("answerSilenceValue.text = getString(R.string.settings_answer_silence_value")) { "SettingsActivity must set answerSilenceValue.text" }
        assert(!src.contains("answerSilenceLabel.text =")) { "SettingsActivity must not set answerSilenceLabel.text" }
    }

    @Test
    fun `strings define help`() {
        val strings = readFile("app/src/main/res/values/strings.xml")
        assert(strings.contains("name=\"settings_answer_silence_help\"")) { "strings.xml must define settings_answer_silence_help" }
    }

    private fun readFile(rel: String): String {
        var dir = File(".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()) {
                val f = File(dir, rel)
                require(f.exists()) { "missing $rel" }
                return f.readText()
            }
            val parent = dir.parentFile
            requireNotNull(parent) { "project root not found from $dir" }
            dir = parent
        }
        throw IllegalStateException("project root not found from ${File(".").absoluteFile}")
    }
}
