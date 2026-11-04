package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h148: the focus wait gives up after WAIT_CAP_MS and takes focus back. */
class H148Test {
    @Test fun waitCapConstant() { assertEquals(20_000L, FocusPolicy.WAIT_CAP_MS) }

    @Test fun waitTimedOutBoundaries() {
        assertFalse(FocusPolicy.waitTimedOut(1_000L, 20_999L))
        assertTrue(FocusPolicy.waitTimedOut(1_000L, 21_000L))
        assertTrue(FocusPolicy.waitTimedOut(1_000L, 60_000L))
        assertFalse(FocusPolicy.waitTimedOut(5_000L, 5_000L))
    }

    @Test fun awaitFocusResumeReRequestsFocus() {
        val src = drillServiceSrc()
        val idx = src.indexOf("private fun awaitFocusResume(): Boolean {")
        assertTrue("awaitFocusResume not found", idx >= 0)
        val speak = src.indexOf("override fun speak(")
        assertTrue("override fun speak( missing", speak > idx)
        val body = src.substring(idx, speak)
        assertTrue("missing startedAt", body.contains("val startedAt = SystemClock.elapsedRealtime()"))
        assertTrue("missing waitTimedOut", body.contains("FocusPolicy.waitTimedOut(startedAt, SystemClock.elapsedRealtime())"))
        assertTrue("missing re-request focus", body.contains("return acquireFocus()"))
    }

    private fun drillServiceSrc(): String {
        for (c in listOf("src/main/java/com/evan/voicesrs/DrillService.kt", "app/src/main/java/com/evan/voicesrs/DrillService.kt")) {
            val f = java.io.File(c)
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("DrillService.kt not found")
    }
}
