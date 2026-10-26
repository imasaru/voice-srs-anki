package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class OwnWordsJudgeTest {
    /** Build a valid OpenAI-style chat completion. [wrap] surrounds the model's
     * JSON content with extra text / code fences. */
    private fun chat(correct: Boolean, confidence: Double, wrap: String = ""): String {
        val inner = JSONObject().put("correct", correct)
            .put("confidence", confidence).toString()
        val choices = JSONArray().put(
            JSONObject().put("message", JSONObject().put("content", wrap + inner + wrap))
        )
        return JSONObject().put("choices", choices).toString()
    }

    @Test
    fun `request body is openai chat completion`() {
        val json = OwnWordsJudge.requestJson(
            model = "clef",
            question = "What is 2+2?",
            correctAnswer = "B. 4",
            userAnswer = "four"
        )
        assertEquals("clef", json.getString("model"))
        assertEquals(0, json.getInt("temperature"))
        assertEquals(64, json.getInt("max_tokens"))
        assertFalse(json.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))
        assertEquals("json_object", json.getJSONObject("response_format").getString("type"))

        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").isNotBlank())
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val user = messages.getJSONObject(1).getString("content")
        assertTrue(user.contains("What is 2+2?"))
        assertTrue(user.contains("B. 4"))
        assertTrue(user.contains("four"))
    }

    @Test
    fun `endpoint builds chat completions without doubling v1`() {
        // base without /v1 appends it once
        assertEquals(
            "http://omlx.example.invalid/v1/chat/completions",
            OwnWordsJudge.endpoint("http://omlx.example.invalid")
        )
        // base already ending /v1 is not doubled
        assertEquals(
            "http://omlx.example.invalid/v1/chat/completions",
            OwnWordsJudge.endpoint("http://omlx.example.invalid/v1")
        )
        // trailing slash trimmed, not doubled
        assertEquals(
            "http://omlx.example.invalid/v1/chat/completions",
            OwnWordsJudge.endpoint("http://omlx.example.invalid/v1/")
        )
        // a stray trailing /chat/completions is not doubled either
        assertEquals(
            "http://omlx.example.invalid/v1/chat/completions",
            OwnWordsJudge.endpoint("http://omlx.example.invalid/v1/chat/completions/")
        )
        assertEquals(0.70, OwnWordsJudge.THRESHOLD, 0.0)
    }

    @Test
    fun `parse grades on correct and confidence`() {
        val good = OwnWordsJudge.parse(chat(correct = true, confidence = 0.70)) as OwnWordsJudge.Verdict.Graded
        assertEquals(3, good.ease)
        assertTrue(good.correct)
        assertEquals(0.70, good.confidence, 0.0)

        // correct but below threshold is incorrect
        val low = OwnWordsJudge.parse(chat(correct = true, confidence = 0.69)) as OwnWordsJudge.Verdict.Graded
        assertEquals(1, low.ease)
        assertFalse(low.correct)

        // correct=false is incorrect even at high confidence
        val no = OwnWordsJudge.parse(chat(correct = false, confidence = 0.99)) as OwnWordsJudge.Verdict.Graded
        assertEquals(1, no.ease)
        assertFalse(no.correct)
    }

    @Test
    fun `parse ignores extra text and code fences`() {
        val wrapped = OwnWordsJudge.parse(
            chat(correct = true, confidence = 0.95, wrap = "Here: ```json ")
        ) as OwnWordsJudge.Verdict.Graded
        assertTrue(wrapped.correct)
        assertEquals(0.95, wrapped.confidence, 0.0)

        // leading text before the block still parses
        val leading = OwnWordsJudge.parse(
            chat(correct = true, confidence = 0.8, wrap = "ok here ")
        ) as OwnWordsJudge.Verdict.Graded
        assertTrue(leading.correct)
    }

    @Test
    fun `malformed body and timeout fall back`() {
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse("not-json"))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse("{}"))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse("""{"answers":{}}"""))
        // choices present but content is not a JSON object
        assertEquals(
            OwnWordsJudge.Verdict.Fallback,
            OwnWordsJudge.parse("""{"choices":[{"message":{"content":"not json at all"}}]}""")
        )
        // missing confidence / correct fields
        assertEquals(
            OwnWordsJudge.Verdict.Fallback,
            OwnWordsJudge.parse(
                """{"choices":[{"message":{"content":"{\"confidence\":0.9}"}}]}"""
            )
        )
        assertEquals(
            OwnWordsJudge.Verdict.Fallback,
            OwnWordsJudge.result(null, SocketTimeoutException("read"))
        )
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.result("", null))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.result(null, null))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.grade(Double.NaN))
    }

    @Test
    fun `grade keeps threshold behaviour`() {
        assertEquals(3, (OwnWordsJudge.grade(0.70) as OwnWordsJudge.Verdict.Graded).ease)
        assertEquals(1, (OwnWordsJudge.grade(0.69) as OwnWordsJudge.Verdict.Graded).ease)
    }

    @Test
    fun `fallback reason surfaces without secrets`() {
        assertNull(OwnWordsJudge.fallbackReason(chat(correct = true, confidence = 0.9), null))
        assertEquals("no response", OwnWordsJudge.fallbackReason(null, null))
        val reason = OwnWordsJudge.fallbackReason(null, java.net.ConnectException("boom"))
        assertTrue(reason!!.startsWith("ConnectException"))
        assertTrue(!reason.contains("apikey"))
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

    @Test
    fun blankDecisionUrlFallsBackToMainBase() {
        assertEquals("http://h:8000/v1", OwnWordsJudge.effectiveBase("", "http://h:8000/v1/"))
        assertEquals("http://h:8000/v1/chat/completions",
            OwnWordsJudge.endpoint(OwnWordsJudge.effectiveBase("  ", "http://h:8000/v1")))
        assertEquals("http://h:8000/v1/chat/completions",
            OwnWordsJudge.endpoint(OwnWordsJudge.effectiveBase("", "http://h:8000")))
        assertEquals("http://h:8000/v1/chat/completions",
            OwnWordsJudge.endpoint(OwnWordsJudge.effectiveBase("", "http://h:8000/v1/chat/completions")))
        assertEquals("http://d:1", OwnWordsJudge.effectiveBase("http://d:1", "http://h:8000/v1"))
    }
}
