package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** h153r: diag log row columns use the same weights as the header. */
class H153rTest {
    private fun weightOf(xml: String, id: String): String {
        val start = xml.indexOf("@+id/$id")
        require(start >= 0) { "missing $id" }
        val open = xml.lastIndexOf("<", start)
        val close = xml.indexOf(">", start)
        val tag = xml.substring(open, close)
        val m = Regex("layout_weight=\"([0-9.]+)\"").find(tag)
        return m?.groupValues?.get(1) ?: "none"
    }
    private fun widthOf(xml: String, id: String): String {
        val start = xml.indexOf("@+id/$id")
        val open = xml.lastIndexOf("<", start)
        val close = xml.indexOf(">", start)
        return Regex("layout_width=\"([^\"]+)\"").find(xml.substring(open, close))!!.groupValues[1]
    }
    private val row = File("src/main/res/layout/item_diag_log_row.xml").readText()
    private val head = File("src/main/res/layout/activity_diag_log.xml").readText()

    @Test fun a_timeMatchesHeader() {
        assertEquals(weightOf(head, "diagLogHeaderTime"), weightOf(row, "diagLogRowTime"))
        assertEquals("0dp", widthOf(row, "diagLogRowTime"))
    }
    @Test fun b_levelMatchesHeader() {
        assertEquals(weightOf(head, "diagLogHeaderLevel"), weightOf(row, "diagLogRowLevel"))
        assertEquals("0dp", widthOf(row, "diagLogRowLevel"))
    }
    @Test fun c_messageMatchesHeader() {
        assertEquals(weightOf(head, "diagLogHeaderMessage"), weightOf(row, "diagLogRowMessage"))
        assertEquals("0dp", widthOf(row, "diagLogRowMessage"))
    }
}
