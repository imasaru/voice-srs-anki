package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Verifies the thread sheet scrolls as one list: the question/explanation/list are
 *  inside a NestedScrollView, while the input row stays pinned outside it. */
class ThreadSheetLayoutTest {
    private val idNs = "http://schemas.android.com/apk/res/android"

    private fun loadDoc(): org.w3c.dom.Document {
        val p = File("src/main/res/layout/sheet_thread.xml")
        assertTrue("sheet_thread.xml not found at ${p.absolutePath}", p.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(p.inputStream())
    }

    private fun byId(doc: org.w3c.dom.Document, localName: String): Element? {
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) {
                val el = n as Element
                val v = el.getAttributeNS(idNs, "id") ?: continue
                if (v.endsWith("/$localName") || v == "+$localName" || v == localName) {
                    return el
                }
            }
        }
        return null
    }

    private fun isDescendant(ancestor: Element?, node: Node?): Boolean {
        if (ancestor == null || node == null) return false
        var cur: Node? = node.parentNode
        while (cur != null) {
            if (cur === ancestor) return true
            cur = cur.parentNode
        }
        return false
    }

    @Test
    fun `question, explanation and list share one scroll view with a pinned input`() {
        val doc = loadDoc()

        val scroll = byId(doc, "threadScroll")
        assertNotNull("must have a NestedScrollView @+id/threadScroll", scroll)
        assertTrue("threadScroll must be androidx.core.widget.NestedScrollView",
            scroll!!.tagName == "androidx.core.widget.NestedScrollView")

        val q = byId(doc, "threadQuestion")
        val sections = byId(doc, "threadSections")
        val list = byId(doc, "threadList")
        assertNotNull("threadQuestion", q)
        assertNotNull("threadSections", sections)
        assertNotNull("threadList", list)

        assertTrue("threadQuestion must be inside threadScroll", isDescendant(scroll, q))
        assertTrue("threadSections must be inside threadScroll", isDescendant(scroll, sections))
        assertTrue("threadList must be inside threadScroll", isDescendant(scroll, list))

        val input = byId(doc, "threadInput")
        val send = byId(doc, "threadSend")
        assertNotNull("threadInput", input)
        assertNotNull("threadSend", send)
        assertFalse("threadInput must stay outside the scroll view", isDescendant(scroll, input))
        assertFalse("threadSend must stay outside the scroll view", isDescendant(scroll, send))

        assertEqualsAttr(scroll, "layout_height", "0dp")
        assertEqualsAttr(scroll, "layout_weight", "1")

        assertEqualsAttr(list!!, "layout_height", "wrap_content")
        assertEqualsAttr(list, "nestedScrollingEnabled", "false")
    }

    private fun assertEqualsAttr(el: Element, attr: String, expected: String) {
        val actual = el.getAttribute("android:$attr")
        assertTrue("android:$attr should be '$expected' but was '$actual'", actual == expected)
    }
}
