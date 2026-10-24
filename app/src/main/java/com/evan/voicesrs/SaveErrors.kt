package com.evan.voicesrs

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps a failure during a memo save or a "Make a card" note create to a short,
 * user-facing message. Pure logic (java.net only) so it is JVM-unit-testable;
 * the UI layer turns the result into a [com.google.android.material.snackbar.Snackbar].
 */
object SaveErrors {
    enum class Op { MEMO, MAKE_CARD }

    fun message(op: Op, e: Throwable): String {
        val msg = e.message ?: ""
        // Network-level failure (direct or wrapped by AnkiConnect's own exception).
        if (hasCauseType(e) || msg.contains("Could not reach AnkiConnect", ignoreCase = true)) {
            return "AnkiConnect unreachable"
        }
        // Memo saves surface raw IO errors from the AnkiConnect call path.
        if (op == Op.MEMO && e is IOException) {
            return "Couldn't save memo: ${e.message ?: "unknown error"}"
        }
        if (msg.contains("deck", ignoreCase = true) && msg.contains("was not found", ignoreCase = true)) {
            return "Deck not found" + quoted(msg)
        }
        if (msg.contains("model", ignoreCase = true) && msg.contains("was not found", ignoreCase = true)) {
            return "Note type not found"
        }
        if (msg.contains("cannot create note because it is empty", ignoreCase = true)
            || msg.contains("missing field", ignoreCase = true)) {
            return "Missing field"
        }
        if (msg.contains("duplicate", ignoreCase = true)) {
            return "Duplicate card"
        }
        return fallback(msg)
    }

    private fun fallback(msg: String): String =
        if (msg.isNotBlank()) "Couldn't save: $msg" else "Couldn't save"

    private fun quoted(msg: String): String {
        val open = msg.indexOf('\'')
        if (open < 0) return ""
        val close = msg.indexOf('\'', open + 1)
        return if (close > open) ": ${msg.substring(open + 1, close)}" else ""
    }

    private fun hasCauseType(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            when (cur) {
                is ConnectException, is SocketTimeoutException, is UnknownHostException -> return true
            }
            cur = cur.cause
        }
        return false
    }
}
