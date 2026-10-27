package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/**
 * Verifies the two Android Lint security-baseline fixes:
 *  - themes.xml: the API-29 `android:forceDarkAllowed` item carries
 *    tools:targetApi="q" so it degrades on minSdk 26.
 *  - include_chat_composer.xml: the ImageButton tints use app:tint
 *    (Material) instead of android:tint (API 29+).
 */
class LintFixesLayoutTest {

    private fun parseXml(path: String): Document {
        val f = File(path)
        assertTrue("Missing file: $path", f.isFile)
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        return factory.newDocumentBuilder().parse(f)
    }

    private fun elements(doc: Document, tag: String): List<Element> {
        val list = doc.getElementsByTagName(tag)
        val out = ArrayList<Element>()
        for (i in 0 until list.length) {
            val el = list.item(i) as? Element ?: continue
            out.add(el)
        }
        return out
    }

    @Test
    fun `forceDarkAllowed keeps value false and targets q`() {
        val doc = parseXml("src/main/res/values/themes.xml")
        val items = elements(doc, "item")
        val target = items.firstOrNull { it.getAttribute("name") == "android:forceDarkAllowed" }
        assertNotNull("missing android:forceDarkAllowed item", target)
        assertEquals("forceDarkAllowed value", "false", target!!.textContent)
        assertEquals("forceDarkAllowed targetApi", "q", target.getAttribute("tools:targetApi"))
    }

    @Test
    fun `composer ImageButtons use app tint not android tint`() {
        val doc = parseXml("src/main/res/layout/include_chat_composer.xml")
        val buttons = elements(doc, "ImageButton")
        assertTrue("expected at least one ImageButton", buttons.isNotEmpty())
        for (b in buttons) {
            assertFalse("ImageButton ${b.getAttribute("android:id")} still has android:tint",
                b.hasAttribute("android:tint"))
        }
        val send = buttons.firstOrNull { it.getAttribute("android:id") == "@+id/chatSend" }
        assertNotNull("missing chatSend ImageButton", send)
        assertEquals("chatSend tint", "@color/chat_send_icon", send!!.getAttribute("app:tint"))
        assertTrue("expected an ImageButton with app:tint=@color/chat_accent",
            buttons.any { it.getAttribute("app:tint") == "@color/chat_accent" })
    }
}
