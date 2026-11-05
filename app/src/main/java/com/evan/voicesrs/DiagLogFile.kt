package com.evan.voicesrs

import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** h89: rolling diagnostic log files diag-0.log (newest) .. diag-(keep-1).log in dir. Never throws to the caller. */
class DiagLogFile(private val dir: File, private val capBytes: Long = CAP_BYTES, private val keep: Int = KEEP, private val maxQueue: Int = MAX_QUEUE) {
    companion object {
        const val CAP_BYTES = 1_000_000L
        const val KEEP = 5
        const val MAX_QUEUE = 2000
    }
    private val exec = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(maxQueue)) { r -> Thread(r, "diaglog").apply { isDaemon = true } }

    /** Count of writes that failed (disk error, bad dir). */
    @Volatile var failures = 0
        private set

    /** Count of lines dropped because the writer queue was full. */
    @Volatile var dropped = 0
        private set

    fun file(i: Int): File = File(dir, "diag-$i.log")

    /** Queue one line for the background writer thread. Never throws. */
    fun append(line: String) {
        try { exec.execute { appendNow(line) } } catch (e: RejectedExecutionException) { dropped++ } catch (t: Throwable) { failures++ }
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
