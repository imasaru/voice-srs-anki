package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** Fake VoiceIo with scripted transcripts. */
private class FakeIo(private val transcripts: List<String?>) : VoiceIo {
    val spoken = mutableListOf<String>()
    val timeouts = mutableListOf<Long>()
    var listenCalls = 0
    override fun speak(text: String) { spoken.add(text) }
    override fun listen(timeoutMs: Long): String? {
        timeouts.add(timeoutMs)
        return if (listenCalls < transcripts.size) transcripts[listenCalls++] else null
    }
    override fun beep() {}
}

private class StoppedIo : VoiceIo {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) { spoken.add(text) }
    override fun listen(timeoutMs: Long): String? = null
    override fun beep() {}
    override fun stopped(): Boolean = true
}

class VoiceSelfGraderTest {
    @Test
    fun `stop during grading sends no ease`() {
        val io = StoppedIo()
        try {
            VoiceSelfGrader().grade(ctx(), io)
            fail("expected PauseRequested")
        } catch (_: PauseRequested) {
        }
        assertTrue("no reprompt after stop", io.spoken.none { it == RatingGuides.REPROMPT })
    }

    @Test
    fun `missed listens do not grade or advance`() {
        val io = FakeIo(listOf(null, null, null, "good"))
        try {
            VoiceSelfGrader().grade(ctx(), io)
            fail("a miss must not invent an ease")
        } catch (_: ListenGaveUp) {
        }
        assertEquals(3, io.listenCalls)
        assertEquals(2, io.spoken.count { it == RatingGuides.REPROMPT })
        assertFalse(io.spoken.contains("the answer"))
    }

    @Test
    fun `on-device and neural misses share one listen flow`() {
        for (engine in listOf("on-device", "neural")) {
            val io = FakeIo(List(5) { null })
            try {
                VoiceSelfGrader().grade(ctx().copy(listenAttempts = 3), io)
                fail("$engine invented an ease")
            } catch (_: ListenGaveUp) {
            }
            assertEquals(engine, 3, io.listenCalls)
            assertEquals(engine, 2, io.spoken.count { it == RatingGuides.REPROMPT })
        }
    }

    @Test
    fun `one listen attempt gives up without a reprompt or a grade`() {
        val io = FakeIo(listOf(null, "good"))
        try {
            VoiceSelfGrader().grade(ctx().copy(listenAttempts = 1), io)
            fail("a miss must not invent an ease")
        } catch (_: ListenGaveUp) {
        }
        assertEquals(1, io.listenCalls)
        assertFalse(io.spoken.contains(RatingGuides.REPROMPT))
    }

    private val card = AnkiCard("<p>Q</p>", "<p>A</p>", "VoiceSRS-Test", listOf(1, 2, 3, 4))
    private fun ctx() = GradeContext(card, "the answer")

    @Test
    fun `good maps to ease 3`() {
        assertEquals(3, VoiceSelfGrader().grade(ctx(), FakeIo(listOf("good"))))
    }

    @Test
    fun `grade listen uses the configured wait`() {
        val io = FakeIo(listOf("good"))
        val custom = GradeContext(card, "the answer", listenTimeoutMs = 15_000L)
        assertEquals(3, VoiceSelfGrader().grade(custom, io))
        assertEquals(listOf(15_000L), io.timeouts)
    }

    @Test
    fun `grade listen defaults to the grader wait`() {
        val io = FakeIo(listOf("again", "good"))
        assertEquals(1, VoiceSelfGrader().grade(ctx(), io))
        assertEquals(listOf(Grader.LISTEN_TIMEOUT_MS), io.timeouts)
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
    fun `unrecognized input uses every attempt then gives up without a grade`() {
        val io = FakeIo(listOf("blah blah", null, "sandwich"))
        try {
            VoiceSelfGrader().grade(ctx(), io)
            fail("unrecognized speech must not invent an ease")
        } catch (_: ListenGaveUp) {
        }
        assertTrue(io.spoken.contains(RatingGuides.LONG))
        assertEquals(2, io.spoken.count { it == RatingGuides.REPROMPT })
        assertEquals(3, io.listenCalls)
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
    fun `speed mode skips guides but still uses every listen attempt`() {
        val io = FakeIo(listOf("sandwich", null, null, "good"))
        val speed = GradeContext(card, "the answer", cardIndex = 0, speedMode = true, listenAttempts = 3)
        try {
            VoiceSelfGrader().grade(speed, io)
            fail("speed mode must not invent an ease")
        } catch (_: ListenGaveUp) {
        }
        assertTrue(io.spoken.isEmpty())
        assertEquals(3, io.listenCalls)
    }

    @Test
    fun `speed mode still accepts a grade before the attempt limit`() {
        val io = FakeIo(listOf(null, "good"))
        val speed = GradeContext(card, "the answer", speedMode = true, listenAttempts = 3)
        assertEquals(3, VoiceSelfGrader().grade(speed, io))
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
    fun `fuzzy grade gud is good`() {
        assertEquals(3, VoiceSelfGrader().grade(ctx(), FakeIo(listOf("gud"))))
    }

    @Test
    fun `san is good`() {
        assertEquals(3, VoiceSelfGrader().grade(ctx(), FakeIo(listOf("san"))))
    }

    @Test(expected = SkipRequested::class)
    fun `skip is not an ease`() {
        VoiceSelfGrader().grade(ctx(), FakeIo(listOf("skip this card")))
    }

    @Test(expected = PauseRequested::class)
    fun `pause is not an ease`() {
        VoiceSelfGrader().grade(ctx(), FakeIo(listOf("pause")))
    }

    @Test
    fun `buttons fallback applies to graded ease`() {
        val learning = card.copy(buttons = listOf(1, 2, 3))
        val g = VoiceSelfGrader()
        assertEquals(3, g.grade(GradeContext(learning, "ans"), FakeIo(listOf("easy"))))
    }

    @Test
    fun `claim present before grade returns tapped ease with no speech or listen`() {
        val io = FakeIo(listOf("easy"))
        val ctx = GradeContext(card, "the answer", claimedEase = { 2 })
        assertEquals(2, VoiceSelfGrader().grade(ctx, io))
        assertTrue(io.spoken.isEmpty())
        assertEquals(0, io.listenCalls)
    }

    @Test
    fun `claim during opening prompt returns tapped ease and does not listen`() {
        var claimed: Int? = null
        val io = object : VoiceIo {
            val spoken = mutableListOf<String>()
            var listenCalls = 0
            override fun speak(text: String) {
                spoken.add(text)
                claimed = 4
            }
            override fun listen(timeoutMs: Long): String? {
                listenCalls++
                return "again"
            }
            override fun beep() {}
        }
        val ctx = GradeContext(card, "the answer", claimedEase = { claimed })
        assertEquals(4, VoiceSelfGrader().grade(ctx, io))
        assertEquals(0, io.listenCalls)
        assertEquals(listOf(RatingGuides.LONG), io.spoken)
    }

    @Test
    fun `claim during reprompt returns tapped ease and does not listen again`() {
        var claimed: Int? = null
        var speaks = 0
        val io = object : VoiceIo {
            val spoken = mutableListOf<String>()
            var listenCalls = 0
            override fun speak(text: String) {
                speaks++
                spoken.add(text)
                if (speaks >= 2) claimed = 1
            }
            override fun listen(timeoutMs: Long): String? {
                listenCalls++
                return if (listenCalls == 1) "sandwich" else "easy"
            }
            override fun beep() {}
        }
        val ctx = GradeContext(card, "the answer", claimedEase = { claimed })
        assertEquals(1, VoiceSelfGrader().grade(ctx, io))
        assertEquals(1, io.listenCalls)
        assertTrue(io.spoken.contains(RatingGuides.REPROMPT))
    }

    @Test
    fun `claimed ease is mapped onto the buttons the card offers`() {
        val learning = card.copy(buttons = listOf(1, 2, 3))
        val io = FakeIo(listOf("again"))
        val ctx = GradeContext(learning, "ans", claimedEase = { 4 })
        assertEquals(3, VoiceSelfGrader().grade(ctx, io))
        assertEquals(0, io.listenCalls)
    }

    @Test
    fun `repeat resets the missed listen count`() {
        val io = FakeIo(listOf(null, null, "repeat", null, null, "good"))
        assertEquals(3, VoiceSelfGrader().grade(ctx().copy(listenAttempts = 3), io))
        assertEquals(6, io.listenCalls)
        assertEquals(1, io.spoken.count { it == "the answer" })
    }

    @Test
    fun `nine repeats give up`() {
        val io = FakeIo(List(9) { "repeat" } + "good")
        try {
            VoiceSelfGrader().grade(ctx(), io)
            fail("nine repeats must give up")
        } catch (_: ListenGaveUp) {
        }
        assertEquals(9, io.listenCalls)
        assertEquals(8, io.spoken.count { it == "the answer" })
    }
}
