package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h149a: rating listen deadline state and the RateCountdown helper. */
class H149aTest {
    @Test fun secondsLeft() {
        assertNull(RateCountdown.secondsLeft(null, 1_000L))
        assertNull(RateCountdown.secondsLeft(0L, 0L))
        assertEquals(6, RateCountdown.secondsLeft(10_000L, 4_000L))
        assertEquals(7, RateCountdown.secondsLeft(10_000L, 3_500L))
        assertEquals(1, RateCountdown.secondsLeft(10_000L, 9_001L))
        assertNull(RateCountdown.secondsLeft(10_000L, 10_000L))
        assertNull(RateCountdown.secondsLeft(10_000L, 12_000L))
    }
    @Test fun text() {
        assertEquals("Rate 6s", RateCountdown.text(6))
        assertEquals("Rate 12s", RateCountdown.text(12))
    }
    @Test fun visible() {
        assertTrue(RateCountdown.visible(true, false, false, true, 5))
        assertFalse(RateCountdown.visible(false, false, false, true, 5))
        assertFalse(RateCountdown.visible(true, true, false, true, 5))
        assertFalse(RateCountdown.visible(true, false, true, true, 5))
        assertFalse(RateCountdown.visible(true, false, false, false, 5))
        assertFalse(RateCountdown.visible(true, false, false, true, null))
    }
    @Test fun drillUiStateRateDeadlineNull() {
        assertNull(DrillUiState().rateDeadlineMs)
    }
    @Test fun listenPublishesRateDeadline() {
        val src = drillServiceSrc()
        val start = src.indexOf("override fun listen(timeoutMs: Long): String? {")
        assertTrue("listen not found", start >= 0)
        val end = src.indexOf("private fun listenForAnswer(")
        assertTrue("listenForAnswer not found", end > start)
        val body = src.substring(start, end)
        assertTrue("missing publishRateDeadline on grading", body.contains("if (grading) publishRateDeadline(SystemClock.elapsedRealtime() + timeoutMs)"))
        assertTrue("missing finally publishRateDeadline(null)", body.contains("finally { publishRateDeadline(null) }"))
        assertTrue("missing it.copy(rateDeadlineMs = deadlineMs)", body.contains("it.copy(rateDeadlineMs = deadlineMs)"))
        assertFalse("old captureListen line still present", body.contains("val result = captureListen(timeoutMs)"))
    }
    private fun drillServiceSrc(): String {
        for (c in listOf("src/main/java/com/evan/voicesrs/DrillService.kt", "app/src/main/java/com/evan/voicesrs/DrillService.kt")) {
            val f = java.io.File(c)
            if (f.exists()) return f.readText()
        }
        throw IllegalStateException("DrillService.kt not found")
    }
}
