package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TimerRowLayoutTest {

    private val resRoot = File("src/main/res")

    private fun parse(vararg path: String): Document {
        val file = File(resRoot, path.joinToString("/"))
        assertTrue("Missing $file", file.exists())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val builder = factory.newDocumentBuilder()
        return file.inputStream().use { builder.parse(it) }
    }

    private fun elementById(doc: Document, id: String): Element? {
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.getAttribute("android:id") == "@+id/$id") return el
        }
        return null
    }

    @Test
    fun `timer row has the five expected ids`() {
        val idSet = mutableSetOf<String>()
        val nodes = parse("layout", "include_timer_row.xml").getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val raw = el.getAttribute("android:id")
            if (raw.startsWith("@+id/")) idSet += raw.removePrefix("@+id/")
        }
        listOf("timerRow", "timerMinus", "timerValue", "timerPlus", "timerSwitch").forEach {
            assertTrue("include_timer_row.xml missing id $it", idSet.contains(it))
        }
    }

    @Test
    fun `minus and plus are 48dp targets and the switch is natural size`() {
        val minus = elementById(parse("layout", "include_timer_row.xml"), "timerMinus")
        val plus = elementById(parse("layout", "include_timer_row.xml"), "timerPlus")
        val sw = elementById(parse("layout", "include_timer_row.xml"), "timerSwitch")
        assertNotNull(minus)
        assertNotNull(plus)
        assertNotNull(sw)
        assertEquals("48dp", minus!!.getAttribute("android:layout_height"))
        assertEquals("48dp", plus!!.getAttribute("android:layout_height"))
        assertEquals("wrap_content", sw!!.getAttribute("android:layout_height"))
    }

    @Test
    fun `timer row is wired into the folded and wide main layouts`() {
        val folded = File(resRoot, "layout/activity_main.xml").readText()
        assertTrue(folded.contains("@layout/include_timer_row"))
        val statusCard = File(resRoot, "layout/include_status_card.xml").readText()
        assertTrue(statusCard.contains("@layout/include_timer_row"))
    }
}
