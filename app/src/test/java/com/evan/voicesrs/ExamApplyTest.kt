package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamApplyTest {

    private val today = LocalDate.of(2026, 10, 26)
    private val exam = LocalDate.of(2026, 11, 7)
    private val deck = "Licenses::Driving"

    private fun input(
        examDate: LocalDate? = exam,
        target: Double? = null,
        cloneId: Long? = null,
        lastApplied: LocalDate? = null,
        configId: Long? = 1655L,
        coverage: Int = 84
    ) = ExamApply.Input(
        deck = deck,
        today = today,
        examDate = examDate,
        targetOverride = target,
        cloneId = cloneId,
        lastApplied = lastApplied,
        configId = configId,
        coveragePercent = coverage
    )

    @Test
    fun `no exam date disables the plan`() {
        val plan = ExamApply.plan(input(examDate = null))
        assertFalse(plan.enabled)
        assertTrue(plan.skipWrites)
        assertEquals("", plan.spoken)
        assertFalse(plan.clone)
        assertFalse(plan.save)
        assertFalse(plan.sweep)
    }

    @Test
    fun `a past exam disables the plan`() {
        val plan = ExamApply.plan(input(examDate = today.minusDays(2)))
        assertFalse(plan.enabled)
        assertTrue(plan.skipWrites)
        assertEquals("", plan.spoken)
        assertEquals(-2, plan.daysLeft)
    }

    @Test
    fun `already applied today skips writes and still speaks`() {
        val plan = ExamApply.plan(input(cloneId = 99L, lastApplied = today, configId = 99L))
        assertTrue(plan.enabled)
        assertTrue(plan.skipWrites)
        assertFalse(plan.clone)
        assertFalse(plan.save)
        assertFalse(plan.sweep)
        assertTrue(plan.spoken.contains("Driving in 12 days"))
        assertTrue(plan.spoken.contains("84% covered"))
        assertTrue(plan.spoken.contains("92 percent"))
    }

    @Test
    fun `first run clones assigns and saves without a sweep`() {
        val plan = ExamApply.plan(input())
        assertTrue(plan.enabled)
        assertFalse(plan.skipWrites)
        assertTrue(plan.clone)
        assertEquals("VoiceSRS exam Licenses::Driving", plan.cloneName)
        assertEquals(1655L, plan.cloneFrom)
        assertTrue(plan.assign)
        assertNull(plan.assignId)
        assertTrue(plan.save)
        assertEquals(0.92, plan.retention!!, 1e-9)
        assertFalse(plan.sweep)
        assertNull(plan.sweepRange)
        assertEquals(12, plan.daysLeft)
    }

    @Test
    fun `later run reuses the clone and does not re-clone`() {
        val plan = ExamApply.plan(input(cloneId = 77L, configId = 77L))
        assertFalse(plan.skipWrites)
        assertFalse(plan.clone)
        assertNull(plan.cloneName)
        assertFalse(plan.assign)
        assertEquals(77L, plan.assignId)
        assertTrue(plan.save)
    }

    @Test
    fun `a reused clone is reassigned when the deck is on another preset`() {
        val plan = ExamApply.plan(input(cloneId = 77L, configId = 1655L))
        assertFalse(plan.clone)
        assertTrue(plan.assign)
        assertEquals(77L, plan.assignId)
        assertTrue(plan.save)
    }

    @Test
    fun `clone is skipped when there is no config id to copy`() {
        val plan = ExamApply.plan(input(cloneId = null, configId = null))
        assertTrue(plan.enabled)
        assertTrue(plan.skipWrites)
        assertFalse(plan.clone)
        assertFalse(plan.save)
    }

    @Test
    fun `sweep is on inside the last 7 days`() {
        val soon = ExamApply.plan(input(examDate = today.plusDays(7)))
        assertTrue(soon.sweep)
        assertEquals("0-6", soon.sweepRange)
        assertEquals(0.93, soon.retention!!, 1e-9)
        val far = ExamApply.plan(input(examDate = today.plusDays(8)))
        assertFalse(far.sweep)
        assertNull(far.sweepRange)
    }

    @Test
    fun `per-deck target overrides the ramp`() {
        val plan = ExamApply.plan(input(target = 0.97, examDate = today.plusDays(30)))
        assertEquals(0.97, plan.retention!!, 1e-9)
        assertTrue(plan.spoken.contains("97 percent"))
    }

    @Test
    fun `countdown line has days retention and coverage`() {
        assertEquals(
            "Exam in 12 days · 90% retention · 84% covered",
            ExamApply.countdownLine(12, 0.90, 84)
        )
        assertEquals(
            "Exam in 1 day · 95% retention · 100% covered",
            ExamApply.countdownLine(1, 0.95, 100)
        )
        assertEquals(
            "Exam today · 95% retention · 50% covered",
            ExamApply.countdownLine(0, 0.95, 50)
        )
        assertEquals("", ExamApply.countdownLine(-1, 0.90, 10))
    }

    @Test
    fun `findCards queries keep the deck quoted`() {
        assertEquals("deck:\"Licenses::Driving\"", ExamApply.allQuery(deck))
        assertEquals(
            "deck:\"Licenses::Driving\" -is:new prop:due<12",
            ExamApply.coveredQuery(deck, 12)
        )
        assertEquals(
            "deck:\"Licenses::Driving\" (is:new OR prop:due>=7)",
            ExamApply.sweepQuery(deck, 7)
        )
    }

    @Test
    fun `exam prefs round-trip through a fake map`() {
        val map = LinkedHashMap<String, Any?>()
        val saved = ExamStore.State(
            date = exam,
            target = 0.93,
            cloneId = 44L,
            lastApplied = today,
            coverage = 60
        )
        ExamStore.write(map, deck, saved)
        assertEquals("2026-11-07", map[ExamStore.dateKey(deck)])
        assertEquals("0.93", map[ExamStore.targetKey(deck)])
        assertEquals("44", map[ExamStore.cloneKey(deck)])
        assertEquals("2026-10-26", map[ExamStore.appliedKey(deck)])
        assertEquals("60", map[ExamStore.coverageKey(deck)])
        assertEquals(saved, ExamStore.read(map, deck))
        val other = ExamStore.read(map, "Other")
        assertNull(other.date)
        assertNull(other.cloneId)
    }

    @Test
    fun `clearing the exam keeps the clone id`() {
        val map = LinkedHashMap<String, Any?>()
        ExamStore.write(
            map,
            deck,
            ExamStore.State(date = exam, target = 0.91, cloneId = 9L, lastApplied = today, coverage = 10)
        )
        ExamStore.write(map, deck, ExamStore.disable(ExamStore.read(map, deck)))
        val cleared = ExamStore.read(map, deck)
        assertNull(cleared.date)
        assertNull(cleared.target)
        assertNull(cleared.lastApplied)
        assertNull(cleared.coverage)
        assertEquals(9L, cleared.cloneId)
        assertFalse(map.containsKey(ExamStore.dateKey(deck)))
        assertEquals("9", map[ExamStore.cloneKey(deck)])
    }

    @Test
    fun `target parser accepts a percent or a fraction`() {
        assertEquals(0.90, ExamStore.parseTarget("0.90")!!, 1e-9)
        assertEquals(0.90, ExamStore.parseTarget("90")!!, 1e-9)
        assertEquals(0.95, ExamStore.parseTarget("95")!!, 1e-9)
        assertNull(ExamStore.parseTarget(""))
        assertNull(ExamStore.parseTarget("0.5"))
        assertNull(ExamStore.parseTarget("abc"))
    }
}
