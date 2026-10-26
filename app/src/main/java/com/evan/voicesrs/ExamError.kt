package com.evan.voicesrs

/**
 * Pure formatting for a failed exam apply. No I/O, no logging, so the exact
 * copy is covered by unit tests and stays stable for the UI dump.
 */
object ExamError {
    /**
     * User-facing message for an exam apply that could not complete. Leads with
     * a stable prefix; the exception name and (when present) its message follow.
     */
    fun format(e: Exception): String {
        val kind = e.javaClass.simpleName
        val detail = e.message?.trim()?.takeIf { it.isNotEmpty() }
        return if (detail == null) {
            "Exam apply failed ($kind). Try again."
        } else {
            "Exam apply failed ($kind): $detail"
        }
    }
}
