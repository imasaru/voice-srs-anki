package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamRevertRunTest {
    private fun rel(r: Int): Int? = ExamRevert.searchRelDue { m -> r >= m }

    @Test fun `a overdue probe gives a negative relative due`() { assertEquals(-12, rel(-12)) }
    @Test fun `b future probe`() { assertEquals(40, rel(40)) }
    @Test fun `c due today`() { assertEquals(0, rel(0)) }
    @Test fun `d no due gives null`() { assertNull(ExamRevert.searchRelDue { false }) }

    @Test fun `e failed steps are counted and not moved`() {
        val steps = listOf(
            ExamRevert.Step.AssignPreset(1L),
            ExamRevert.Step.SaveConfig("{}"),
            ExamRevert.Step.SetDue(listOf(1L, 2L), "3"),
            ExamRevert.Step.SetDue(listOf(3L), "5")
        )
        val out = ExamRevert.runSteps(steps) { s -> !(s is ExamRevert.Step.SetDue && s.days == "5") }
        assertEquals(ExamRevert.Outcome(moved = 2, failed = 1), out)
    }

    @Test fun `f all ok and AssignPreset not run`() {
        val ran = ArrayList<ExamRevert.Step>()
        val steps = listOf(ExamRevert.Step.AssignPreset(1L), ExamRevert.Step.SetDue(listOf(9L), "0"))
        val out = ExamRevert.runSteps(steps) { s -> ran += s; true }
        assertEquals(ExamRevert.Outcome(moved = 1, failed = 0), out)
        assertEquals(listOf<ExamRevert.Step>(ExamRevert.Step.SetDue(listOf(9L), "0")), ran)
    }
}
