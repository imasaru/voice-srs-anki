package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** h114: exam date is read-only unless "Apply exam schedule to Anki deck" is on. */
class H114Test {

    private val layoutPath = "src/main/res/layout/activity_settings.xml"
    private val stringsPath = "src/main/res/values/strings.xml"
    private val examSyncPath = "src/main/java/com/evan/voicesrs/ExamSync.kt"

    private fun state(date: String? = "2026-11-15"): ExamStore.State =
        ExamStore.State(date = date?.let { java.time.LocalDate.parse(it) })

    private fun cfg(apply: Boolean = false): AnkiConfig =
        AnkiConfig(url = "", apiKey = "", deck = "").copy(examApplyToAnki = apply)

    @Test fun a_defaultIsReadonly() {
        assertFalse("examApplyToAnki defaults to false", cfg().examApplyToAnki)
    }

    @Test fun b_shouldApplyRespectsSwitchAndDate() {
        assertFalse("off + date -> no apply", ExamSync.shouldApply(cfg(false), state()))
        assertTrue("on + date -> apply", ExamSync.shouldApply(cfg(true), state()))
        assertFalse("on + no date -> no apply", ExamSync.shouldApply(cfg(true), state(null)))
    }

    @Test fun c_runChecksShouldApplyBeforeExecute() {
        val src = File(examSyncPath).readText()
        val sa = src.indexOf("shouldApply(")
        val ex = src.indexOf("execute(")
        assertTrue("missing shouldApply call", sa >= 0)
        assertTrue("missing execute call", ex >= 0)
        assertTrue("shouldApply( must come before execute(", sa < ex)
    }

    @Test fun d_examDeckForIgnoresUnsavedText() {
        assertEquals("Saved", examDeckFor("half-typed", "Saved"))
    }

    private fun allEls(): List<org.w3c.dom.Element> {
        val f = File(layoutPath)
        assertTrue("Missing file: $layoutPath", f.isFile)
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val list = doc.getElementsByTagName("*")
        return (0 until list.length).mapNotNull { list.item(it) as? org.w3c.dom.Element }
    }

    private fun idx(els: List<org.w3c.dom.Element>, id: String): Int =
        els.indexOfFirst { it.getAttribute("android:id") == "@+id/$id" }

    @Test fun e_examApplySwitchSitsBetweenHelpAndDate() {
        val els = allEls()
        assertTrue("examApplySwitch present", idx(els, "examApplySwitch") >= 0)
        val help = idx(els, "examHelpText")
        val sw = idx(els, "examApplySwitch")
        val date = idx(els, "examDateButton")
        assertTrue("switch after help", sw > help)
        assertTrue("switch before date", sw < date)
    }

    @Test fun f_examStringsExist() {
        val s = File(stringsPath).readText()
        for (name in listOf(
            "settings_exam_apply",
            "settings_exam_help_readonly",
            "settings_exam_save_failed",
            "settings_exam_countdown_readonly"
        )) {
            assertTrue("Missing string: $name",
                Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(s) != null)
        }
    }
}
