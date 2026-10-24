package com.evan.voicesrs

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class StudyToolsTest {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val day = "2026-10-24"
    private val start = StudyTools.dayStartMs(day, utc)

    @Test
    fun `schema json names the three tools`() {
        val json = StudyTools.schemas().toString()
        assertTrue(json.contains(StudyTools.GET_STUDY_SUMMARY))
        assertTrue(json.contains(StudyTools.GET_CATEGORY_STATS))
        assertTrue(json.contains(StudyTools.GET_MISSED_CARDS))
        assertTrue(json.contains("\"type\":\"function\""))
        assertTrue(json.contains("from"))
        assertTrue(json.contains("deck"))
        assertEquals(4, StudyTools.MAX_ROUNDS)
    }

    @Test
    fun `summary accuracy and unavailable study time`() {
        val rows = listOf(
            StudyReview(start + 1, 1L, 3, 4_000L),
            StudyReview(start + 2, 1L, 1, 0L),
            StudyReview(start + 3, 2L, 4, 2_000L),
            StudyReview(start + 4, 3L, 2, 1_000L)
        )
        val sum = StudyTools.summary(rows)
        assertEquals(3, sum.getInt("cards"))
        assertEquals(4, sum.getInt("reviews"))
        assertEquals(1, sum.getInt("again"))
        assertEquals(1, sum.getInt("hard"))
        assertEquals(1, sum.getInt("good"))
        assertEquals(1, sum.getInt("easy"))
        assertEquals(50.0, sum.getDouble("accuracy"), 0.01)
        assertEquals(7_000L, sum.getLong("study_time_ms"))

        val old = listOf(
            StudyReview(start + 1, 1L, 3, 0L),
            StudyReview(start + 2, 2L, 1, 0L)
        )
        assertEquals(StudyTools.UNAVAILABLE, StudyTools.summary(old).getString("study_time"))
        assertFalse(StudyTools.summary(old).has("study_time_ms"))
        assertEquals(StudyTools.UNAVAILABLE, StudyTools.summary(emptyList()).getString("study_time"))
    }

    @Test
    fun `category first-try percent and missed list honor the limit`() {
        val cards = mapOf(
            1L to StudyCard(1L, listOf("Spark"), "How does Delta work?"),
            2L to StudyCard(2L, listOf("SQL", "marked"), "What is a view?"),
            3L to StudyCard(3L, emptyList(), "Name the lakehouse layers")
        )
        val rows = listOf(
            StudyReview(start + 1, 1L, 3, 1_000L),
            StudyReview(start + 2, 1L, 1, 1_000L),
            StudyReview(start + 3, 2L, 1, 1_000L),
            StudyReview(start + 4, 2L, 3, 1_000L),
            StudyReview(start + 5, 3L, 1, 1_000L)
        )
        val stats = StudyTools.categoryStats(rows, cards)
        val byName = (0 until stats.length()).associate { i ->
            val o = stats.getJSONObject(i)
            o.getString("category") to o
        }
        assertEquals(100.0, byName.getValue("Spark").getDouble("first_try_pct"), 0.01)
        assertEquals(50.0, byName.getValue("Spark").getDouble("all_review_pct"), 0.01)
        assertEquals(0.0, byName.getValue("SQL").getDouble("first_try_pct"), 0.01)
        assertEquals(50.0, byName.getValue("SQL").getDouble("all_review_pct"), 0.01)
        assertEquals(0.0, byName.getValue("Untagged").getDouble("first_try_pct"), 0.01)
        assertFalse(byName.containsKey("marked"))

        val missed = StudyTools.missed(rows, cards, limit = 1)
        assertEquals(1, missed.length())
        assertEquals(1L, missed.getJSONObject(0).getLong("cardId"))
        assertEquals("Spark", missed.getJSONObject(0).getString("category"))
        val allMissed = StudyTools.missed(rows, cards, limit = 10)
        assertEquals(2, allMissed.length())
        assertEquals(3L, allMissed.getJSONObject(1).getLong("cardId"))
    }

    @Test
    fun `missing duration is unavailable and fallback detection`() {
        assertTrue(StudyTools.toolsRejected(400, "unexpected keyword argument tools"))
        assertTrue(StudyTools.toolsRejected(400, "Unknown field: tool_choice"))
        assertFalse(StudyTools.toolsRejected(400, "card not found"))
        assertFalse(StudyTools.toolsRejected(500, "tools"))
        assertTrue(StudyTools.needsFallbackChip(offeredTools = true, sawToolCalls = false, rejected = false))
        assertTrue(StudyTools.needsFallbackChip(offeredTools = true, sawToolCalls = false, rejected = true))
        assertFalse(StudyTools.needsFallbackChip(offeredTools = true, sawToolCalls = true, rejected = false))
        assertFalse(StudyTools.needsFallbackChip(offeredTools = false, sawToolCalls = false, rejected = false))
        assertTrue(StudyTools.shortQuestion("<b>How does Delta work?</b><br>A. files").startsWith("How does Delta work?"))
        assertTrue(StudyTools.shortQuestion("x".repeat(90)).endsWith("…"))
        assertEquals(listOf("Untagged"), StudyTools.categories(listOf("marked", "leech", "_hidden")))
        assertTrue(StudyTools.looksLikeStudyAsk("How did I do today?"))
        assertFalse(StudyTools.looksLikeStudyAsk("What is a lakehouse?"))
    }

    @Test
    fun `cardReviews tuples become review rows`() {
        val arr = JSONArray()
            .put(JSONArray().put(start + 5).put(9L).put(-1).put(3).put(1).put(0).put(2500).put(4_200).put(1))
            .put(JSONArray().put(0).put(9L).put(-1).put(1).put(0).put(0).put(0).put(0).put(0))
        val parsed = StudyTools.parseCardReviews(arr)
        assertEquals(1, parsed.size)
        assertEquals(9L, parsed[0].cardId)
        assertEquals(3, parsed[0].ease)
        assertEquals(4_200L, parsed[0].timeMs)
    }
}
