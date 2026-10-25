package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamPlanTest {

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun `daysLeft same day is zero, future positive, past negative`() {
        assertEquals(0, ExamPlan.daysLeft(today, today))
        assertEquals(12, ExamPlan.daysLeft(today, LocalDate.of(2026, 11, 7)))
        assertEquals(-3, ExamPlan.daysLeft(today, LocalDate.of(2026, 10, 23)))
    }

    @Test
    fun `targetRetention ramps from base to peak`() {
        assertEquals(0.90, ExamPlan.targetRetention(30), 1e-9)
        assertEquals(0.90, ExamPlan.targetRetention(21), 1e-9)
        assertEquals(0.95, ExamPlan.targetRetention(0), 1e-9)
        assertEquals(0.93, ExamPlan.targetRetention(7), 1e-9)
        assertEquals(0.90, ExamPlan.targetRetention(-1), 1e-9)
    }

    @Test
    fun `targetRetention accepts custom base and peak`() {
        assertEquals(0.97, ExamPlan.targetRetention(0, base = 0.85, peak = 0.97), 1e-9)
        assertEquals(0.85, ExamPlan.targetRetention(21, base = 0.85, peak = 0.97), 1e-9)
    }

    @Test
    fun `targetRetention rejects bad arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamPlan.targetRetention(5, base = 0.95, peak = 0.90)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExamPlan.targetRetention(5, rampDays = 0)
        }
    }

    @Test
    fun `inSweepWindow is inclusive of the window edge`() {
        assertTrue(ExamPlan.inSweepWindow(7))
        assertFalse(ExamPlan.inSweepWindow(8))
        assertTrue(ExamPlan.inSweepWindow(0))
        assertFalse(ExamPlan.inSweepWindow(-1))
    }

    @Test
    fun `sweepRange maps days left to setDueDate syntax`() {
        assertNull(ExamPlan.sweepRange(-1))
        assertEquals("0", ExamPlan.sweepRange(0))
        assertEquals("0", ExamPlan.sweepRange(1))
        assertEquals("0-1", ExamPlan.sweepRange(2))
        assertEquals("0-11", ExamPlan.sweepRange(12))
    }

    @Test
    fun `cardsToSweep keeps order and selects new un-dated or future cards`() {
        val exam = LocalDate.of(2026, 11, 7)
        val cards = listOf(
            ExamPlan.CardDue(10L, LocalDate.of(2026, 11, 1), isNew = false), // before exam, not new -> excluded
            ExamPlan.CardDue(11L, LocalDate.of(2026, 11, 7), isNew = false), // on exam day -> included
            ExamPlan.CardDue(12L, LocalDate.of(2026, 11, 20), isNew = false), // after exam -> included
            ExamPlan.CardDue(13L, null, isNew = false),                       // null due -> included
            ExamPlan.CardDue(14L, LocalDate.of(2026, 10, 1), isNew = true),   // new -> included
            ExamPlan.CardDue(15L, LocalDate.of(2026, 11, 1), isNew = false)   // before exam, not new -> excluded
        )
        assertEquals(
            listOf(11L, 12L, 13L, 14L),
            ExamPlan.cardsToSweep(cards, exam)
        )
    }

    @Test
    fun `coverage counts cards already scheduled before the exam`() {
        val exam = LocalDate.of(2026, 11, 7)
        val cards = listOf(
            ExamPlan.CardDue(1L, LocalDate.of(2026, 11, 1), isNew = false), // covered
            ExamPlan.CardDue(2L, LocalDate.of(2026, 11, 1), isNew = false), // covered
            ExamPlan.CardDue(3L, LocalDate.of(2026, 11, 1), isNew = false), // covered
            ExamPlan.CardDue(4L, null, isNew = false),                      // null -> not covered
            ExamPlan.CardDue(5L, LocalDate.of(2026, 11, 30), isNew = false) // after exam -> not covered
        )
        val c = ExamPlan.coverage(cards, exam)
        assertEquals(5, c.total)
        assertEquals(3, c.covered)
        assertEquals(60, c.percent)

        assertEquals(100, ExamPlan.coverage(emptyList(), exam).percent)

        val twoOfThree = listOf(
            ExamPlan.CardDue(1L, LocalDate.of(2026, 11, 1), isNew = false),
            ExamPlan.CardDue(2L, LocalDate.of(2026, 11, 1), isNew = false),
            ExamPlan.CardDue(3L, LocalDate.of(2026, 11, 1), isNew = true)
        )
        assertEquals(66, ExamPlan.coverage(twoOfThree, exam).percent)
    }

    @Test
    fun `spokenSummary handles plural singular today and past`() {
        assertEquals("Driving test in 12 days, 84% covered.", ExamPlan.spokenSummary("Driving test", 12, 84))
        assertEquals("Driving test in 1 day, 84% covered.", ExamPlan.spokenSummary("Driving test", 1, 84))
        assertEquals("Driving test is today, 84% covered.", ExamPlan.spokenSummary("Driving test", 0, 84))
        assertEquals("", ExamPlan.spokenSummary("Driving test", -2, 84))
    }
}
