package com.evan.voicesrs

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** h89: one diagnostic log line "<ISO time> <TAG> <event> k=v ...". Pure, no Android. */
object DiagLog {
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")
    const val NONE = "none"
    private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    fun line(epochMs: Long, tag: String, event: String, kv: List<Pair<String, Any?>> = emptyList(), zone: ZoneId = JST): String {
        val sb = StringBuilder()
        sb.append(TS.format(Instant.ofEpochMilli(epochMs).atZone(zone)))
        sb.append(' ').append(tag).append(' ').append(event)
        for ((k, v) in kv) {
            sb.append(' ').append(k).append('=').append(value(v))
        }
        return sb.toString()
    }

    /** null -> NONE. Escape \ as \\, newline as \n, carriage return as \r, " as \". Wrap in double quotes if the escaped text is empty or contains a space or a double quote. */
    fun value(v: Any?): String {
        if (v == null) return NONE
        val esc = v.toString()
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\"", "\\\"")
        return if (esc.isEmpty() || esc.contains(' ') || esc.contains('"')) "\"$esc\"" else esc
    }
}
