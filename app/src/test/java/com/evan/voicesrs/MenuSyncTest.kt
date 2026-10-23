package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuSyncTest {
    @Test
    fun `visible only when there is pending work`() {
        assertFalse(MenuSync.visible(0, syncing = false))
        assertFalse(MenuSync.visible(0, syncing = true))
        assertTrue(MenuSync.visible(1, syncing = false))
        assertTrue(MenuSync.visible(5, syncing = false))
    }

    @Test
    fun `visible while syncing too, just disabled`() {
        // The menu stays visible during a sync (isEnabled = !syncing), so a
        // stuck sync stays reachable. Only pending work drives visibility.
        assertTrue(MenuSync.visible(3, syncing = true))
    }

    @Test
    fun `invalidate when pending increases across zero`() {
        assertTrue(MenuSync.shouldInvalidate(0, 1))
    }

    @Test
    fun `invalidate when pending drops back to zero`() {
        assertTrue(MenuSync.shouldInvalidate(2, 0))
    }

    @Test
    fun `invalidate on any pending change`() {
        assertTrue(MenuSync.shouldInvalidate(4, 5))
        assertTrue(MenuSync.shouldInvalidate(1, 3))
    }

    @Test
    fun `no invalidate when pending is unchanged`() {
        assertFalse(MenuSync.shouldInvalidate(0, 0))
        assertFalse(MenuSync.shouldInvalidate(3, 3))
    }
}
