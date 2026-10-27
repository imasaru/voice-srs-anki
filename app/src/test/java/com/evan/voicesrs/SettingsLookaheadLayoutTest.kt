package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File

/** Item 21: Lookahead sits in the card explanation section, above the Decision model heading. */
class SettingsLookaheadLayoutTest {

    private val path = "src/main/res/layout/activity_settings.xml"

    private fun all(): List<Element> {
        val f = File(path)
        assertTrue("Missing file: $path", f.isFile)
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val list = doc.getElementsByTagName("*")
        return (0 until list.length).mapNotNull { list.item(it) as? Element }
    }

    private fun idx(els: List<Element>, id: String): Int =
        els.indexOfFirst { it.getAttribute("android:id") == "@+id/$id" }

    private fun headingIdx(els: List<Element>): Int =
        els.indexOfFirst { it.getAttribute("android:text") == "@string/settings_decision_heading" }

    @Test fun a_lookaheadAfterKeyStatus() {
        val els = all()
        assertTrue(idx(els, "aiKeyStatus") >= 0)
        assertTrue(idx(els, "aiLookaheadLayout") > idx(els, "aiKeyStatus"))
    }

    @Test fun b_lookaheadBeforeDecisionHeading() {
        val els = all()
        assertTrue(headingIdx(els) >= 0)
        assertTrue(idx(els, "aiLookaheadLayout") < headingIdx(els))
        assertTrue(idx(els, "aiLookaheadLayout") < idx(els, "decisionEnabledSwitch"))
    }

    @Test fun c_exactlyOneLookahead() {
        val els = all()
        assertEquals(1, els.count { it.getAttribute("android:id") == "@+id/aiLookaheadLayout" })
        assertEquals(1, els.count { it.getAttribute("android:id") == "@+id/aiLookaheadInput" })
    }

    @Test fun d_sameParentAsKeyStatus() {
        val els = all()
        val look = els[idx(els, "aiLookaheadLayout")]
        val key = els[idx(els, "aiKeyStatus")]
        assertSame(key.parentNode, look.parentNode)
    }

    @Test fun e_inputKeptInsideAndUnchanged() {
        val els = all()
        val input = els[idx(els, "aiLookaheadInput")]
        assertEquals("@+id/aiLookaheadLayout", (input.parentNode as Element).getAttribute("android:id"))
        assertEquals("number", input.getAttribute("android:inputType"))
        assertEquals("actionDone", input.getAttribute("android:imeOptions"))
        val look = els[idx(els, "aiLookaheadLayout")]
        assertEquals("@string/settings_ai_lookahead", look.getAttribute("android:hint"))
        assertEquals("@string/settings_ai_lookahead_help", look.getAttribute("app:helperText"))
    }

    @Test fun f_batchSwitchStaysAfterDecision() {
        val els = all()
        assertTrue(idx(els, "aiBatchSwitch") > idx(els, "decisionModelRefresh"))
    }
}
