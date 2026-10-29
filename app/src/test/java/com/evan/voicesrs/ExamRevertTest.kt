package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamRevertTest {
    private val cfg = """{"id":100,"name":"Default","new":{"perDay":20},"mod":1,"usn":-1}"""

    @Test fun presetOnlyWhenConfigUnchanged() {
        val s = ExamStore.State(origCfg = 100L, cloneId = 200L, snapConfig = cfg)
        val live = """{"usn":5,"mod":9,"new":{"perDay":20},"name":"Default","id":100}"""
        assertEquals(listOf(ExamRevert.Step.AssignPreset(100L)), ExamRevert.plan(s, live, 1000))
    }

    @Test fun configRestoredWhenChanged() {
        val s = ExamStore.State(origCfg = 100L, snapConfig = cfg)
        val live = """{"id":100,"name":"Default","new":{"perDay":50}}"""
        assertEquals(
            listOf(ExamRevert.Step.AssignPreset(100L), ExamRevert.Step.SaveConfig(cfg)),
            ExamRevert.plan(s, live, 1000)
        )
    }

    @Test fun configRestoredWhenLiveUnknown() {
        val s = ExamStore.State(origCfg = 100L, snapConfig = cfg)
        assertTrue(ExamRevert.plan(s, null, 1000).contains(ExamRevert.Step.SaveConfig(cfg)))
    }

    @Test fun sweptCardsRestoredGroupedByDays() {
        val s = ExamStore.State(origCfg = 100L, snapConfig = cfg, snapDue = mapOf(1L to 1030, 2L to 1012, 3L to 1030), snapToday = 1000)
        val steps = ExamRevert.plan(s, cfg, 1000)
        assertEquals(
            listOf(
                ExamRevert.Step.AssignPreset(100L),
                ExamRevert.Step.SetDue(listOf(2L), "12"),
                ExamRevert.Step.SetDue(listOf(1L, 3L), "30")
            ),
            steps
        )
    }

    @Test fun revertThreeDaysAfterSnapshot() {
        val s = ExamStore.State(origCfg = 100L, snapConfig = cfg, snapDue = mapOf(1L to 1030, 2L to 1002), snapToday = 1000)
        assertEquals(
            listOf(
                ExamRevert.Step.AssignPreset(100L),
                ExamRevert.Step.SetDue(listOf(2L), "0"),
                ExamRevert.Step.SetDue(listOf(1L), "27")
            ),
            ExamRevert.plan(s, cfg, 1003)
        )
    }

    @Test fun unknownTodayFallsBackToSnapToday() {
        val s = ExamStore.State(origCfg = 100L, snapConfig = cfg, snapDue = mapOf(1L to 1010), snapToday = 1000)
        assertTrue(ExamRevert.plan(s, cfg, null).contains(ExamRevert.Step.SetDue(listOf(1L), "10")))
    }

    @Test fun emptySnapshotGivesNoSteps() {
        assertEquals(emptyList<ExamRevert.Step>(), ExamRevert.plan(ExamStore.State(), cfg, 1000))
        assertEquals(emptyList<ExamRevert.Step>(), ExamRevert.plan(ExamStore.State(origCfg = 100L), cfg, 1000))
    }

    @Test fun todayAbsFromProbe() {
        assertEquals(1000, ExamRevert.todayAbs(1010, 10))
    }

    @Test fun storeRoundTripKeepsSnapshot() {
        val s = ExamStore.State(origCfg = 100L, cloneId = 200L, snapConfig = cfg, snapDue = mapOf(5L to 1009, 6L to 1021), snapToday = 1000)
        val m = HashMap<String, Any?>()
        ExamStore.write(m, "D", s)
        val back = ExamStore.read(m, "D")
        assertEquals(s.snapConfig, back.snapConfig)
        assertEquals(s.snapDue, back.snapDue)
        assertEquals(1000, back.snapToday)
        assertTrue(ExamStore.hasSnapshot(back))
        val cleared = ExamStore.afterRevert(back)
        assertFalse(ExamStore.hasSnapshot(cleared))
        assertEquals(200L, cleared.cloneId)
        assertEquals(null, cleared.date)
    }

    @Test fun cloneRefusedSurfacesViaExamError() {
        val msg = ExamError.format(AnkiConnectException(ExamError.CLONE_REFUSED))
        assertEquals("Exam apply failed (AnkiConnectException): Anki refused to copy the deck preset", msg)
    }
}
