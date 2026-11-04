package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

/** h145a: the daily report reads answer time from the app-local AnswerTimeLog when Anki's revlog.time is 0 (stock AnkiConnect arms the timer before answering). */
class H145aTest {
    private val tz = TimeZone.getTimeZone("UTC")

    @Test
    fun fillsZeroRevWithinGap() {
        val at = 1_000_000L
        val rows = listOf(StudyReview(at, 1L, 3, 0L, 1, "Jp::N3"))
        val local = listOf(AnswerTime(1L, "Jp::N3", 3, 5300L, at + 10_000L))
        assertEquals(5300L, ReportData.dumpCards(rows, emptyMap(), tz, local).first().revs.first().ms)
    }

    @Test
    fun noMatchAcrossGapCardOrEase() {
        val at = 1_000_000L
        val r = { l: List<AnswerTime> -> ReportData.dumpCards(listOf(StudyReview(at, 1L, 3, 0L, 1, "Jp")), emptyMap(), tz, l).first().revs.first().ms }
        assertEquals(0L, r(listOf(AnswerTime(1L, "Jp", 3, 4000L, at + 130_000L))))
        assertEquals(0L, r(listOf(AnswerTime(2L, "Jp", 3, 4000L, at + 100L))))
        assertEquals(0L, r(listOf(AnswerTime(1L, "Jp", 2, 4000L, at + 100L))))
    }

    @Test
    fun keepsRealRevlogTime() {
        val rows = listOf(StudyReview(1_000_000L, 1L, 3, 7000L, 1, "Jp"))
        val local = listOf(AnswerTime(1L, "Jp", 3, 5300L, 1_000_000L))
        assertEquals(7000L, ReportData.dumpCards(rows, emptyMap(), tz, local).first().revs.first().ms)
    }

    @Test
    fun eachAnswerTimeUsedOnce() {
        val revs = listOf(StudyReview(1000L, 1L, 3, 0L, 1, "Jp"), StudyReview(1100L, 1L, 3, 0L, 1, "Jp"))
        val local = listOf(AnswerTime(1L, "Jp", 3, 5300L, 1020L), AnswerTime(1L, "Jp", 3, 6000L, 1200L))
        val o = ReportData.dumpCards(revs, emptyMap(), tz, local).first().revs
        assertEquals(5300L, o[0].ms)
        assertEquals(6000L, o[1].ms)
    }

    @Test
    fun renderedReportNoZeroMsNote() {
        val day = "2026-10-20"
        val start = StudyTools.dayStartMs(day, tz)
        val revs = listOf(StudyReview(start + 1000L, 1L, 3, 0L, 1, "Jp::N3"), StudyReview(start + 2000L, 2L, 2, 0L, 1, "Jp::N3"))
        val local = listOf(AnswerTime(1L, "Jp::N3", 3, 5000L, start + 1000L), AnswerTime(2L, "Jp::N3", 2, 7000L, start + 2000L))
        val report = DailyReportBuilder.build(day, ReportData.dumpCards(revs, emptyMap(), tz, local))
        assertEquals(0, report.zeroMsReviews)
        assertFalse(DailyReportBuilder.render(report, "21:00 JST").contains("0 ms"))
    }
}
