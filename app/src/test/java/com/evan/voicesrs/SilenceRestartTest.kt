package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceRestartTest {

    @Test
    fun `a restart waits before recreating`() {
        var destroy = 0
        var begin = 0
        var delay: Long? = null
        var block: (() -> Unit)? = null
        SilenceRestart.schedule(300L, { destroy += 1 }, { ms, r -> delay = ms; block = r }, { true }, { begin += 1 })
        assertEquals(1, destroy)
        assertEquals(0, begin)
        assertEquals(300L, delay!!)
        assertNotNull(block)
        block!!()
        assertEquals(1, begin)
        var block2: (() -> Unit)? = null
        SilenceRestart.schedule(300L, { destroy += 1 }, { _, r -> block2 = r }, { false }, { begin += 1 })
        assertEquals(2, destroy)
        block2!!()
        assertEquals(1, begin)
    }

    @Test
    fun `b error 11 after a restart retries once`() {
        assertEquals(SilenceRestart.Plan.Retry(700L), SilenceRestart.onError(11, true, 0, 0L, 10_000L))
        assertEquals(SilenceRestart.Plan.Retry(700L), SilenceRestart.onError(8, true, 0, 0L, 10_000L))
    }

    @Test
    fun `c second busy ends quiet`() {
        assertEquals(SilenceRestart.Plan.EndQuiet, SilenceRestart.onError(11, true, 1, 0L, 10_000L))
        assertEquals(SilenceRestart.Plan.EndQuiet, SilenceRestart.onError(11, true, 0, 9_000L, 10_000L))
    }

    @Test
    fun `d first recognizer and other errors are not mine`() {
        assertEquals(SilenceRestart.Plan.NotMine, SilenceRestart.onError(11, false, 0, 0L, 10_000L))
        assertEquals(SilenceRestart.Plan.NotMine, SilenceRestart.onError(7, true, 0, 0L, 10_000L))
        assertEquals(SilenceRestart.Plan.NotMine, SilenceRestart.onError(5, true, 0, 0L, 10_000L))
    }

    @Test
    fun `e source guard`() {
        val t = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()
        val after = t.indexOf("private fun restartSilenceListenAfter(session: Int, delayMs: Long)")
        assertTrue(after >= 0)
        val body = t.substring(after, t.indexOf("\n    }\n", after))
        assertTrue(body.contains("SilenceRestart.schedule("))
        assertTrue(body.contains("destroy = { destroyRecognizerOnMain() }"))
        val b = body.indexOf("begin =")
        assertTrue(b >= 0 && body.indexOf("beginListenOnMain(session, epoch)") >= b)
        val e = t.indexOf("override fun onError(error: Int)")
        val r = t.indexOf("if (retryable && !clientRetryUsed")
        assertTrue(e >= 0 && r >= 0)
        assertTrue(t.indexOf("SilenceRestart.onError(", e) < r)
    }
}
