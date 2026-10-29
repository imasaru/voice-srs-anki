package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File

/** Item 69B: Settings "Revert exam mode" button sits between the date/Clear row and Target retention. */
class SettingsExamRevertLayoutTest {

    private val layoutPath = "src/main/res/layout/activity_settings.xml"
    private val stringsPath = "src/main/res/values/strings.xml"

    private fun all(): List<Element> {
        val f = File(layoutPath)
        assertTrue("Missing file: $layoutPath", f.isFile)
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val list = doc.getElementsByTagName("*")
        return (0 until list.length).mapNotNull { list.item(it) as? Element }
    }

    private fun idx(els: List<Element>, id: String): Int =
        els.indexOfFirst { it.getAttribute("android:id") == "@+id/$id" }

    private fun readStrings(): String {
        val f = File(stringsPath)
        assertTrue("Missing file: $stringsPath", f.isFile)
        return f.readText()
    }

    private fun stringValue(name: String): String {
        val regex = Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val m = regex.find(readStrings())
        assertTrue("Missing string: $name", m != null)
        return m!!.groupValues[1]
    }

    @Test fun a_revertButtonIsGoneTextButton() {
        val els = all()
        val b = els[idx(els, "examRevertButton")]
        assertEquals("@style/Widget.Material3.Button.TextButton", b.getAttribute("style"))
        assertEquals("gone", b.getAttribute("android:visibility"))
        assertEquals("@string/settings_exam_revert", b.getAttribute("android:text"))
    }

    @Test fun b_revertButtonBetweenClearAndTarget() {
        val els = all()
        val clear = idx(els, "examClearButton")
        val revert = idx(els, "examRevertButton")
        val target = idx(els, "examTargetLayout")
        assertTrue("revert after clear", revert > clear)
        assertTrue("revert before target", revert < target)
    }

    @Test fun c_statusTextGoneBetweenRevertAndTarget() {
        val els = all()
        val revert = idx(els, "examRevertButton")
        val status = idx(els, "examStatusText")
        val target = idx(els, "examTargetLayout")
        assertEquals("gone", els[status].getAttribute("android:visibility"))
        assertTrue("status after revert", status > revert)
        assertTrue("status before target", status < target)
    }

    @Test fun d_revertStringsMatch() {
        assertEquals("Revert exam mode?", stringValue("settings_exam_revert_title"))
        assertEquals(
            "Puts this deck back on its original preset and moves pulled-forward cards back to their old due dates.",
            stringValue("settings_exam_revert_body")
        )
        assertEquals("Revert", stringValue("settings_exam_revert_confirm"))
    }

    @Test fun e_dateAndClearUnchanged() {
        val els = all()
        val date = els[idx(els, "examDateButton")]
        assertEquals("@style/Widget.Material3.Button.TonalButton", date.getAttribute("style"))
        assertEquals("1", date.getAttribute("android:layout_weight"))
        val clear = els[idx(els, "examClearButton")]
        assertEquals("@style/Widget.Material3.Button.TextButton", clear.getAttribute("style"))
        assertEquals("@string/settings_exam_clear", clear.getAttribute("android:text"))
    }
}
