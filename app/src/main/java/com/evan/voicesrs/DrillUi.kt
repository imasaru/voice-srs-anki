package com.evan.voicesrs

import java.util.concurrent.CopyOnWriteArrayList

/** Snapshot the main screen renders. Written by the drill, read by the activity. */
data class DrillUiState(
    val status: String = "",
    val frontHtml: String = "",
    val backHtml: String = "",
    val showBack: Boolean = false,
    val heard: String = "",
    val ratingMicOpen: Boolean = false,
    val engineLabel: String = ""
)

object DrillUi {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(DrillUiState) -> Unit>()

    @Volatile
    var state: DrillUiState = DrillUiState()
        private set

    fun update(block: (DrillUiState) -> DrillUiState) {
        val next = synchronized(lock) {
            val updated = block(state)
            state = updated
            updated
        }
        for (listener in listeners) {
            try {
                listener(next)
            } catch (_: Exception) {
            }
        }
    }

    fun addListener(listener: (DrillUiState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (DrillUiState) -> Unit) {
        listeners.remove(listener)
    }
}
