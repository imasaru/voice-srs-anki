package com.evan.voicesrs

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTalkTest {
    @Test
    fun `clip cuts an invented Learner turn`() {
        val raw = "Delta Lake is the ACID table format.\n\nLearner: Nice llama analogy!\nYes, that tracks."
        assertEquals("Delta Lake is the ACID table format.", SelfTalk.clip(raw))
        assertEquals(
            "Keep this.",
            SelfTalk.clip("Keep this.\nLearner said: thanks\nYou're welcome.")
        )
        assertEquals("Keep this.", SelfTalk.clip("Keep this."))
        assertEquals("Keep this.", SelfTalk.clip("Keep this.\nUser: invented\nNo."))
    }

    @Test
    fun `request body includes Learner stop sequences`() {
        val body = AiRequest.completionBody(
            model = "m",
            messages = JSONArray().put(org.json.JSONObject().put("role", "user").put("content", "hi")),
            maxTokens = 16,
            stream = true
        )
        val stop = body.getJSONArray("stop")
        val values = List(stop.length()) { stop.getString(it) }
        assertTrue(values.contains("\nLearner:"))
        assertTrue(values.contains("\nLearner said:"))
        assertEquals(SelfTalk.STOP, values)
        assertFalse(body.toString().contains("Learner said: hi"))
    }

    @Test
    fun `stream body carries stream_options when streaming`() {
        val streaming = AiRequest.completionBody(
            model = "m",
            messages = JSONArray(),
            maxTokens = 16,
            stream = true
        )
        val usage = streaming.getJSONObject("stream_options")
        assertTrue(usage.getBoolean("include_usage"))

        val nonStreaming = AiRequest.completionBody(
            model = "m",
            messages = JSONArray(),
            maxTokens = 16,
            stream = false
        )
        assertFalse(nonStreaming.has("stream_options"))
    }
}
