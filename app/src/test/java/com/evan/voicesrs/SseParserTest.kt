package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun `chunks split mid token still assemble`() {
        val parser = SseParser()
        assertTrue(parser.push("data: {\"choices\":[{\"delta\":{\"content\":\"Hel").isEmpty())
        assertEquals(listOf("Hello"), parser.push("lo\"}}]}\n"))
        assertEquals("Hello", parser.rawText())
        assertEquals("Hello", parser.visibleText())
    }

    @Test
    fun `done stops later data`() {
        val parser = SseParser()
        parser.push("data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}\n")
        parser.push("data: [DONE]\n")
        assertTrue(parser.done)
        assertTrue(parser.push("data: {\"choices\":[{\"delta\":{\"content\":\"B\"}}]}\n").isEmpty())
        assertEquals("A", parser.rawText())
    }

    @Test
    fun `think blocks are stripped including ones split across chunks`() {
        val parser = SseParser()
        parser.push("data: {\"choices\":[{\"delta\":{\"content\":\"<thi\"}}]}\n")
        parser.push("data: {\"choices\":[{\"delta\":{\"content\":\"nk>secret</think>Answer\"}}]}\n")
        assertEquals("<think>secret</think>Answer", parser.rawText())
        assertEquals("Answer", parser.visibleText())
    }

    @Test
    fun `unclosed think drops the tail`() {
        assertEquals("Before", ThinkStrip.strip("Before <think>hidden"))
        assertEquals("", ThinkStrip.strip("<think>only"))
        assertEquals("Yes", ThinkStrip.strip("<think>nope</think>Yes"))
    }

    @Test
    fun `unclosed leading think is stripped`() {
        assertEquals("", ThinkStrip.strip("<think>never closed"))
        assertEquals("", ThinkStrip.strip("\n<think>\nsecret without end"))
        assertEquals("Answer", ThinkStrip.strip("<think>secret</think>\n\nAnswer"))
    }

    @Test
    fun `reasoning content then content keeps only the answer`() {
        val parser = SseParser()
        val reasoning = parser.push(
            "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hmm\"}}]}\n"
        )
        assertTrue(reasoning.isEmpty())
        assertTrue(parser.consumedReasoning())
        assertEquals("", parser.rawText())
        assertEquals("", parser.visibleText())
        val alias = parser.push(
            "data: {\"choices\":[{\"delta\":{\"reasoning\":\"more\"}}]}\n"
        )
        assertTrue(alias.isEmpty())
        assertTrue(parser.consumedReasoning())
        val answer = parser.push(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Answer\"}}]}\n"
        )
        assertEquals(listOf("Answer"), answer)
        assertEquals("Answer", parser.rawText())
        assertEquals("Answer", parser.visibleText())
        assertTrue(!parser.consumedReasoning())
    }

    @Test
    fun `keepalive chunk is ignored`() {
        val parser = SseParser()
        val ignored = parser.push(
            "data: {\"model\":\"keepalive\",\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n"
        )
        assertTrue(ignored.isEmpty())
        assertTrue(!parser.done)
        assertTrue(!parser.consumedReasoning())
        assertEquals("", parser.rawText())
        parser.push("data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n")
        assertEquals("Hi", parser.visibleText())
    }

    @Test
    fun `non stream reasoning content is not the answer`() {
        val body = "{" +
            "\"choices\":[{\"finish_reason\":\"stop\",\"message\":{" +
            "\"content\":\"okay\",\"reasoning_content\":\"because\"}}]}"
        val parsed = ChatJson.parse(body)
        assertEquals("okay", parsed?.content)
        assertEquals(true, parsed?.reasoning)
        assertEquals("stop", parsed?.finishReason)
        assertEquals("okay", ChatJson.messageContent(body))
        assertEquals("okay", AiMessages.require(parsed!!.content.orEmpty(), parsed.finishReason))
    }

    @Test
    fun `length with empty content is the budget error`() {
        val parser = SseParser()
        parser.push("data: {\"model\":\"keepalive\",\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n")
        parser.push("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"spin\"}}]}\n")
        parser.push("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n")
        assertEquals("", parser.visibleText())
        assertEquals("length", parser.finishReason)
        try {
            AiMessages.require(parser.rawText(), parser.finishReason)
            throw AssertionError("expected budget error")
        } catch (e: AiException) {
            assertEquals(
                "Model used its whole token budget on reasoning; try a :no-think model or raise max tokens",
                e.message
            )
        }
        val body = "{" +
            "\"choices\":[{\"finish_reason\":\"length\",\"message\":{" +
            "\"content\":\"\",\"reasoning_content\":\"spin\"}}]}"
        val parsed = ChatJson.parse(body)
        assertEquals("", parsed?.content)
        assertEquals(true, parsed?.reasoning)
        assertEquals("length", parsed?.finishReason)
        try {
            AiMessages.require("", "stop")
            throw AssertionError("expected no text")
        } catch (e: AiException) {
            assertEquals("Model returned no text", e.message)
        }
    }

    @Test
    fun `comments blank lines and crlf are ignored`() {
        val parser = SseParser()
        val pieces = parser.push(": ping\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"Ok\"}}]}\r\n")
        assertEquals(listOf("Ok"), pieces)
    }

    @Test
    fun `message content is accepted on a data line`() {
        val parser = SseParser()
        parser.push("data: {\"choices\":[{\"message\":{\"content\":\"Hi\"}}]}\n")
        assertEquals("Hi", parser.rawText())
    }

    @Test
    fun `non stream json body yields message content`() {
        assertEquals(
            "Hi",
            ChatJson.messageContent("{\"choices\":[{\"message\":{\"content\":\"Hi\"}}]}")
        )
    }

    @Test
    fun `reasoning only stream is empty visible text so the client can fall back`() {
        val parser = SseParser()
        parser.push("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hmm\"}}]}\n")
        parser.push("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n")
        parser.push("data: [DONE]\n")
        assertEquals("", parser.visibleText())
        assertEquals("", parser.rawText())
        assertTrue(parser.done)
        assertTrue(EmptyStream() is Exception)
    }
}
