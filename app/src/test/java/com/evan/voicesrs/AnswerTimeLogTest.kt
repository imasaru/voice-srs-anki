package com.evan.voicesrs

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.TimeZone

class AnswerTimeLogTest {
    @After
    fun tearDown() = AnswerTimeLog.reset()

    @Test
    fun encodeDecodeRoundTrip() {
        val rows = listOf(AnswerTime(1L, "Jp::N3", 3, 4200L, 1000L), AnswerTime(2L, "Jp", 1, 0L, 2000L))
        assertEquals(rows, AnswerTimeLog.decode(AnswerTimeLog.encode(rows)))
        assertEquals(emptyList<AnswerTime>(), AnswerTimeLog.decode("not json"))
    }

    @Test
    fun recordPersistsAndReloads() {
        val dir = Files.createTempDirectory("atl").toFile()
        AnswerTimeLog.init(dir)
        AnswerTimeLog.record(AnswerTime(7L, "Jp", 4, 1500L, 99L))
        AnswerTimeLog.record(AnswerTime(0L, "Jp", 4, 1500L, 99L))
        assertTrue(AnswerTimeLog.file(dir).isFile)
        AnswerTimeLog.reset()
        AnswerTimeLog.init(dir)
        assertEquals(listOf(AnswerTime(7L, "Jp", 4, 1500L, 99L)), AnswerTimeLog.snapshot())
        dir.deleteRecursively()
    }

    @Test
    fun trimKeepsNewest() {
        val rows = (1L..5L).map { AnswerTime(it, "d", 3, 1L, it) }
        assertEquals(listOf(4L, 5L), AnswerTimeLog.trim(rows, 2).map { it.cardId })
    }

    @Test
    fun answerTimeTotalsForRangeAndDeck() {
        val log = listOf(
            AnswerTime(1L, "Jp", 3, 4000L, 100L),
            AnswerTime(2L, "Jp::N3", 1, 6000L, 200L),
            AnswerTime(3L, "Other", 3, 9000L, 150L),
            AnswerTime(4L, "Jp", 3, 8000L, 999L),
            AnswerTime(5L, "Jp", 3, 0L, 120L)
        )
        val o = StudyTools.answerTime(log, 100L, 300L, "Jp", 2)
        assertEquals(2, o.getInt("timed_reviews"))
        assertEquals(10000L, o.getLong("total_answer_time_ms"))
        assertEquals(5000L, o.getLong("avg_answer_time_ms"))
        assertFalse(o.has("note"))
        val partial = StudyTools.answerTime(log, 100L, 300L, "Jp", 5)
        assertTrue(partial.getString("note").contains("2 of 5"))
    }

    @Test
    fun answerTimeUnavailableForOlderReviews() {
        val o = StudyTools.answerTime(emptyList(), 0L, 10L, "Jp", 12)
        assertEquals(StudyTools.UNAVAILABLE, o.getString("answer_time"))
        assertTrue(o.getString("note").contains("older reviews"))
    }

    @Test
    fun runnerToolsIncludeAnswerTime() {
        val tz = TimeZone.getTimeZone("UTC")
        val day = "2026-10-20"
        val at = StudyTools.dayStartMs(day, tz) + 60_000L
        val log = listOf(AnswerTime(1L, "Jp", 3, 3000L, at))
        val runner = StudyToolRunner(AnkiConnect("http://anki.example.invalid:1", ""), "Jp", tz) { log }
        val args = "{\"from\":\"$day\",\"to\":\"$day\"}"
        for (tool in listOf(StudyTools.GET_STUDY_SUMMARY, StudyTools.GET_CATEGORY_STATS)) {
            val time = JSONObject(runner.invoke(tool, args)).getJSONObject("answer_time")
            assertEquals(3000L, time.getLong("total_answer_time_ms"))
        }
    }
}
