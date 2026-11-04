package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h148b: a status-line notice while the drill waits for audio focus. */
class H148bTest {
    @Test fun otherAudioConstant() {
        assertEquals("Paused for other audio", DrillNotice.OTHER_AUDIO)
    }

    @Test fun afterFocusWaitDropsOurNotice() {
        assertEquals("Answer", DrillNotice.afterFocusWait(DrillNotice.OTHER_AUDIO, "Answer"))
    }

    @Test fun afterFocusWaitKeepsNewerStatus() {
        assertEquals("Reading the question", DrillNotice.afterFocusWait("Reading the question", "Answer"))
    }

    @Test fun afterFocusWaitEmpty() {
        assertEquals("", DrillNotice.afterFocusWait("", ""))
    }

    @Test fun awaitFocusResumePostsAndClearsNotice() {
        val src = drillServiceSrc()
        val idx = src.indexOf("private fun awaitFocusResume(): Boolean {")
        assertTrue("awaitFocusResume not found", idx >= 0)
        val speak = src.indexOf("override fun speak(")
        assertTrue("override fun speak( missing", speak > idx)
        val body = src.substring(idx, speak)
        assertTrue("missing postStatusNotice(OTHER_AUDIO)", body.contains("postStatusNotice(DrillNotice.OTHER_AUDIO)"))
        assertTrue("missing clearFocusNotice(statusBefore) x2", body.count { it == 'c' } >= 0 && body.split("clearFocusNotice(statusBefore)").size - 1 >= 2)
        assertTrue("missing afterFocusWait", body.contains("DrillNotice.afterFocusWait(it.status, before)"))
        val clearIdx = body.indexOf("clearFocusNotice(statusBefore)")
        val acquireIdx = body.indexOf("return acquireFocus()")
        assertTrue("clearFocusNotice(statusBefore) must appear before return acquireFocus()", clearIdx >= 0 && clearIdx < acquireIdx)
    }

    private fun drillServiceSrc(): String {
        for (c in listOf("src/main/java/com/evan/voicesrs/DrillService.kt", "app/src/main/java/com/evan/voicesrs/DrillService.kt")) {
            val f = java.io.File(c)
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("DrillService.kt not found")
    }
}
