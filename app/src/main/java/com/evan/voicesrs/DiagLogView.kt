package com.evan.voicesrs

import java.io.File

/** h89: pure helper for the in-app diagnostic log viewer (no Android). */
object DiagLogView {
    const val LIMIT = 200
    val LEVELS = listOf("TRACE", "DEBUG", "INFO", "WARNING", "ERROR")

    data class Row(val time: String, val level: String, val message: String, val raw: String)

    /** Map DiagLog event token to a LEVELS label. I->INFO, W->WARNING, E->ERROR, D/DEBUG->DEBUG, T/TRACE->TRACE; anything else -> INFO. */
    fun levelOf(event: String): String = when (event.uppercase()) {
        "I", "INFO" -> "INFO"
        "W", "WARNING", "WARN" -> "WARNING"
        "E", "ERROR", "ERR" -> "ERROR"
        "D", "DEBUG" -> "DEBUG"
        "T", "TRACE" -> "TRACE"
        else -> "INFO"
    }

    /**
     * Parse one DiagLog line. Returns null if it has fewer than 3 whitespace-separated fields.
     * time = HH:mm:ss taken from the ISO timestamp's time portion (chars after 'T', length 8).
     * level = levelOf(event).
     * message = everything after the ISO timestamp + first space (i.e. "TAG event k=v...").
     * raw = the original line.
     */
    fun parseLine(line: String): Row? {
        val t = line.trim()
        val sp = t.indexOf(' ')
        if (sp < 0) return null
        val ts = t.substring(0, sp)
        val rest = t.substring(sp + 1)
        val fields = rest.split(Regex("\\s+"))
        if (fields.size < 2) return null
        return Row(ts.substringAfter('T').take(8), levelOf(fields[1]), rest, line)
    }

    /**
     * Read diag-*.log under dir (diag-0 newest … diag-N oldest). Concatenate oldest→newest
     * (diag-(keep-1) … diag-0, skipping missing), split lines, drop blanks, keep the last [limit]
     * lines, parse each (skip nulls). Never throws: missing/unreadable dir → emptyList.
     */
    fun readLast(dir: File, limit: Int = LIMIT, keep: Int = DiagLogFile.KEEP): List<Row> = try {
        val all = ArrayList<String>()
        for (i in keep - 1 downTo 0) {
            val f = File(dir, "diag-$i.log")
            if (f.exists()) all.addAll(f.readText().lineSequence().filter { it.isNotBlank() })
        }
        all.takeLast(limit).mapNotNull { parseLine(it) }
    } catch (t: Throwable) {
        emptyList()
    }

    /** Keep rows whose level is in enabled. Empty enabled → empty list. */
    fun filter(rows: List<Row>, enabled: Set<String>): List<Row> =
        rows.filter { it.level in enabled }

    /** Delete every diag-*.log under dir. Never throws. Returns true if dir existed. */
    fun clear(dir: File): Boolean {
        return try {
            if (!dir.exists()) return false
            dir.listFiles()?.filter { f -> f.name.startsWith("diag-") && f.name.endsWith(".log") }?.forEach { it.delete() }
            true
        } catch (t: Throwable) {
            false
        }
    }
}
