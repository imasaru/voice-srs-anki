package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagLogTest {

    @Test
    fun a_formatsIsoJstTagEventKv() {
        assertEquals("1970-01-01T09:00:00.000+09:00 LISTEN start card=3 ms=1500", DiagLog.line(0L, "LISTEN", "start", listOf("card" to 3, "ms" to 1500)))
    }

    @Test
    fun b_escapesNewlinesAndQuotesSpaces() {
        assertEquals("\"a b\"", DiagLog.value("a b"))
        assertEquals("x\\ny", DiagLog.value("x\ny"))
        assertEquals("\"say \\\"hi\\\"\"", DiagLog.value("say \"hi\""))
        assertEquals("none", DiagLog.value(null))
        assertEquals("\"\"", DiagLog.value(""))
    }

    @Test
    fun c_usesGivenZone() {
        assertEquals("1970-01-01T00:00:00.000Z T e", DiagLog.line(0L, "T", "e", zone = java.time.ZoneId.of("UTC")))
    }

    @Test
    fun d_noKvNoTrailingSpace() {
        assertEquals("1970-01-01T09:00:01.234+09:00 AI ok", DiagLog.line(1234L, "AI", "ok"))
    }
}
