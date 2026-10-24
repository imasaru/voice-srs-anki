package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class OwnWordsJudgeTest {
    @Test
    fun `request json is system one with question correct and user answer`() {
        val json = OwnWordsJudge.requestJson(
            model = "clef",
            question = "What is 2+2?",
            correctAnswer = "B. 4",
            userAnswer = "four"
        )
        assertEquals("clef", json.getString("model"))
        val state = json.getJSONObject("state")
        assertEquals("What is 2+2?", state.getString("question"))
        assertEquals("B. 4", state.getString("correct_answer"))
        assertEquals("four", state.getString("user_answer"))
        val q = json.getJSONObject("questions").getJSONObject(OwnWordsJudge.QUESTION_ID)
        assertEquals("noul", q.getString("type"))
        assertTrue(q.getString("instructions").isNotBlank())
        assertEquals(
            "http://omlx.example.invalid/v1/systemone",
            OwnWordsJudge.endpoint("http://omlx.example.invalid")
        )
        assertEquals(
            "http://omlx.example.invalid/v1/systemone",
            OwnWordsJudge.endpoint("http://omlx.example.invalid/v1")
        )
        assertEquals(0.70, OwnWordsJudge.THRESHOLD, 0.0)
    }

    @Test
    fun `parse noul confidence at threshold is Good and below is Again`() {
        val good = OwnWordsJudge.parse(
            """{"answers":{"correct":{"type":"noul","noul":0.70,"confidence":0.70}}}"""
        )
        val goodV = good as OwnWordsJudge.Verdict.Graded
        assertEquals(3, goodV.ease)
        assertTrue(goodV.correct)
        assertEquals(0.70, goodV.confidence, 0.0)

        val again = OwnWordsJudge.parse(
            """{"answers":{"correct":{"type":"noul","noul":0.69}}}"""
        )
        val againV = again as OwnWordsJudge.Verdict.Graded
        assertEquals(1, againV.ease)
        assertFalse(againV.correct)
        assertEquals(0.69, againV.confidence, 0.0)

        assertEquals(3, (OwnWordsJudge.grade(0.70) as OwnWordsJudge.Verdict.Graded).ease)
        assertEquals(1, (OwnWordsJudge.grade(0.69) as OwnWordsJudge.Verdict.Graded).ease)
    }

    @Test
    fun `malformed body and timeout fall back`() {
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse("not-json"))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse("{}"))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse("""{"answers":{}}"""))
        assertEquals(
            OwnWordsJudge.Verdict.Fallback,
            OwnWordsJudge.result(null, SocketTimeoutException("read"))
        )
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.result("", null))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.result(null, null))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.grade(Double.NaN))
    }

    @Test
    fun `letter answers are not sent to the judge`() {
        val letters = setOf("A", "B", "C", "D")
        assertFalse(OwnWordsJudge.shouldJudge("B", letters))
        assertFalse(OwnWordsJudge.shouldJudge("bee", letters))
        assertTrue(OwnWordsJudge.shouldJudge("the union of the two frames", letters))
        assertFalse(OwnWordsJudge.shouldJudge("four", emptySet()))
        assertFalse(OwnWordsJudge.shouldJudge("  ", letters))
        assertEquals(
            "B. 4",
            OwnWordsJudge.correctText(
                "What is 2+2?\nA. 3\nB. 4\nC. 5",
                "B",
                ""
            )
        )
    }
}
