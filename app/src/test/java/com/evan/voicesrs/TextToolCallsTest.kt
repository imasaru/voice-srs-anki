package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolCallsTest {
    private val BT = "\u0060\u0060\u0060" // three backticks

    private val jsonInner = """{"name":"get_category_stats","arguments":{"deck":"X"}}"""

    @Test
    fun `no params yields empty args and strips the block`() {
        val sample = BT + "function=get_study_summary>" + BT + "/function>" + BT + "\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("get_study_summary", calls[0].name)
        assertEquals("{}", calls[0].arguments)
        assertEquals("", stripped)
    }

    @Test
    fun `parameter form with multiple params`() {
        val sample = "" +
            BT + "function=get_category_stats>" + BT + "\n" +
            BT + "parameter=deck>" + BT + "Front\n" +
            BT + "/parameter>" + BT + "\n" +
            BT + "parameter=from>" + BT + "\n" +
            "2026-10-01\n" +
            BT + "/parameter>" + BT + "\n" +
            BT + "/function>" + BT + "\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("get_category_stats", calls[0].name)
        val args = calls[0].arguments.orEmpty()
        assertTrue("expected deck+Front in " + args, args.contains("deck"))
        assertTrue("expected Front in " + args, args.contains("Front"))
        assertTrue("expected from in " + args, args.contains("from"))
        assertTrue("expected 2026-10-01 in " + args, args.contains("2026-10-01"))
        assertEquals("", stripped)
    }

    @Test
    fun `json form`() {
        val sample = BT + jsonInner + BT + "\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("get_category_stats", calls[0].name)
        val args = calls[0].arguments.orEmpty()
        assertTrue("expected deck+X in " + args, args.contains("deck"))
        assertTrue("expected X in " + args, args.contains("X"))
        assertEquals("", stripped)
    }

    @Test
    fun `multiple blocks`() {
        val b1 = BT + "function=get_study_summary>" + BT + "/function>" + BT + "\n"
        val b2 = BT + jsonInner + BT + "\n"
        val sample = "before\n" + b1 + "\n" + b2 + "after\n"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(2, calls.size)
        assertEquals("get_study_summary", calls[0].name)
        assertEquals("get_category_stats", calls[1].name)
        assertTrue(stripped.startsWith("before"))
        assertTrue(stripped.endsWith("after"))
    }

    @Test
    fun `surrounding prose is preserved`() {
        val b = BT + "function=get_study_summary>" + BT + "/function>" + BT
        val sample = "Here is the summary." + b + "\nHope that helps!"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(1, calls.size)
        assertEquals("Here is the summary.\nHope that helps!", stripped)
    }

    @Test
    fun `malformed unclosed block is left harmless`() {
        val sample = "before\n" + BT + "function=foo>bar"
        val (calls, stripped) = TextToolCalls.extract(sample)
        assertEquals(0, calls.size)
        assertEquals(sample, stripped)
    }

    @Test
    fun `content without tags is unchanged`() {
        val plain = "Just a plain answer with no tools here."
        val (calls, stripped) = TextToolCalls.extract(plain)
        assertEquals(emptyList<ChatJson.ToolCall>(), calls)
        assertEquals(plain, stripped)
    }
}
