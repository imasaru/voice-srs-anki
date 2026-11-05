package com.evan.voicesrs

import java.io.File
import java.util.concurrent.Executors

/** h89: rolling diagnostic log files diag-0.log (newest) .. diag-(keep-1).log in dir. Never throws to the caller. */
class DiagLogFile(private val dir: File, private val capBytes: Long = CAP_BYTES, private val keep: Int = KEEP) {
    companion object {
        const val CAP_BYTES = 1_000_000L
        const val KEEP = 5
    }
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "diaglog").apply { isDaemon = true } }

    /** Count of writes that failed (disk error, bad dir). */
    @Volatile var failures = 0
        private set

    fun file(i: Int): File = File(dir, "diag-$i.log")

    /** Queue one line for the background writer thread. Never throws. */
    fun append(line: String) {
        try { exec.execute { appendNow(line) } } catch (t: Throwable) { failures++ }
    }

    /** Write one line now (line + "\n", UTF-8). If diag-0 exists and would pass capBytes, rotate first. Never throws: on any Throwable, failures++. */
    @Synchronized fun appendNow(line: String) {
        try {
            dir.mkdirs()
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            val cur = file(0)
            if (cur.exists() && cur.length() + bytes.size > capBytes) rotate()
            cur.appendBytes(bytes)
        } catch (t: Throwable) { failures++ }
    }

    /** Delete file(keep - 1), then rename file(i) -> file(i + 1) for i = keep - 2 down to 0 (only files that exist). */
    private fun rotate() {
        file(keep - 1).delete()
        for (i in keep - 2 downTo 0) {
            val src = file(i)
            if (src.exists()) src.renameTo(file(i + 1))
        }
    }
}
