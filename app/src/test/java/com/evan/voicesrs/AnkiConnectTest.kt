package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectTest {
    @Test
    fun `answerCards body includes duration in ms when known`() {
        val grades = listOf(
            PendingGrade(1_498_938_915_662L, 2, 10L, durationMs = 4_200L),
            PendingGrade(1_502_098_034_048L, 4, 11L, durationMs = 1_500L)
        )
        val json = AnswerCardsRequest.body(grades).toString()
        val first = AnswerCardsRequest.body(grades).getJSONObject(0)
        assertEquals(1_498_938_915_662L, first.getLong("cardId"))
        assertEquals(2, first.getInt("ease"))
        assertEquals(4_200L, first.getLong("time"))
        assertTrue(json.contains("\"time\":1500"))
        assertFalse(json.contains("durationMs"))
    }

    @Test
    fun `unknown duration omits time so stock AnkiConnect stays valid`() {
        val old = PendingGrade(9L, 3, 100L)
        val encoded = AnswerCardsRequest.body(listOf(old)).getJSONObject(0)
        assertEquals(9L, encoded.getLong("cardId"))
        assertEquals(3, encoded.getInt("ease"))
        assertFalse(encoded.has("time"))
        val stripped = AnswerCardsRequest.body(
            listOf(PendingGrade(9L, 3, 100L, durationMs = 4_200L)),
            includeTime = false
        ).getJSONObject(0)
        assertFalse(stripped.has("time"))
    }

    @Test
    fun `a time-field rejection retries without duration`() {
        assertTrue(AnswerCardsRequest.timeRejected("unexpected keyword argument 'time'"))
        assertTrue(AnswerCardsRequest.timeRejected("Unknown field: duration"))
        assertTrue(AnswerCardsRequest.timeRejected("invalid extra time"))
        assertFalse(AnswerCardsRequest.timeRejected("card not found"))
        assertFalse(AnswerCardsRequest.timeRejected(null))
        assertFalse(AnswerCardsRequest.timeRejected(""))
    }
}
