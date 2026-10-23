package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoCardTest {
    @Test
    fun `Heard is first even when nothing was captured`() {
        val idle = InfoCard.compose(heard = "", status = "Reading the question", drilling = true)
        assertTrue(idle.startsWith(InfoCard.HEARD_EMPTY))
        assertTrue(idle.contains("Reading the question"))
        assertEquals(InfoCard.HEARD_EMPTY, idle.lines().first())
    }

    @Test
    fun `Heard line quotes the transcript and listening names Answer B`() {
        val text = InfoCard.compose("bee", "Listening for your answer", drilling = true)
        assertEquals("Heard: \"bee\"", text.lines().first())
        assertTrue(text.contains("Answer B"))
        assertTrue(text.contains(InfoCard.LISTEN_ANSWER))
        assertFalse(text.startsWith("Listening"))
    }

    @Test
    fun `Heard status lines are not repeated as a pipeline stage`() {
        val text = InfoCard.compose("hello there", "Heard: \"hello there\"", drilling = true)
        assertEquals("Heard: \"hello there\"", text)
        assertEquals(1, text.lines().size)
    }

    @Test
    fun `idle drill keeps the ready line without Heard`() {
        assertEquals("Ready", InfoCard.compose("", "Ready", drilling = false))
    }
}
