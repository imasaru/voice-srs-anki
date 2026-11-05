package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** h155b: skip sets skipNotice fields; ChatPane feeds DrillNotice.chatNotice into the list. */
class H155bTest {
    private fun src(name: String) = File("src/main/java/com/evan/voicesrs/$name").readText()

    @Test fun a_skipOnlineSetsSkipNotice() {
        val s = src("DrillService.kt")
        val at = s.indexOf("private fun skipOnline(")
        assertTrue("skipOnline missing", at >= 0)
        val body = s.substring(at, minOf(s.length, at + 900))
        assertTrue(body.contains("postStatusNotice(say)"))
        assertTrue(body.contains("skipNotice = say"))
        assertTrue(body.contains("skipNoticeOrdinal = it.questionOrdinal"))
    }

    @Test fun b_offlineSkipSetsSkipNotice() {
        val s = src("DrillService.kt")
        val off = s.indexOf("offline skip id=")
        assertTrue("offline skip log missing", off >= 0)
        val start = s.lastIndexOf("CardStep.Skipped -> {", off)
        assertTrue("offline Skipped branch missing", start >= 0)
        val body = s.substring(start, minOf(s.length, start + 700))
        assertTrue(body.contains("postStatusNotice(say)"))
        assertTrue(body.contains("skipNotice = say"))
        assertTrue(body.contains("skipNoticeOrdinal = it.questionOrdinal"))
    }

    @Test fun c_chatPaneUsesChatNotice() {
        val s = src("ChatPane.kt")
        val at = s.indexOf("private fun render()")
        assertTrue("render missing", at >= 0)
        val body = s.substring(at, minOf(s.length, at + 800))
        assertTrue(body.contains("DrillNotice.chatNotice(GeneralChat.notice(), DrillUi.state)"))
    }
}
