package com.evan.voicesrs

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class H120bTest {
    @Test fun fromLabelAndValues() {
        val labels = listOf("Auto-detect", "English", "Japanese")
        assertEquals(AsrLanguage.AUTO, AsrLanguage.fromLabel("Auto-detect", labels))
        assertEquals(AsrLanguage.EN, AsrLanguage.fromLabel("English", labels))
        assertEquals(AsrLanguage.JA, AsrLanguage.fromLabel("Japanese", labels))
        assertEquals(AsrLanguage.EN, AsrLanguage.fromLabel("Klingon", labels))
        assertEquals(listOf(AsrLanguage.AUTO, AsrLanguage.EN, AsrLanguage.JA), AsrLanguage.values().toList())
    }

    @Test fun layoutOrder() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        val h = xml.indexOf("@+id/asrUrlHelp"); val l = xml.indexOf("@+id/asrLanguageInput"); val w = xml.indexOf("@+id/listenWaitInput")
        assertTrue(h > 0 && l > h && w > l)
        assertTrue(xml.substring(l, xml.indexOf("/>", l)).contains("inputType=\"none\""))
        assertTrue(xml.contains("@+id/asrLanguageHelp"))
    }

    @Test fun settingsActivityWires() {
        val src = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue(src.contains("findViewById(R.id.asrLanguageInput)"))
        assertTrue(src.contains("asrLanguageLabels[held.asrLanguage.ordinal]"))
        assertTrue(src.contains("AsrLanguage.fromLabel(label, asrLanguageLabels)"))
    }

    @Test fun stringsHaveKeys() {
        val s = File("src/main/res/values/strings.xml").readText()
        listOf("settings_asr_language", "settings_asr_language_auto", "settings_asr_language_en", "settings_asr_language_ja", "settings_asr_language_help").forEach { assertTrue(s.contains("name=\"$it\"")) }
    }
}
