package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandingInsightsTest {

    private val fixture = javaClass.classLoader!!.getResource("insights/thread-history.json")!!.readText()

    @Test
    fun `fixture contains synthetic IDs and fictional timestamps only`() {
        val root = JSONObject(fixture).getJSONArray("threads")
        var messages = 0
        for (i in 0 until root.length()) {
            val thread = root.getJSONObject(i)
            assertTrue(thread.getLong("cardId") in 2001L..2016L)
            assertTrue(thread.getString("contentHash").startsWith("synthetic-"))
            val rows = thread.getJSONArray("messages")
            for (j in 0 until rows.length()) {
                assertTrue(rows.getJSONObject(j).getLong("at") in 946684800000L..946685000000L)
                messages++
            }
        }
        assertEquals(60, messages)
    }

    private fun card(id: Long) = UnderstandingInsights.insights(fixture).associateBy { it.cardId }[id]!!

    @Test
    fun `parse merges threads per card sorted by at, first-seen card order`() {
        val parsed = UnderstandingInsights.parse(fixture)
        assertEquals(16, parsed.size)
        assertEquals(18, JSONObject(fixture).getJSONArray("threads").length())
        // first thread defines first-seen order
        assertEquals(2001L, parsed.keys.first())

        val c103 = parsed[2001]!!
        // 4 from the first thread + 6 from the second thread of this card
        assertEquals(10, c103.size)
        // merged and sorted by `at`, so the second-thread user lands
        // before its assistant even though the JSON listed them out of order
        val at = c103.map { it.at }
        assertEquals(at, at.sorted())
        assertTrue(at.indexOf(946684960400L) < at.indexOf(946684960500L))
    }

    @Test
    fun `total asked equals user messages minus bookmark entries`() {
        val root = JSONObject(fixture)
        var userMessages = 0
        var bookmarks = 0
        val threads = root.getJSONArray("threads")
        for (i in 0 until threads.length()) {
            val msgs = threads.getJSONObject(i).getJSONArray("messages")
            for (j in 0 until msgs.length()) {
                val m = msgs.getJSONObject(j)
                if (m.getString("role") == "user") {
                    userMessages++
                    if (m.getString("text").startsWith(UnderstandingInsights.BOOKMARK)) bookmarks++
                }
            }
        }

        val insights = UnderstandingInsights.insights(fixture)
        assertEquals(16, insights.size)
        val totalAsked = insights.sumOf { it.asked.size }
        assertEquals(userMessages - bookmarks, totalAsked)
    }

    @Test
    fun `bookmark marks card and is excluded from asked`() {
        val insights = card(2002)
        assertTrue(insights.bookmarked)
        assertTrue(insights.asked.all { !it.text.startsWith(UnderstandingInsights.BOOKMARK) })

        assertFalse(card(2004).bookmarked)
    }

    @Test
    fun `classify maps synthetic utterances to question types`() {
        assertEquals(QuestionType.ANALOGY, UnderstandingInsights.classify("Invent an analogy with a llama and a triangle"))
        assertEquals(QuestionType.DEFINITION, UnderstandingInsights.classify("what is a synthetic triangle"))
        assertEquals(QuestionType.DEFINITION, UnderstandingInsights.classify("What is a shape?"))
        assertEquals(QuestionType.WHY, UnderstandingInsights.classify("why does this invented triangle have three sides"))
        assertEquals(QuestionType.ANSWER_ATTEMPT, UnderstandingInsights.classify("I say a"))
        assertEquals(QuestionType.VISUALIZE, UnderstandingInsights.classify("Help visualize a synthetic square"))
        assertEquals(QuestionType.COMPARE, UnderstandingInsights.classify("Compare circles vs squares"))
        assertEquals(QuestionType.RECALL_CONFUSION, UnderstandingInsights.classify("I remember a synthetic square but not its sides"))
        assertEquals(QuestionType.REPORT_REQUEST, UnderstandingInsights.classify("Write today's study report for synthetic data"))
        assertEquals(QuestionType.EXPLAIN, UnderstandingInsights.classify("please explain an invented shape"))
        assertEquals(QuestionType.OTHER, UnderstandingInsights.classify("Try a different synthetic card"))
    }

    @Test
    fun `concept takes rest of Core concept line then first sentence then empty`() {
        assertEquals(
            "Synthetic shape 5",
            card(2005).concept,
        )

        val synth = listOf(ThreadMsg("assistant", "Idempotence means an operation yields the same result. More text.", 0))
        assertEquals("Idempotence means an operation yields the same result", UnderstandingInsights.concept(synth))

        val noAssistant = listOf(ThreadMsg("user", "hi", 0))
        assertEquals("", UnderstandingInsights.concept(noAssistant))
    }

    @Test
    fun `mainType is the most common question type and catches an in-card report request`() {
        assertEquals(QuestionType.RECALL_CONFUSION, card(2001).mainType)

        val c131 = card(2003)
        assertEquals(2, c131.asked.size)
        assertEquals(QuestionType.REPORT_REQUEST, c131.asked.first().type)
    }

    @Test
    fun `empty or malformed input yields no insights`() {
        assertTrue(UnderstandingInsights.insights("{\"threads\":[]}").isEmpty())
        assertTrue(UnderstandingInsights.insights("not json").isEmpty())
    }

    @Test
    fun badThreadIsSkippedNotWholeHistory() {
        val json = "{\"threads\":[{\"noteId\":1},{\"cardId\":7,\"messages\":[{\"role\":\"user\",\"text\":\"what is acid\",\"at\":5},\"junk\"]}]}"
        val out = UnderstandingInsights.insights(json)
        assertEquals(1, out.size)
        assertEquals(7L, out[0].cardId)
        assertEquals(QuestionType.DEFINITION, out[0].asked.single().type)
    }
}
