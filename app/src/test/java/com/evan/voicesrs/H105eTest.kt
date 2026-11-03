package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H105eTest {
    @Test fun routeMicIsLocalFunctionPicksById() {
        val src = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()
        assertTrue(src.contains("fun routeMic()"))
        assertTrue(src.contains("raw.firstOrNull { it.id == picked.id }"))
        assertFalse(src.contains("MicRoute.typeOf(it.type) == picked.type"))
    }

    @Test fun routeMicCalledRightAfterAcquireFocusInLoop() {
        val src = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()
        val c = src.indexOf("private fun captureListen(")
        val w = src.indexOf("while (true)", c)
        val f = src.indexOf("if (!acquireFocus()) return null", w)
        val r = src.indexOf("routeMic()", f)
        assertTrue("captureListen", c >= 0)
        assertTrue("while(true) after captureListen", w > c)
        assertTrue("acquireFocus after while", f > w)
        assertTrue("routeMic after acquireFocus", r > f)
        assertTrue("routeMic is right after acquireFocus", r - f < 80)
    }

    @Test fun settingsShowsSummaryAsTextView() {
        val xml = File("src/main/res/layout/activity_settings.xml").readText()
        assertTrue(xml.contains("@+id/useHeadsetMicSummary"))
        assertFalse(xml.contains("android:summary=\"@string/settings_use_headset_mic_summary\""))
    }
}
