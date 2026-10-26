package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamRestoreTest {

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun `no saved original config never restores`() {
        val state = ExamStore.State(date = LocalDate.of(2026, 11, 7), origCfg = null, cloneId = 42L)
        assertFalse(ExamRestore.needsRestore(state, today))
    }

    @Test
    fun `future exam date does not restore`() {
        val state = ExamStore.State(
            date = LocalDate.of(2026, 11, 7),
            origCfg = 100L,
            cloneId = 200L
        )
        assertFalse(ExamRestore.needsRestore(state, today))
    }

    @Test
    fun `exam day itself does not restore`() {
        val state = ExamStore.State(date = today, origCfg = 100L, cloneId = 200L)
        assertFalse(ExamRestore.needsRestore(state, today))
    }

    @Test
    fun `past exam date restores`() {
        val state = ExamStore.State(
            date = LocalDate.of(2026, 10, 1),
            origCfg = 100L,
            cloneId = 200L
        )
        assertTrue(ExamRestore.needsRestore(state, today))
    }

    @Test
    fun `cleared exam (null date) restores`() {
        val state = ExamStore.State(
            date = null,
            origCfg = 100L,
            cloneId = 200L
        )
        assertTrue(ExamRestore.needsRestore(state, today))
    }

    @Test
    fun `cleared exam without a saved original config does not restore`() {
        val state = ExamStore.State(date = null, origCfg = null, cloneId = 200L)
        assertFalse(ExamRestore.needsRestore(state, today))
    }

    // decide() / newState() — the success and failure follow-up paths.

    @Test
    fun `success clears origCfg, keeps cloneId, no error`() {
        val d = ExamRestore.decide(setOk = true)
        assertTrue(d.clearPrefs)
        assertFalse(d.reportError)
        assertFalse(d.deleteClone)

        val state = ExamStore.State(date = null, origCfg = 100L, cloneId = 200L)
        val next = ExamRestore.newState(state, d)
        assertEquals(null, next.origCfg)
        assertEquals(200L, next.cloneId)
    }

    @Test
    fun `failure keeps origCfg and cloneId and reports error`() {
        val d = ExamRestore.decide(setOk = false)
        assertFalse(d.clearPrefs)
        assertTrue(d.reportError)
        assertFalse(d.deleteClone)

        val state = ExamStore.State(date = null, origCfg = 100L, cloneId = 200L)
        val next = ExamRestore.newState(state, d)
        // pref kept so the next run retries; clone id retained for reuse
        assertEquals(100L, next.origCfg)
        assertEquals(200L, next.cloneId)
    }
}
