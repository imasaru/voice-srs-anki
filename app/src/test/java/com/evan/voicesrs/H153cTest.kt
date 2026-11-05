package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class H153cTest {
    @get:Rule val tmp = TemporaryFolder()

    @After fun reset() { DiagLog.sink = null }

    @Test fun a_logWithoutSinkDoesNotThrow() {
        DiagLog.sink = null
        DiagLog.log("APP", "start")
        assertNull(DiagLog.sink)
    }

    @Test fun b_logWritesThroughSink() {
        val f = DiagLogFile(tmp.newFolder("b"))
        DiagLog.sink = f
        DiagLog.log("APP", "start", listOf("k" to "v"), now = 0L)
        var text = ""
        for (n in 0 until 100) {
            if (f.file(0).exists()) {
                text = f.file(0).readText()
                if (text.isNotEmpty()) break
            }
            Thread.sleep(20)
        }
        assertEquals("1970-01-01T09:00:00.000+09:00 APP start k=v\n", text)
    }

    @Test fun c_voiceSrsAppInstallsSink() {
        val src = File("src/main/java/com/evan/voicesrs/VoiceSrsApp.kt").let { if (it.exists()) it else File("app/src/main/java/com/evan/voicesrs/VoiceSrsApp.kt") }.readText()
        assertTrue(src.contains("DiagLog.sink = DiagLogFile(File(filesDir, \"logs\"))"))
        assertTrue(src.contains("DiagLog.log(\"APP\", \"start\""))
    }
}
