package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DailyReportWiringTest {
    private val tz = TimeZone.getTimeZone("Asia/Tokyo")
    private fun res(p: String) = javaClass.classLoader!!.getResource(p)!!.readText()

    @Test
    fun renderAddsIssueTimeAndSectionBeforeFocus() {
        val r = DailyReportBuilder.build("2000-01-01", DailyReportBuilder.parseDump(res("daily/fixture-daily-synthetic.txt")))
        val out = DailyReportBuilder.render(r, "10:49 JST", "## Where understanding broke\n- x\n")
        assertTrue(out.startsWith("# Daily study report — 2000-01-01 · issued 10:49 JST\n"))
        assertTrue(out.indexOf("## Where understanding broke") in out.indexOf("## Missed cards by concept")..out.indexOf("## Focus next"))
        assertEquals(DailyReportBuilder.render(r).lines().drop(1), DailyReportBuilder.render(r, null).lines().drop(1))
    }

    @Test
    fun renderListsStudyBlocksAfterTimeSpan() {
        val r = DailyReportBuilder.build("2000-01-01", DailyReportBuilder.parseDump(res("daily/fixture-daily-synthetic.txt")))
        val out = DailyReportBuilder.render(r, null, "", listOf("Block 1: 45 of 90 min", "Block 2: 30 min"))
        val at = out.indexOf("## Study blocks\n- Block 1: 45 of 90 min\n- Block 2: 30 min\n")
        assertTrue(out, at > out.indexOf("## Time span"))
        assertTrue(out, at < out.indexOf("## Volume & accuracy"))
        assertFalse(DailyReportBuilder.render(r).contains("## Study blocks"))
        assertEquals(DailyReportBuilder.render(r), DailyReportBuilder.render(r, null, "", emptyList()))
        val day = StudyTools.today(tz)
        val t = StudyTools.dayStartMs(day, tz) + 3_600_000L
        val rows = listOf(StudyReview(t, 1L, 3, 4000, 1, "P::Mock Exam 2"))
        val hist = "$day|1|2700000|90;1999-01-01|1|600000|0"
        val rep = StudyToolRunner(Fake(rows), "P", tz).dailyReport("P", t + 60_000, "", hist)
        assertTrue(rep, rep.contains("## Study blocks\n- Block 1: 45 of 90 min\n"))
        assertFalse(rep, rep.contains("Block 1: 10 min"))
    }

    @Test
    fun whereBrokeUsesSyntheticThreadsAndBookmarks() {
        val json = res("insights/thread-history.json")
        val all = UnderstandingInsights.whereBroke(json, 0L, Long.MAX_VALUE)
        assertTrue(all.startsWith("## Where understanding broke\n"))
        assertTrue(all.contains("questions") || all.contains("question"))
        assertFalse(all.contains("study report\""))
        val none = UnderstandingInsights.whereBroke(json, 0L, 1L)
        assertTrue(none.contains("No card-thread questions or bookmarks today."))
        val bm = """{"threads":[{"cardId":7,"messages":[{"role":"user","text":"[bookmark] later","at":5}]}]}"""
        assertEquals("## Where understanding broke\n- Stem seven: bookmarked\n",
            UnderstandingInsights.whereBroke(bm, 0L, 10L, mapOf(7L to "Stem seven")))
        val rep = """{"threads":[{"cardId":8,"messages":[{"role":"user","text":"today's study report","at":5}]}]}"""
        assertTrue(UnderstandingInsights.whereBroke(rep, 0L, 10L).contains("No card-thread"))
    }

    private class Fake(val rows: List<StudyReview>, val fail: Boolean = false) : StudyAnki {
        override fun cardReviewsTree(deck: String, startId: Long): List<StudyReview> {
            if (fail) throw java.io.IOException("down")
            return rows
        }
        override fun cardsInfo(ids: List<Long>): List<OfflineCard> = emptyList()
        override fun noteTags(ids: List<Long>): Map<Long, List<String>> = emptyMap()
        override fun reportFields(ids: List<Long>) = mapOf(1L to ReportFields("P::Mock Exam 2", "Data Governance", "Which grant lets you read?", "SELECT"))
    }

    @Test
    fun runnerDailyReportCoversSubdecksAndThreads() {
        val day = StudyTools.today(tz)
        val t = StudyTools.dayStartMs(day, tz) + 3_600_000L
        val rows = listOf(
            StudyReview(t, 1L, 1, 0, 0, "P::Mock Exam 2"),
            StudyReview(t + 1000, 2L, 3, 4000, 1, "P::Practice::01 Databricks Lakehouse Platform"),
        )
        val threads = """{"threads":[{"cardId":1,"messages":[{"role":"user","text":"why is it SELECT?","at":${t + 5}}]}]}"""
        val out = StudyToolRunner(Fake(rows), "P", tz).dailyReport("P", t + 60_000, threads)
        assertTrue(out, out.startsWith("# Daily study report — $day · issued "))
        assertTrue(out.contains("2 cards, 2 reviews, 50.0% accuracy"))
        assertTrue(out.contains("- Mock Exam 2: 0.0% (1 reviews)"))
        assertTrue(out.contains("- Data Governance: 0.0% (0/1)"))
        assertFalse(out.contains("- Databricks Lakehouse Platform: 100.0% (1/1)"))
        assertTrue(out.contains("### Unity Catalog (1)") || out.contains("(1)\n- Which grant"))
        assertTrue(out.contains("- Which grant lets you read?: 1 question (mostly why)"))
        val err = StudyToolRunner(Fake(rows, fail = true), "P", tz).dailyReport("P", t)
        assertTrue(err.contains("Error: could not read reviews from Anki (IOException)"))
    }
}
