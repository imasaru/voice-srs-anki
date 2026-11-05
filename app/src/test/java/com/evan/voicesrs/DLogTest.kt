package com.evan.voicesrs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val calls = mutableListOf<String>()
    private val saved = DLog.logcat

    @After fun reset() { DiagLog.sink = null; DLog.logcat = saved }

    private fun fake() { DLog.logcat = { l, t, m, e -> calls.add("$l $t $m ${e?.javaClass?.simpleName}") } }

    private fun read(f: DiagLogFile): String {
        for (n in 0 until 100) {
            if (f.file(0).exists()) { val s = f.file(0).readText(); if (s.isNotEmpty()) return s }
            Thread.sleep(20)
        }
        return ""
    }

    @Test fun a_forwardsToSinkAndLogcat() {
        fake()
        val f = DiagLogFile(tmp.newFolder("a"))
        DiagLog.sink = f
        DLog.w("Drill", "tts failed", IllegalStateException("x"))
        assertEquals(listOf("w Drill tts failed IllegalStateException"), calls)
        assertTrue(read(f).trimEnd().endsWith(" Drill W msg=\"tts failed\" err=IllegalStateException"))
    }

    @Test fun b_sinkDisabledStillLogsLogcat() {
        fake()
        DiagLog.sink = null
        DLog.i("Drill", "onCreate")
        DLog.e("Drill", "boom")
        assertEquals(listOf("i Drill onCreate null", "e Drill boom null"), calls)
    }

    @Test fun c_logcatThrowIsSwallowed() {
        DLog.logcat = { _, _, _, _ -> throw RuntimeException("not mocked") }
        val f = DiagLogFile(tmp.newFolder("c"))
        DiagLog.sink = f
        DLog.i("Drill", "ok")
        assertTrue(read(f).trimEnd().endsWith(" Drill I msg=ok"))
    }
}
