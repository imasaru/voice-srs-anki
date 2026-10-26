package com.evan.voicesrs

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the header line prepended to the "today's study report" reply so the
 * reader knows when it was generated. Pure formatting — no I/O, unit-tested.
 */
object ReportHeader {
    private const val FORMAT = "yyyy-MM-dd HH:mm:ss z"

    /** "Study report · 2026-10-26 10:49:37 JST" for the given instant and zone. */
    fun line(issuedAtMs: Long, tz: TimeZone = TimeZone.getDefault()): String =
        "Study report · " + SimpleDateFormat(FORMAT, Locale.US).apply { this.timeZone = tz }.format(issuedAtMs)

    /**
     * Returns [body] with a bold header line on top. If the body already starts
     * with that exact bold line, it is returned unchanged (no double header).
     */
    fun prepend(issuedAtMs: Long, body: String, tz: TimeZone = TimeZone.getDefault()): String {
        val header = "**" + line(issuedAtMs, tz) + "**\n\n"
        val trimmed = body.trim()
        return if (trimmed.startsWith(header)) trimmed else header + trimmed
    }
}
