package com.evan.voicesrs

/** Retry rules for general asks. Pure. */
object AiRetry {
    const val AUTO_MAX = 1

    /** Auto-retry only a dropped connection, only once, never after cancel. */
    fun autoRetry(fail: ChatSend.Fail, autoRetriesDone: Int, cancelled: Boolean): Boolean =
        fail == ChatSend.Fail.DROPPED && autoRetriesDone < AUTO_MAX && !cancelled

    /** Error bubble text: partial streamed answer kept above the error line. */
    fun keptText(partial: String, message: String): String =
        if (partial.isBlank()) message else partial.trimEnd() + "\n\n" + message

    /** A retry may run only for an Error row whose request is still stored. */
    fun canManualRetry(state: ExplState, hasRequest: Boolean): Boolean =
        state is ExplState.Error && state.retryable && hasRequest
}
