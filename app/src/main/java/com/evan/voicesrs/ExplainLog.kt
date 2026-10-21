package com.evan.voicesrs

import android.util.Log

/** Logcat lines the emulator QA asserts on. Tag is `Explain`. No secrets. */
object ExplainLog {
    const val TAG = "Explain"

    fun enqueue(kind: ExplainKind, noteId: Long) {
        Log.i(TAG, "ai enqueue kind=${kind.name} note=$noteId")
    }

    fun ready(turnId: Long, cached: Boolean) {
        Log.i(TAG, "ai explain ready turn=$turnId cached=$cached")
    }

    fun badgeCleared(turnId: Long) {
        Log.i(TAG, "ai badge cleared turn=$turnId")
    }
}
