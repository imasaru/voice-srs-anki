package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsJudgeStrictnessLayoutTest {
    private val layout = File("src/main/res/layout/activity_settings.xml").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `strictness sits after the letter switch and before the decision URL`() {
        val letter = layout.indexOf("@+id/letterListenHelp")
        val group = layout.indexOf("@+id/judgeStrictnessGroup")
        val url = layout.indexOf("@+id/decisionUrlLayout")
        assertTrue(letter in 0 until group)
        assertTrue(group < url)
        for (id in listOf("judgeStrictnessLabel", "strictnessLenient", "strictnessNormal", "strictnessStrict", "judgeStrictnessHelp")) {
            assertEquals(id, 1, Regex("@\\+id/$id\"").findAll(layout).count())
        }
    }

    @Test
    fun `strings match the mockup and the 0_70 line is gone`() {
        val expected = mapOf(
            "settings_judge_strictness" to "Judge strictness",
            "settings_strictness_lenient" to "Lenient",
            "settings_strictness_normal" to "Normal",
            "settings_strictness_strict" to "Strict",
            "settings_strictness_help_lenient" to "Borderline answers get the higher rating.",
            "settings_strictness_help_normal" to "Rates on meaning. Borderline answers keep the rating they earn.",
            "settings_strictness_help_strict" to "Borderline answers get the lower rating. Key terms must be said.",
            "settings_decision_help" to "Judges free-speech answers on multiple-choice cards and rates them Again, Hard, Good or Easy.",
            "own_words_partly" to "Partly right.",
            "own_words_reason" to "Judge: %1\$s"
        )
        for ((name, text) in expected) {
            assertTrue(name, strings.contains("<string name=\"$name\">$text</string>"))
        }
        assertFalse(strings.contains("or higher is Good"))
    }
}
