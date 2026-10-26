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

    /**
     * What a caller should do after `setDeckConfigId` reports whether the
     * reassignment to the original preset succeeded. Pure: it performs no AnkiConnect
     * I/O. See NOTES.md ("Follow-up") for why the orphaned clone is kept rather
     * than removed.
     *
     * - clearPrefs: clear the saved `origCfg` pref. Only true on success, so a
     *   failed reassignment keeps the pref and the next run retries.
     * - reportError: true when the reassignment did not succeed; the caller
     *   surfaces it through the existing `Log.w` failure path.
     * - deleteClone: remove the orphaned clone preset. Always false here — we keep
     *   the clone (see [newState]) so a later exam reuses it instead of cloning anew.
     */
    data class Decision(
        val clearPrefs: Boolean,
        val reportError: Boolean,
        val deleteClone: Boolean
    )

    /** Decide the follow-up from the `setDeckConfigId` Boolean result. */
    fun decide(setOk: Boolean): Decision =
        if (setOk) {
            Decision(clearPrefs = true, reportError = false, deleteClone = false)
        } else {
            Decision(clearPrefs = false, reportError = true, deleteClone = false)
        }

    /**
     * The saved state to persist after a restore attempt. Clears `origCfg` when
     * [Decision.clearPrefs] (reassignment succeeded), and drops `cloneId` only when
     * [Decision.deleteClone] — which we never do, so the clone id is retained for a
     * later exam to reuse.
     */
    fun newState(state: ExamStore.State, d: Decision): ExamStore.State =
        state.copy(
            origCfg = if (d.clearPrefs) null else state.origCfg,
            cloneId = if (d.deleteClone) null else state.cloneId
        )
}
