package com.evan.voicesrs

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {

    @Test
    fun `formatReport includes header thread and stack frames`() {
        val t = RuntimeException("boom")
        val r = CrashReporter.formatReport("app version: 0.1.2 (3)", "main", t)
        assertTrue(r.contains("0.1.2 (3)"))
        assertTrue(r.contains("thread: main"))
        assertTrue(r.contains("boom"))
        assertTrue(r.contains("at com.evan.voicesrs.CrashReporterTest"))
    }

    @Test
    fun `formatReport includes the cause chain`() {
        val t = RuntimeException("wrapper", IllegalStateException("root cause"))
        val r = CrashReporter.formatReport("h", "t", t)
        assertTrue(r.contains("root cause"))
        assertTrue(r.contains("Caused by"))
    }
}
