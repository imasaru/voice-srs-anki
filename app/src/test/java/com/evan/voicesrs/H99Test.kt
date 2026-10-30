package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class H99Test {
    private fun completion(content: String) = JSONObject().put("choices",
        org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()

    private fun rating(content: String) =
        (OwnWordsJudge.parse(completion(content)) as? OwnWordsJudge.Verdict.Graded)?.rating

    @Test fun request_uses_strict_schema_with_rating_enum() {
        val b = OwnWordsJudge.requestJson("m", "Q?", "B. x", "y")
        val rf = b.getJSONObject("response_format")
        assertEquals("json_schema", rf.getString("type"))
        val schema = rf.getJSONObject("json_schema").getJSONObject("schema")
        assertEquals("[\"again\",\"hard\",\"good\",\"easy\"]",
            schema.getJSONObject("properties").getJSONObject("rating").getJSONArray("enum").toString())
        assertEquals(0, b.getInt("temperature"))
        assertTrue(OwnWordsJudge.systemPrompt(OwnWordsJudge.Strictness.NORMAL).contains("Example:"))
    }

    @Test fun retry_carries_reminder_and_previous_reply() {
        val b = OwnWordsJudge.requestJson("m", "Q?", "B", "y", previousReply = "{ }")
        val msgs = b.getJSONArray("messages")
        assertEquals(4, msgs.length())
        assertEquals("{ }", msgs.getJSONObject(2).getString("content"))
        assertEquals(OwnWordsJudge.REMINDER, msgs.getJSONObject(3).getString("content"))
    }

    @Test fun second_try_gets_first_reply_text() {
        val seen = mutableListOf<String?>()
        val replies = listOf(completion("{ }"), completion("{\"rating\":\"good\",\"reason\":\"ok\"}"))
        val a = OwnWordsJudge.judgeWithReminder({ false }) { prev -> seen.add(prev); replies[seen.size - 1] }
        assertEquals(listOf(null, "{ }"), seen)
        assertEquals(2, a.tries)
        assertEquals(OwnWordsJudge.Rating.GOOD, (a.verdict as OwnWordsJudge.Verdict.Graded).rating)
    }

    // Real replies from the 12:3x JST live calls (json_object mode).
    @Test fun live_replies_q10_q11_q13_q17() {
        assertEquals(null, rating("{ }"))
        assertEquals(OwnWordsJudge.Rating.AGAIN,
            rating("{ \"again\" : \"B. SQL queries on Delta Lake tables\" , \"reason\" : \"completely wrong\" }"))
        assertEquals(OwnWordsJudge.Rating.GOOD, rating("{ \"good\" : \"good\" }"))
        assertEquals(OwnWordsJudge.Rating.HARD, rating("{ \"rating\": \"hard\" }"))
        // New schema replies for the Q10 / Q11 / Q13 / Q17 inputs.
        assertEquals(OwnWordsJudge.Rating.GOOD, rating("{ \"rating\": \"good\" , \"reason\" : \"Names the effect of the policy.\" }"))
        assertEquals(OwnWordsJudge.Rating.HARD, rating("{ \"rating\": \"hard\", \"reason\": \"Swapped the terms for EDA and production.\" }"))
        assertEquals(OwnWordsJudge.Rating.AGAIN, rating("{ \"rating\": \"again\", \"reason\": \"Unrelated to the question.\" }"))
    }

    @Test fun tolerant_parse_fences_keys_and_plain_word() {
        assertEquals(OwnWordsJudge.Rating.GOOD, rating("```json\n{\"Rating\": \"Good\", \"Reason\": \"x\"}\n```"))
        assertEquals(OwnWordsJudge.Rating.HARD, rating("{\"grade\": \"partial\"}"))
        assertEquals(OwnWordsJudge.Rating.EASY, rating("{\"score\": \"easy\"}"))
        assertEquals(OwnWordsJudge.Rating.AGAIN, rating("Rating: again, it is unrelated."))
        assertEquals(null, rating("{\"rating\": \"maybe\"}"))
        val g = OwnWordsJudge.parse(completion("{\"RATING\":\"good\",\"REASON\":\"  Names   it \"}")) as OwnWordsJudge.Verdict.Graded
        assertEquals("Names it", g.reason)
    }

    @Test fun log_shows_full_content_not_envelope() {
        val reason = "r".repeat(300)
        val log = OwnWordsJudge.contentForLog(completion("{\"rating\":\"good\",\"reason\":\"$reason\"}"))
        assertTrue(log.startsWith("{\"rating\":\"good\""))
        assertTrue(log.contains(reason))
    }

    @Test fun silence_extra_is_int() {
        val v: Any = SpokenLetter.silenceExtra(2000L)
        assertTrue(v is Int)
        assertEquals(2000, v)
    }

    @Test fun q10_code_sentence_highlights() {
        val shown = "Which statement is true? A cluster policy is configured with spark.executor.memory set as a fixed value. What happens?"
        val spoken = "A cluster policy is configured with spark dot executor dot memory set as a fixed value."
        val r = SentenceReading.spanIn(shown, spoken)
        assertNotNull(r)
        assertEquals("A cluster policy is configured with spark.executor.memory set as a fixed value", shown.substring(r!!))
    }

    @Test fun q11_option_punctuation_highlights() {
        val shown = "D. All-purpose cluster for EDA (exploratory data analysis); job cluster with Workflows for production"
        val r = SentenceReading.spanIn(shown, "All-purpose cluster for EDA, exploratory data analysis,")
        assertNotNull(r)
        assertTrue(shown.substring(r!!).startsWith("All-purpose cluster for EDA (exploratory data analysis"))
        assertEquals(null, SentenceReading.spanIn(shown, "inner join returns matches"))
    }

    @Test fun highlight_poll_is_40ms() { assertEquals(40L, EdgeSpeaker.POSITION_POLL_MS) }
}
