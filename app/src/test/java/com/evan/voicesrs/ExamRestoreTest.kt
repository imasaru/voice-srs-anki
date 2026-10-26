package com.evan.voicesrs

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
}
