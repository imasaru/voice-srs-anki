package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H153kTest {
    private fun src() = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()

    @Test fun a_noRawInfoWithTagLeft() {
        val raw = Regex("""(^|[^A-Za-z])Log\.i\(TAG,""", RegexOption.MULTILINE).findAll(src()).count()
        assertEquals(0, raw)
    }

    @Test fun b_infoUsesDLog() {
        val n = Regex("""DLog\.i\(TAG,""").findAll(src()).count()
        assertTrue("DLog i count $n", n >= 100)
        assertTrue(src().contains("DLog.i(TAG, \"onCreate\")"))
    }

    @Test fun c_otherLogsUnchanged() {
        assertTrue(src().contains("Log.i(\"MediaBtn\","))
        assertTrue(Regex("""(^|[^A-Za-z])Log\.d\(TAG,""", RegexOption.MULTILINE).containsMatchIn(src()))
        assertTrue(!src().contains("DLog.d("))
    }
}
