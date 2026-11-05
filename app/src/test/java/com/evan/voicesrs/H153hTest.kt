package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class H153hTest {
    private fun src() = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()

    @Test fun a_gradedLineAfterVerdictLog() {
        val s = src()
        val i = s.indexOf("Log.i(TAG, OwnWordsJudge.verdictLog(")
        assertTrue(i >= 0)
        val j = s.indexOf("""DiagLog.log("JUDGE", "graded", listOf("cid" to cardCid.current()""", i)
        assertTrue(j > i && j - i < 400)
    }

    @Test fun b_fallbackLineAfterFallbackLog() {
        val s = src()
        val i = s.indexOf("own-words judge rating=fallback")
        assertTrue(i >= 0)
        val j = s.indexOf("""DiagLog.log("JUDGE", "fallback", listOf("cid" to cardCid.current()""", i)
        assertTrue(j > i && j - i < 300)
    }
}
