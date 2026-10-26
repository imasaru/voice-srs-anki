package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ReportHeaderTest {

    @Test
    fun `line formats date and issue time in the given zone`() {
        val ts = 1792979377000L
        assertEquals("Study report · 2026-10-26 10:49:37 JST", ReportHeader.line(ts, TimeZone.getTimeZone("Asia/Tokyo")))
        // UTC keeps the same instant, just a different label
        assertEquals("Study report · 2026-10-26 01:49:37 UTC", ReportHeader.line(ts, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `prepend wraps the body with a bold header`() {
        val ts = 1792979377000L
        val out = ReportHeader.prepend(ts, "hello", TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals("**Study report · 2026-10-26 10:49:37 JST**\n\nhello", out)
    }

    @Test
    fun `prepend trims the body and adds a blank line`() {
        val ts = 1792979377000L
        val out = ReportHeader.prepend(ts, "  spaced  ", TimeZone.getTimeZone("UTC"))
        assertEquals("**Study report · 2026-10-26 01:49:37 UTC**\n\nspaced", out)
    }

    @Test
    fun `prepend does not double the header when already present`() {
        val ts = 1792979377000L
        val header = ReportHeader.line(ts, TimeZone.getTimeZone("Asia/Tokyo"))
        val body = "**$header**\n\nalready headed"
        val out = ReportHeader.prepend(ts, body, TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(body, out)
    }
}
