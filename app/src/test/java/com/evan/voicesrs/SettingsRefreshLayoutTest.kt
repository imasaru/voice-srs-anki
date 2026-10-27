package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/**
 * Verifies the refresh controls in activity_settings.xml.
 *
 * refreshDecksButton stays an icon MaterialButton with the metrics that keep
 * the 24dp ic_refresh icon fully visible. aiModelRefresh and decisionModelRefresh
 * are plain ImageButtons (48dp square) so the icon is no longer clipped.
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
    fun `refreshDecksButton stays a 48dp MaterialButton icon button`() {
        val doc = parseXml("src/main/res/layout/activity_settings.xml")
        val refreshDecks = requireNotNull(findElement(doc, "@+id/refreshDecksButton")) { "refreshDecksButton missing" }

        assertEquals("wrong root tag for refreshDecksButton",
            "com.google.android.material.button.MaterialButton", refreshDecks.tagName)
        assertEquals("wrong style for refreshDecksButton",
            "@style/Widget.Material3.Button.IconButton", attr(refreshDecks, "style"))
        assertEquals("width for refreshDecksButton", "wrap_content", attr(refreshDecks, "android:layout_width"))
        assertEquals("height for refreshDecksButton", "wrap_content", attr(refreshDecks, "android:layout_height"))
        assertEquals("minWidth for refreshDecksButton", "48dp", attr(refreshDecks, "android:minWidth"))
        assertEquals("minHeight for refreshDecksButton", "48dp", attr(refreshDecks, "android:minHeight"))
        assertEquals("weight for refreshDecksButton", "0", attr(refreshDecks, "android:layout_weight"))
        assertEquals("iconSize for refreshDecksButton", "24dp", attr(refreshDecks, "app:iconSize"))
        assertEquals("padding for refreshDecksButton", "12dp", attr(refreshDecks, "android:padding"))
        assertEquals("insetLeft for refreshDecksButton", "0dp", attr(refreshDecks, "android:insetLeft"))
        assertEquals("insetRight for refreshDecksButton", "0dp", attr(refreshDecks, "android:insetRight"))
        assertEquals("insetTop for refreshDecksButton", "0dp", attr(refreshDecks, "android:insetTop"))
        assertEquals("insetBottom for refreshDecksButton", "0dp", attr(refreshDecks, "android:insetBottom"))
        assertEquals("icon for refreshDecksButton", "@drawable/ic_refresh", attr(refreshDecks, "app:icon"))
        assertTrue("contentDescription must be a string ref for refreshDecksButton",
            attr(refreshDecks, "android:contentDescription").startsWith("@string/"))
    }

    @Test
    fun `aiModelRefresh and decisionModelRefresh are ImageButtons`() {
        val doc = parseXml("src/main/res/layout/activity_settings.xml")
        val aiRefresh = requireNotNull(findElement(doc, "@+id/aiModelRefresh")) { "aiModelRefresh missing" }
        val decisionRefresh = requireNotNull(findElement(doc, "@+id/decisionModelRefresh")) { "decisionModelRefresh missing" }

        for ((el, label) in listOf(
            aiRefresh to "aiModelRefresh",
            decisionRefresh to "decisionModelRefresh"
        )) {
            assertEquals("wrong root tag for $label", "ImageButton", el.tagName)
            assertEquals("width for $label", "48dp", attr(el, "android:layout_width"))
            assertEquals("height for $label", "48dp", attr(el, "android:layout_height"))
            assertEquals("src for $label", "@drawable/ic_refresh", attr(el, "android:src"))
            assertEquals("scaleType for $label", "center", attr(el, "android:scaleType"))
            assertTrue("contentDescription must be a string ref for $label",
                attr(el, "android:contentDescription").startsWith("@string/"))
            assertFalse("no android:padding for $label", el.hasAttribute("android:padding"))
            assertFalse("no android:layout_marginTop for $label", el.hasAttribute("android:layout_marginTop"))
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
        assertEquals("Refresh decision models", stringValue(doc, "settings_decision_model_refresh"))
    }
}
