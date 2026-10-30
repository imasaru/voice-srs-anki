package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class H98OwnWordsTest {
    @Test
    fun answer_arriving_at_think_deadline_settles_instead_of_being_dropped() {
        // Logcat 12:18:19 JST: stitched own-words listen ended at the deadline, transcript lost.
        assertEquals(ThinkTime.Step.ACCEPT, ThinkTime.nextStep(ThinkTime.Hear.ANSWER, bufferEmpty = true, remainingMs = 0L, settling = false))
        assertEquals(ThinkTime.Step.ACCEPT, ThinkTime.nextStep(ThinkTime.Hear.ANSWER, bufferEmpty = true, remainingMs = -120L, settling = false))
    }

    @Test
    fun empty_or_command_at_deadline_still_reveals() {
        assertEquals(ThinkTime.Step.REVEAL, ThinkTime.nextStep(ThinkTime.Hear.EMPTY, bufferEmpty = false, remainingMs = 0L, settling = true))
        assertEquals(ThinkTime.Step.REVEAL, ThinkTime.nextStep(ThinkTime.Hear.SKIP, bufferEmpty = true, remainingMs = 0L, settling = false))
    }

    private fun reply(content: String) =
        org.json.JSONObject().put("choices", org.json.JSONArray().put(
            org.json.JSONObject().put("message", org.json.JSONObject().put("content", content)))).toString()

    @Test
    fun off_rubric_bad_rating_from_judge_maps_to_again() {
        val v = OwnWordsJudge.parse(reply("{\n  \"rating\": \"bad\",\n  \"reason\": \"unrelated\"\n}"))
        assertTrue(v is OwnWordsJudge.Verdict.Graded)
        assertEquals(OwnWordsJudge.Rating.AGAIN, (v as OwnWordsJudge.Verdict.Graded).rating)
    }

    @Test
    fun synonyms_map_and_garbage_still_falls_back() {
        assertEquals(OwnWordsJudge.Rating.GOOD, OwnWordsJudge.Rating.of("Correct"))
        assertEquals(OwnWordsJudge.Rating.HARD, OwnWordsJudge.Rating.of("partial"))
        assertEquals(null, OwnWordsJudge.Rating.of("banana"))
        assertEquals(OwnWordsJudge.Verdict.Fallback, OwnWordsJudge.parse(reply("{\"rating\":\"banana\"}")))
    }
}
