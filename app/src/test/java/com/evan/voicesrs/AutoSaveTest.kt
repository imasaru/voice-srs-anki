package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSaveTest {

    @Test
    fun `unchanged value does not save`() {
        val same = AutoSave.decide(lastGood = "http://anki", parsed = "http://anki")
        assertFalse(same.save)
        assertFalse(same.invalid)
        assertEquals("http://anki", same.value)

        val debounce = AutoSave.Debounce(800)
        assertNull(AutoSave.schedule(debounce, 1_000, save = same.save))
        assertNull(debounce.deadline())
    }

    @Test
    fun `invalid input does not save and keeps the last good value`() {
        val rejected = AutoSave.decide(lastGood = 20, parsed = null)
        assertFalse(rejected.save)
        assertTrue(rejected.invalid)
        assertEquals(20, rejected.value)

        val debounce = AutoSave.Debounce(AutoSave.DEBOUNCE_MS)
        AutoSave.schedule(debounce, 1_000, save = true)
        assertNull(AutoSave.schedule(debounce, 1_200, save = rejected.save))
        assertEquals(1_800L, debounce.deadline())
    }

    @Test
    fun `a changed valid value saves`() {
        val next = AutoSave.decide(lastGood = 20, parsed = 40)
        assertTrue(next.save)
        assertFalse(next.invalid)
        assertEquals(40, next.value)
        assertEquals(1_800L, AutoSave.schedule(AutoSave.Debounce(800), 1_000, save = next.save))
    }

    @Test
    fun `saves inside the window coalesce to one notice`() {
        val debounce = AutoSave.Debounce(windowMs = 800)
        val first = debounce.onSaved(1_000)
        val second = debounce.onSaved(1_400)
        val third = debounce.onSaved(1_900)
        assertEquals(1_800L, first)
        assertNull(second)
        assertNull(third)
        assertEquals(2_700L, debounce.deadline())
        assertFalse(debounce.take(2_699))
        assertTrue(debounce.take(2_700))
        assertFalse(debounce.take(2_700))
        assertNull(debounce.deadline())
    }

    @Test
    fun `a save after the notice starts a new one`() {
        val debounce = AutoSave.Debounce(800)
        assertEquals(1_800L, debounce.onSaved(1_000))
        assertTrue(debounce.take(1_800))
        assertEquals(2_700L, debounce.onSaved(1_900))
        assertEquals(2_700L, debounce.deadline())
    }

    @Test
    fun `schedule of two saves is one later deadline`() {
        val debounce = AutoSave.Debounce(AutoSave.DEBOUNCE_MS)
        val first = AutoSave.schedule(debounce, 5_000, save = true)
        val second = AutoSave.schedule(debounce, 5_400, save = true)
        assertEquals(5_800L, first)
        assertEquals(6_200L, second)
        assertEquals(second, debounce.deadline())
        assertTrue(debounce.take(6_200))
        assertNull(debounce.deadline())
    }
}
