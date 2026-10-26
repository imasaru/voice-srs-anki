package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoCardTest {
    @Test
    fun `Heard is hidden while TTS reads the question`() {
        val idle = InfoCard.compose(heard = "", status = "Reading the question", drilling = true)
        assertFalse(idle.contains(InfoCard.HEARD_EMPTY))
        assertEquals("Reading the question", idle)
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

    @Test
    fun `Heard is hidden in silent mode even while listening`() {
        val text = InfoCard.compose("bee", "Listening for your answer", drilling = true, silent = true)
        assertFalse(text.contains("Heard"))
        assertEquals(InfoCard.LISTEN_ANSWER, text)
    }

    @Test
    fun `Heard is hidden while paused`() {
        val text = InfoCard.compose("bee", "Paused", drilling = true)
        assertFalse(text.contains("Heard"))
        assertEquals("Paused", text)
    }

    @Test
    fun `Heard shows when the mic is open even on the rating stage`() {
        val text = InfoCard.compose("bee", "Rate it", drilling = true, micOpen = true)
        assertEquals("Heard: \"bee\"", text.lines().first())
        assertEquals("Rate it", text.lines().last())
    }
}
