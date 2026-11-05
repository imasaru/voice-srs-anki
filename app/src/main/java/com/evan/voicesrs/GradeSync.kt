package com.evan.voicesrs

import android.util.Log
import java.io.File

/** Pushes queued offline grades with backoff. Returns how many are still pending. */
object GradeSync {
    private const val TAG = "GradeSync"

    fun pendingCount(dir: File): Int = LocalQueueStore(dir).readGrades().size

    fun sync(dir: File, anki: AnkiConnect, attempts: Int = 4): Int {
        val store = LocalQueueStore(dir)
        var left = store.readGrades()
        if (left.isEmpty()) return 0
        var attempt = 0
        while (left.isNotEmpty() && attempt < attempts) {
            if (attempt > 0) {
                try {
                    Thread.sleep(SyncBackoff.delayMs(attempt - 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            try {
                val ok = anki.answerCards(left)
                val remain = PendingSync.stillPending(left, ok)
                if (remain.size == left.size) {
                    attempt++
                    continue
                }
                left = remain
                store.writeGrades(left)
                if (left.isEmpty()) return 0
            } catch (e: Exception) {
                DLog.w(TAG, "sync attempt failed: ${e.javaClass.simpleName}")
                attempt++
            }
        }
        store.writeGrades(left)
        return left.size
    }
}
