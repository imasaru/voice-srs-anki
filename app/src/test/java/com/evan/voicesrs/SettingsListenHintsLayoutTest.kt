package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SettingsListenHintsLayoutTest {

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
            if (el.getAttribute("name") == name) return el.textContent
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

    @Test
    fun `a listen wait label is Rating listen window`() {
        assertEquals("Rating listen window", str("settings_listen_wait"))
    }

    @Test
    fun `b three hint strings have the approved text`() {
        assertEquals(
            "Total time to think before the answer is shown. Say a letter or your answer to reveal early.",
            str("settings_think_time_help")
        )
        assertEquals(
            "How long the mic stays open for each listen (ratings, and answers when Think time is Off).",
            str("settings_listen_wait_help")
        )
        assertEquals("Missed listens allowed before switching to silent.", str("settings_listen_attempts_help"))
    }

    @Test
    fun `c hint TextViews exist and point at the hint strings`() {
        assertEquals("@string/settings_listen_wait_help", byId("listenWaitHelp")?.getAttribute("android:text"))
        assertEquals("@string/settings_listen_attempts_help", byId("listenAttemptsHelp")?.getAttribute("android:text"))
        assertEquals("@string/settings_think_time_help", byId("thinkTimeHelp")?.getAttribute("android:text"))
    }

    @Test
    fun `d input view ids are unchanged`() {
        for (id in listOf("listenWaitInput", "endSilenceInput", "listenAttemptsInput", "thinkTimeInput")) {
            assertTrue("missing $id", byId(id) != null)
        }
    }
}
