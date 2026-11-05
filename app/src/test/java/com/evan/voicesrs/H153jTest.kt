package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H153jTest {
    private fun src() = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()

    @Test fun a_noRawWarnOrErrorLeft() {
        val raw = Regex("""(^|[^A-Za-z])Log\.(w|e)\(TAG,""", RegexOption.MULTILINE).findAll(src()).count()
        assertEquals(0, raw)
    }

    @Test fun b_warnAndErrorUseDLog() {
        val n = Regex("""DLog\.(w|e)\(TAG,""").findAll(src()).count()
        assertTrue("DLog w/e count $n", n >= 50)
    }

    @Test fun c_infoUnchanged() {
        assertTrue(src().contains("Log.i(TAG, \"onCreate\""))
    }
}
