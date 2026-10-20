package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** Fake VoiceIo with scripted transcripts. */
private class FakeIo(private val transcripts: List<String?>) : VoiceIo {
    val spoken = mutableListOf<String>()
    var listenCalls = 0
    override fun speak(text: String) { spoken.add(text) }
    override fun listen(timeoutMs: Long): String? =
        if (listenCalls < transcripts.size) transcripts[listenCalls++] else null
    override fun beep() {}
}

class VoiceSelfGraderTest {
    private val card = AnkiCard("<p>Q</p>", "<p>A</p>", "VoiceSRS-Test", listOf(1, 2, 3, 4))
    private fun ctx() = GradeContext(card, "the answer")

    @Test
    fun `good maps to ease 3`() {
        assertEquals(3, VoiceSelfGrader().grade(ctx(), FakeIo(listOf("good"))))
    }

    @Test
    fun `each grade word maps correctly`() {
        val g = VoiceSelfGrader()
        assertEquals(1, g.grade(ctx(), FakeIo(listOf("again"))))
        assertEquals(2, g.grade(ctx(), FakeIo(listOf("that was hard"))))
        assertEquals(4, g.grade(ctx(), FakeIo(listOf("easy"))))
    }

    @Test
    fun `repeat re-speaks the answer then grades`() {
        val io = FakeIo(listOf("repeat", "good"))
        assertEquals(3, VoiceSelfGrader().grade(ctx(), io))
        assertTrue(io.spoken.contains("the answer"))
    }

    @Test
    fun `unrecognized input reprompts once then defaults to hard`() {
        val io = FakeIo(listOf("blah blah", null))
        assertEquals(2, VoiceSelfGrader().grade(ctx(), io))
        assertTrue(io.spoken.contains(RatingGuides.LONG))
        assertEquals(1, io.spoken.count { it == RatingGuides.REPROMPT })
        assertEquals(2, io.listenCalls)
    }

    @Test
    fun `a grade on the first listen does not reprompt`() {
        val io = FakeIo(listOf("good"))
        assertEquals(3, VoiceSelfGrader().grade(ctx(), io))
        assertTrue(io.spoken.contains(RatingGuides.LONG))
        assertFalse(io.spoken.contains(RatingGuides.REPROMPT))
        assertEquals(1, io.listenCalls)
    }

    @Test
    fun `speed mode skips guides but still retries a miss`() {
        val io = FakeIo(listOf("nope", null))
        val speed = GradeContext(card, "the answer", cardIndex = 0, speedMode = true)
        assertEquals(2, VoiceSelfGrader().grade(speed, io))
        assertTrue(io.spoken.isEmpty())
        assertEquals(2, io.listenCalls)
    }

    @Test
    fun `fourth card uses the short prompt`() {
        val io = FakeIo(listOf("easy"))
        val later = GradeContext(card, "ans", cardIndex = 3, speedMode = false)
        assertEquals(4, VoiceSelfGrader().grade(later, io))
        assertTrue(io.spoken.contains(RatingGuides.SHORT))
        assertFalse(io.spoken.contains(RatingGuides.LONG))
    }

    @Test
    fun `buttons fallback applies to graded ease`() {
        val learning = card.copy(buttons = listOf(1, 2, 3))
        val g = VoiceSelfGrader()
        assertEquals(3, g.grade(GradeContext(learning, "ans"), FakeIo(listOf("easy"))))
    }
}
