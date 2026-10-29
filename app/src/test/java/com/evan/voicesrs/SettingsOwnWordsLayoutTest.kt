package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SettingsOwnWordsLayoutTest {

    private fun parse(path: String): Document {
        val f = listOf(File(path), File("app/$path")).firstOrNull { it.isFile }
        assertNotNull("Missing file: $path", f)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
    }

    private val layout by lazy { parse("src/main/res/layout/activity_settings.xml") }
    private val strings by lazy { parse("src/main/res/values/strings.xml") }

    private fun str(name: String): String? {
        val list = strings.getElementsByTagName("string")
        for (i in 0 until list.length) {
            val el = list.item(i) as Element
            if (el.getAttribute("name") == name) return el.textContent.replace("\\\"", "\"")
        }
        return null
    }

    private fun byId(id: String): Element? {
        val list = layout.getElementsByTagName("*")
        for (i in 0 until list.length) {
            val el = list.item(i) as Element
            if (el.getAttribute("android:id") == "@+id/$id") return el
        }
        return null
    }

    /** Document order index of an element (its position in the flattened tree). */
    private fun order(el: Element?): Int {
        if (el == null) return -1
        val list = layout.getElementsByTagName("*")
        for (i in 0 until list.length) {
            if (list.item(i) === el) return i
        }
        return -1
    }

    @Test fun letterListenSwitchExistsAndSitsBetweenDecisionAndUrl() {
        val sw = byId("letterListenSwitch")
        assertNotNull("missing letterListenSwitch", sw)
        assertEquals(
            "com.google.android.material.materialswitch.MaterialSwitch",
            sw!!.tagName
        )
        assertEquals("@string/settings_letter_listen", sw.getAttribute("android:text"))
        val decision = byId("decisionEnabledSwitch")
        val url = byId("decisionUrlLayout")
        assertNotNull("missing decisionEnabledSwitch", decision)
        assertNotNull("missing decisionUrlLayout", url)
        assertTrue("letterListenSwitch must come after decisionEnabledSwitch", order(sw) > order(decision))
        assertTrue("letterListenSwitch must come before decisionUrlLayout", order(sw) < order(url))
    }

    @Test fun letterListenHelpAndVoiceCommandsSection() {
        val help = byId("letterListenHelp")
        assertNotNull("missing letterListenHelp", help)
        assertEquals("@string/settings_letter_listen_help", help!!.getAttribute("android:text"))
        val list = byId("voiceCommandsList")
        assertNotNull("missing voiceCommandsList", list)
        val thinkHelp = byId("thinkTimeHelp")
        assertNotNull("missing thinkTimeHelp", thinkHelp)
        assertTrue("voiceCommandsList must come after thinkTimeHelp", order(list) > order(thinkHelp))
    }

    @Test fun letterListenAndVoiceCommandStringsHaveApprovedText() {
        assertEquals("Listen for letter answers (A–D)", str("settings_letter_listen"))
        assertEquals(
            "Off: only your own words are judged. Say “answer B” or tap a letter to pick one.",
            str("settings_letter_listen_help")
        )
        assertEquals("Voice commands", str("settings_voice_commands"))
        assertEquals(
            "Commands count only when said alone. Inside a longer answer they are part of your answer. With own words off, \"next\" and \"pass\" also skip.",
            str("settings_voice_commands_note")
        )
    }
}
