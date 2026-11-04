package com.evan.voicesrs

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class H140Test {
    @Before
    fun reset() {
        ReviewLog.clear()
    }
    @After
    fun done() {
        ReviewLog.clear()
    }
    private fun grade(ease: Int, cached: String?, auto: Boolean): ReviewTurn =
        ReviewLog.appendAfterGrade(ease, 1L, 2L, "f", "b", "h", "s", cached, true, auto)!!

    @Test
    fun `correct Good with cached prefetch obeys the chips`() {
        val t = grade(3, "prefetched", AutoExplain.shouldAuto(AutoExplain.DEFAULT, 3))
        assertTrue(t.explanation is ExplState.None)
        assertTrue(AutoExplain.showButton(t.explanation))
    }
    @Test
    fun `Easy off every chip is None on a cache hit`() {
        val t = grade(4, "prefetched", AutoExplain.shouldAuto(emptySet(), 4))
        assertTrue(t.explanation is ExplState.None)
        assertTrue(AutoExplain.showButton(t.explanation))
    }
    @Test
    fun `Again on chips is Ready cached on a hit`() {
        val t = grade(1, "prefetched", AutoExplain.shouldAuto(AutoExplain.DEFAULT, 1))
        val r = t.explanation as ExplState.Ready
        assertEquals("prefetched", r.text)
        assertTrue(r.cached)
    }
    @Test
    fun `Hard on chips stays Queued on a miss`() {
        val t = grade(2, null, true)
        assertTrue(t.explanation is ExplState.Queued)
    }
    @Test
    fun `ExplainCoordinator gates the cached-ready path on auto`() {
        val src = File(System.getProperty("user.dir"), "../app/src/main/java/com/evan/voicesrs/ExplainCoordinator.kt").readText()
        assertTrue(src.contains("cached != null && auto"))
    }
}
