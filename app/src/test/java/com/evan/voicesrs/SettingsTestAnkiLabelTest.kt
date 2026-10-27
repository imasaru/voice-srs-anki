package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/**
 * Verifies the AnkiConnect section's "Test connection" button label was
 * renamed to "Test Anki". The label text comes from the
 * settings_test string resource, and activity_settings.xml must still wire a
 * view to that same resource reference.
 */
class SettingsTestAnkiLabelTest {

    private fun parseXml(path: String): Document {
        val f = File(path)
        assertTrue("Missing file: $path", f.isFile)
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        return factory.newDocumentBuilder().parse(f)
    }

    private fun stringValue(doc: Document, name: String): String? {
        val list = doc.getElementsByTagName("string")
        for (i in 0 until list.length) {
            val el = list.item(i) as? Element ?: continue
            if (el.getAttribute("name") == name) return el.textContent
        }
        return null
    }

    @Test
    fun settings_test_label_is_TestAnki() {
        val doc = parseXml("src/main/res/values/strings.xml")
        assertEquals("Test Anki", stringValue(doc, "settings_test"))
    }

    @Test
    fun activity_settings_still_references_settings_test_string() {
        val doc = parseXml("src/main/res/layout/activity_settings.xml")
        val refs = doc.getElementsByTagName("*")
        var found = false
        for (i in 0 until refs.length) {
            val el = refs.item(i) as? Element ?: continue
            if (el.getAttribute("android:text") == "@string/settings_test") found = true
        }
        assertTrue("No view in activity_settings.xml references @string/settings_test", found)
    }
}
