package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/**
 * Verifies the refresh controls in activity_settings.xml are icon-only
 * MaterialButton buttons with the metrics that keep the 24dp ic_refresh
 * icon fully visible (48dp tap target, iconSize 24dp, 12dp padding,
 * zero insets). Also checks the content-description strings.
 */
class SettingsRefreshLayoutTest {

    private fun parseXml(path: String): Document {
        val f = File(path)
        assertTrue("Missing file: $path", f.isFile)
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        return factory.newDocumentBuilder().parse(f)
    }

    private fun findElement(doc: Document, idRef: String): Element? {
        val list = doc.getElementsByTagName("*")
        for (i in 0 until list.length) {
            val el = list.item(i) as? Element ?: continue
            if (el.getAttribute("android:id") == idRef) return el
        }
        return null
    }

    private fun stringValue(doc: Document, name: String): String? {
        val list = doc.getElementsByTagName("string")
        for (i in 0 until list.length) {
            val el = list.item(i) as? Element ?: continue
            if (el.getAttribute("name") == name) return el.textContent
        }
        return null
    }

    private fun attr(el: Element, name: String): String = el.getAttribute(name)

    @Test
    fun `both refresh controls are 48dp MaterialButton icon buttons`() {
        val doc = parseXml("src/main/res/layout/activity_settings.xml")
        val refreshDecks = requireNotNull(findElement(doc, "@+id/refreshDecksButton")) { "refreshDecksButton missing" }
        val aiRefresh = requireNotNull(findElement(doc, "@+id/aiModelRefresh")) { "aiModelRefresh missing" }

        val buttons = listOf(refreshDecks to "refreshDecksButton", aiRefresh to "aiModelRefresh")
        for ((el, label) in buttons) {
            assertEquals("wrong root tag for $label",
                "com.google.android.material.button.MaterialButton", el.tagName)
            assertEquals("wrong style for $label",
                "@style/Widget.Material3.Button.IconButton", attr(el, "style"))
            assertEquals("width for $label", "48dp", attr(el, "android:layout_width"))
            assertEquals("height for $label", "48dp", attr(el, "android:layout_height"))
            assertEquals("iconSize for $label", "24dp", attr(el, "app:iconSize"))
            assertEquals("padding for $label", "12dp", attr(el, "android:padding"))
            assertEquals("insetLeft for $label", "0dp", attr(el, "android:insetLeft"))
            assertEquals("insetRight for $label", "0dp", attr(el, "android:insetRight"))
            assertEquals("insetTop for $label", "0dp", attr(el, "android:insetTop"))
            assertEquals("insetBottom for $label", "0dp", attr(el, "android:insetBottom"))
            assertEquals("icon for $label", "@drawable/ic_refresh", attr(el, "app:icon"))
            assertTrue("contentDescription must be a string ref for $label",
                attr(el, "android:contentDescription").startsWith("@string/"))
        }
    }

    @Test
    fun `refreshDecksButton keeps a 4dp start margin`() {
        val doc = parseXml("src/main/res/layout/activity_settings.xml")
        val el = requireNotNull(findElement(doc, "@+id/refreshDecksButton")) { "refreshDecksButton missing" }
        assertEquals("4dp", attr(el, "android:layout_marginStart"))
    }

    @Test
    fun `content description strings map to expected labels`() {
        val doc = parseXml("src/main/res/values/strings.xml")
        assertEquals("Refresh decks", stringValue(doc, "settings_refresh_decks_desc"))
        assertEquals("Refresh models", stringValue(doc, "settings_ai_model_refresh"))
    }
}
