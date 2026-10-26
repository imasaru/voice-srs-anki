package com.evan.voicesrs

import java.time.LocalDate

/**
 * Decides when a cloned exam preset should be reverted to the deck's original
 * config. The decision is a pure function of the saved [ExamStore.State] and
 * today's date so it can be unit tested; the AnkiConnect I/O that performs the
 * reassignment lives in [ExamSync] and [SettingsActivity].
 */
object ExamRestore {
    /**
     * True when the deck has a saved original preset ([ExamStore.State.origCfg])
     * and the exam is no longer active for [today] — either cleared (date == null)
     * or the exam date is in the past. Callers must then reassign the original
     * preset to the deck via `setDeckConfigId` and drop the saved config prefs.
     */
    fun needsRestore(state: ExamStore.State, today: LocalDate): Boolean =
        state.origCfg != null &&
            (state.date == null || state.date.isBefore(today))
}
