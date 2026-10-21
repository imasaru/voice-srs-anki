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
}
