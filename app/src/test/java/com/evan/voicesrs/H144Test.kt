package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class H144Test {
    private val xml = File("src/main/res/layout/include_timer_row.xml")
    private val pill = File("src/main/res/drawable/timer_value_pill.xml")
    private val doc: Element by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(InputSource(xml.reader())).documentElement
    }

    private fun attr(el: Element, n: String): String? {
        val a = el.attributes
        for (i in 0 until a.length) {
            val at = a.item(i)
            if (at.nodeName == n || at.nodeName.endsWith(":$n")) return at.nodeValue
        }
        return null
    }

    private fun local(el: Element) =
        if (el.nodeName.contains(":")) el.nodeName.substringAfter(":") else el.nodeName

    private fun kids(el: Element) =
        (0 until el.childNodes.length).map { el.childNodes.item(it) }.filterIsInstance<Element>()

    private fun idOf(el: Element) =
        attr(el, "id")?.removePrefix("@+id/")?.removePrefix("@id/") ?: ""

    private fun find(el: Element, id: String): Element? {
        if (idOf(el) == id) return el
        for (c in kids(el)) find(c, id)?.let { return it }
        return null
    }

    @Test fun `line2 is minus weight-1 bar value frame plus in order`() {
        val k = kids(find(doc, "timerLine2")!!)
        assertEquals(3, k.size)
        assertEquals("timerMinus", idOf(k[0]))
        val f = k[1]
        assertEquals("FrameLayout", local(f))
        assertEquals("1", attr(f, "layout_weight"))
        val ids = kids(f).map { idOf(it) }
        assertTrue("frame: $ids", ids.contains("timerGoalBar") && ids.contains("timerValue"))
        assertEquals("timerPlus", idOf(k[2]))
    }

    @Test fun `bar gap and stop are zero`() {
        val b = find(doc, "timerGoalBar")!!
        assertEquals("0dp", attr(b, "indicatorTrackGapSize"))
        assertEquals("0dp", attr(b, "trackStopIndicatorSize"))
    }

    @Test fun `label has textColor+background and pill drawable exists`() {
        val v = find(doc, "timerValue")!!
        assertNotNull(attr(v, "textColor"))
        assertNotNull(attr(v, "background"))
        assertTrue(pill.exists())
    }
}
