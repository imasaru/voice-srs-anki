package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolCallsTest {
    // Build every angle-bracket tag from escapes so the environment's own
    // tool-call parser never intercepts them.
    private val LT = "\u003c"
    private val GT = "\u003e"
    private val TOOL_CALL_OPEN = LT + "tool_call" + GT
    private val TOOL_CALL_CLOSE = LT + "/tool_call" + GT
    private val FUNCTION_OPEN_PREFIX = LT + "function="
    private val FUNCTION_CLOSE = LT + "/function" + GT
    private val PARAM_OPEN_PREFIX = LT + "parameter="
    private val PARAM_CLOSE = LT + "/parameter" + GT

    private val jsonInner = """{"name":"get_category_stats","arguments":{"deck":"X"}}"""

    @Test
    fun `exact get_study_summary with no params`() {
        val sample =
            TOOL_CALL_OPEN + "\n" +
            FUNCTION_OPEN_PREFIX + "get_study_summary" + GT + "\n" +
            FUNCTION_CLOSE + "\n" +
            TOOL_CALL_CLOSE + "\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("get_study_summary", calls[0].name)
        assertEquals("{}", calls[0].arguments)
        assertEquals("", stripped)
    }

    @Test
    fun `one call with a parameter`() {
        val sample =
            TOOL_CALL_OPEN + "\n" +
            FUNCTION_OPEN_PREFIX + "get_category_stats" + GT + "\n" +
            PARAM_OPEN_PREFIX + "deck" + GT + "\n" +
            "Front\n" +
            PARAM_CLOSE + "\n" +
            FUNCTION_CLOSE + "\n" +
            TOOL_CALL_CLOSE + "\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("get_category_stats", calls[0].name)
        val args = JSONObject(calls[0].arguments)
        assertEquals("Front", args.getString("deck"))
        assertEquals("", stripped)
    }

    @Test
    fun `two calls in one response`() {
        val b1 =
            TOOL_CALL_OPEN + "\n" +
            FUNCTION_OPEN_PREFIX + "get_study_summary" + GT + "\n" +
            FUNCTION_CLOSE + "\n" +
            TOOL_CALL_CLOSE + "\n"
        val b2 =
            TOOL_CALL_OPEN + "\n" +
            FUNCTION_OPEN_PREFIX + "get_category_stats" + GT + "\n" +
            PARAM_OPEN_PREFIX + "deck" + GT + "\n" +
            "Hard\n" +
            PARAM_CLOSE + "\n" +
            FUNCTION_CLOSE + "\n" +
            TOOL_CALL_CLOSE + "\n"
        val sample = "before\n" + b1 + "\n" + b2 + "after\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(2, calls.size)
        assertEquals("get_study_summary", calls[0].name)
        assertEquals("get_category_stats", calls[1].name)
        assertEquals("Hard", JSONObject(calls[1].arguments).getString("deck"))
        assertTrue(stripped.startsWith("before"))
        assertTrue(stripped.endsWith("after"))
    }

    @Test
    fun `JSON in wrapper form`() {
        val sample =
            TOOL_CALL_OPEN + "\n" +
            jsonInner + "\n" +
            TOOL_CALL_CLOSE + "\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("get_category_stats", calls[0].name)
        val args = JSONObject(calls[0].arguments)
        assertEquals("X", args.getString("deck"))
        assertEquals("", stripped)
    }

    @Test
    fun `plain code block with a name field is not a tool call`() {
        val code = """
            |```json
            |{"name":"get_category_stats","arguments":{"deck":"X"}}
            |```
        """.trimMargin()
        val (calls, stripped) = TextToolCalls.extract(code)
        assertEquals(0, calls.size)
        assertEquals(code, stripped)
    }

    @Test
    fun `unclosed tool_call wrapper is left as-is`() {
        val sample =
            "intro\n" + TOOL_CALL_OPEN + "\n" +
            FUNCTION_OPEN_PREFIX + "get_study_summary" + GT + "\n" +
            FUNCTION_CLOSE
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(0, calls.size)
        assertEquals(sample, stripped)
    }

    @Test
    fun `plain text with no tags returns no calls`() {
        val text = "You studied 12 cards today. Nice work!"
        val (calls, stripped) = TextToolCalls.extract(text)
        assertEquals(0, calls.size)
        assertEquals(text, stripped)
    }
}
