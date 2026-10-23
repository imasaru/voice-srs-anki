package com.evan.voicesrs

/**
 * Pure decision logic for the sync menu action. Kept out of [MainActivity] so
 * the visibility / invalidation rules can be unit-tested without Android.
 */
object MenuSync {
    /**
     * Whether the sync menu item should be shown. Visible whenever there is
     * work to sync; a sync in flight keeps it visible (disabled, not hidden) so
     * a stuck sync stays reachable. Only the pending count drives visibility.
     */
    fun visible(pending: Int, syncing: Boolean): Boolean = pending > 0

    /**
     * Whether the menu must be rebuilt when the pending-sync count moves from
     * [prev] to [now]. The menu item's visibility depends on whether the count
     * is zero, so any change to the count warrants a rebuild.
     */
    fun shouldInvalidate(prev: Int, now: Int): Boolean = prev != now
}
