package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ReportDataTest {
    private val tz = TimeZone.getTimeZone("Asia/Tokyo")

    @Test
    fun parseCardReviewsKeepsRevlogType() {
        val arr = JSONArray("[[1000,5,0,1,10,0,2500,4200,0],[2000,5,0,3,10,0,2500,0,1]]")
        val rows = StudyTools.parseCardReviews(arr)
        assertEquals(listOf(0, 1), rows.map { it.type })
    }

    @Test
    fun dumpCardsGroupsByCardAcrossSubdecks() {
        val t0 = StudyTools.dayStartMs("2026-10-26", tz) + 12 * 3_600_000L + 7 * 60_000L
        val rows = listOf(
            StudyReview(t0 + 60_000, 1L, 3, 0, 1, "DEA::Mock Exam 1"),
            StudyReview(t0, 1L, 1, 900, 0, "DEA::Mock Exam 1"),
            StudyReview(t0 + 5_000, 2L, 3, 100, 0, "DEA::Practice::05 Data Governance"),
        )
        val fields = mapOf(1L to ReportFields("DEA::Mock Exam 1", "Data Governance", "Which grant?", "GRANT USE CATALOG"))
        val cards = ReportData.dumpCards(rows, fields, tz)
        assertEquals(listOf(1L, 2L), cards.map { it.cid })
        assertEquals(listOf("12:07:00", "12:08:00"), cards[0].revs.map { it.time })
        assertEquals(listOf(1, 3), cards[0].revs.map { it.ease })
        assertEquals(0, cards[0].revs.first().type)
        assertEquals("Data Governance", cards[0].domain)
        assertEquals("Data Governance", cards[1].domain)
        assertEquals("DEA::Practice::05 Data Governance", cards[1].deck)
        val r = DailyReportBuilder.build("2026-10-26", cards)
        assertEquals(2, r.cards)
        assertEquals(2, r.newCards)
        assertEquals(listOf("Mock Exam 1", "05 Data Governance"), r.byDeck.map { it.name })
    }

    @Test
    fun fieldsOfReadsNoteFieldsWithFallbacks() {
        val o = JSONObject(
            """{"cardId":9,"deckName":"DEA::Mock Exam 2","question":"<b>Q?</b>",
               "fields":{"Domain":{"value":"ELT"},"Correct":{"value":"B"}}}"""
        )
        val f = ReportData.fieldsOf(o)
        assertEquals(ReportFields("DEA::Mock Exam 2", "ELT", "Q?", "B"), f)
    }
}
