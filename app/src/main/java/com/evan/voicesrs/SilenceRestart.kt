package com.evan.voicesrs

/** Silence-restart timing and the busy retry after a restart. Pure; DrillService supplies the clock and the delay. */
object SilenceRestart {
    const val ERROR_RECOGNIZER_BUSY = 8
    const val ERROR_SERVER_DISCONNECTED = 11
    const val RESTART_DELAY_MS = 300L
    const val RETRY_BACKOFF_MS = 700L
    const val MAX_BUSY_RETRIES = 1

    sealed class Plan {
        data class Retry(val delayMs: Long) : Plan()
        /** End this listen like a silent window: no mic fault, no warning. */
        object EndQuiet : Plan()
        /** Not a restart-busy case; the caller keeps today's handling. */
        object NotMine : Plan()
    }

    fun isBusy(error: Int): Boolean = error == ERROR_SERVER_DISCONNECTED || error == ERROR_RECOGNIZER_BUSY

    fun onError(error: Int, fromRestart: Boolean, busyRetries: Int, nowMs: Long, deadlineMs: Long): Plan {
        if (!fromRestart || !isBusy(error)) return Plan.NotMine
        val left = deadlineMs - nowMs - RETRY_BACKOFF_MS
        if (busyRetries < MAX_BUSY_RETRIES && left >= ListenWindow.MIN_REMAINING_MS) return Plan.Retry(RETRY_BACKOFF_MS)
        return Plan.EndQuiet
    }

    /** destroy now; begin after [delayMs] via [postDelayed], only if [stillOpen]. */
    fun schedule(delayMs: Long, destroy: () -> Unit, postDelayed: (Long, () -> Unit) -> Unit, stillOpen: () -> Boolean, begin: () -> Unit) {
        destroy()
        postDelayed(delayMs) { if (stillOpen()) begin() }
    }
}
