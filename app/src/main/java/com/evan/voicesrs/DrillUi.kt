package com.evan.voicesrs

import java.util.concurrent.CopyOnWriteArrayList

/** Snapshot the main screen renders. Written by the drill, read by the activity. */
data class DrillUiState(
    val status: String = "",
    val frontHtml: String = "",
    val backHtml: String = "",
    val showBack: Boolean = false,
    /** Rating chips are shown only after the answer is revealed; hidden on the question. */
    val ratingVisible: Boolean = false,
    val heard: String = "",
    val ratingMicOpen: Boolean = false,
    val engineLabel: String = "",
    /** "Done 4 · Left 16 · Streak 3 · ~8 min". Blank when idle. */
    val progressLine: String = "",
    val pendingSync: Int = 0,
    val showError: Boolean = false,
    val errorMessage: String = "",
    /** Error card title; blank means the default "Can't start the drill". */
    val errorTitle: String = "",
    /** True while [DrillService] is in a drill session, including after the activity is recreated. */
    val drillRunning: Boolean = false,
    /** Silent flag captured when this session started. Idle UI uses the pref instead. */
    val silentSession: Boolean = false,
    /** Silent question is on screen and the drill is waiting for Show answer. */
    val awaitingShowAnswer: Boolean = false,
    /** Ease (1-4) of the grade chosen for the current card by tap or voice; null clears the chips. */
    val selectedEase: Int? = null
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
