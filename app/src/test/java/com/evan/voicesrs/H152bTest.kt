package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h152b (item 62 part 2): DrillService uses SilentAsk.Action to handle "rate again". */
class H152bTest {
    private fun readSrc(rel: String): String =
        listOf(rel, "app/$rel").firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }
            ?: throw IllegalStateException("$rel not found")

    @Test fun drillServiceHandlesRateAgain() {
        val src = readSrc("src/main/java/com/evan/voicesrs/DrillService.kt")
        assertTrue("askSilentFirst returns SilentAsk.Action", src.contains("private fun askSilentFirst(listenWaitMs: Long): SilentAsk.Action"))
        assertTrue("uses SilentAsk.action", src.contains("return SilentAsk.action(reply)"))
        assertTrue("when (askSilentFirst", src.contains("when (askSilentFirst(listenWaitMs))"))
        assertTrue("RATE_AGAIN arm", src.contains("SilentAsk.Action.RATE_AGAIN ->"))
        assertTrue("spoke MARKED_AGAIN", src.contains("speak(SilentAsk.MARKED_AGAIN)"))
        assertTrue("KEEP_VOICE arm", src.contains("SilentAsk.Action.KEEP_VOICE ->"))
        assertTrue("GO_SILENT arm", src.contains("SilentAsk.Action.GO_SILENT ->"))
        assertFalse("old Boolean guard gone", src.contains("if (!askSilentFirst("))
        assertFalse("old goSilent call gone", src.contains("SilentAsk.goSilent(reply)"))
    }
}
