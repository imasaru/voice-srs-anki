package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentAudioTest {
    @Test
    fun `silent mode blocks mic tts and earcons and aborts a readout`() {
        assertFalse(AudioGate.outputAllowed(silent = true))
        assertFalse(AudioGate.micAllowed(silent = true))
        assertFalse(AudioGate.earconAllowed(silent = true))
        assertTrue(AudioGate.abortInProgress(nowSilent = true))
    }

    @Test
    fun `voice mode allows audio and a tap still cuts tts and listening`() {
        assertTrue(AudioGate.outputAllowed(silent = false))
        assertTrue(AudioGate.micAllowed(silent = false))
        assertTrue(AudioGate.earconAllowed(silent = false))
        assertFalse(AudioGate.abortInProgress(nowSilent = false))
        assertTrue(AudioGate.showAnswerTapInterruptsAudio())
        assertTrue(AudioGate.ratingTapInterruptsTts())
    }

    @Test
    fun `button state follows the card and ignores silent toggling`() {
        for (silent in listOf(false, true)) {
            val question = AnswerControls.of(
                hasQuestion = true,
                answerShown = false,
                silent = silent
            )
            assertTrue(question.showAnswer)
            assertFalse(question.rating)

            val revealed = AnswerControls.of(
                hasQuestion = true,
                answerShown = true,
                silent = silent
            )
            assertFalse(revealed.showAnswer)
            assertTrue(revealed.rating)
        }
        val voiceQuestion = AnswerControls.of(hasQuestion = true, answerShown = false, silent = false)
        val silentQuestion = AnswerControls.of(hasQuestion = true, answerShown = false, silent = true)
        assertEquals(voiceQuestion, silentQuestion)
        val voiceAnswer = AnswerControls.of(hasQuestion = true, answerShown = true, silent = false)
        val silentAnswer = AnswerControls.of(hasQuestion = true, answerShown = true, silent = true)
        assertEquals(voiceAnswer, silentAnswer)

        val idle = AnswerControls.of(hasQuestion = false, answerShown = false, silent = true)
        val idleVoice = AnswerControls.of(hasQuestion = false, answerShown = true, silent = false)
        assertFalse(idle.showAnswer)
        assertFalse(idle.rating)
        assertEquals(idle, idleVoice)
    }

    @Test
    fun `chat row buttons stay put when silent toggles`() {
        val question = DrillUiState(
            drillRunning = true,
            frontHtml = "What is Delta?",
            showBack = false,
            silentSession = false
        )
        val loud = live(ChatFeed.rows(emptyList(), question))
        val quiet = live(ChatFeed.rows(emptyList(), question.copy(silentSession = true)))
        assertEquals(loud.showAnswer, quiet.showAnswer)
        assertEquals(loud.rating, quiet.rating)
        assertTrue(loud.showAnswer)
        assertFalse(loud.rating)

        val answer = question.copy(showBack = true, backHtml = "ACID tables", silentSession = true)
        val silentShown = live(ChatFeed.rows(emptyList(), answer))
        val voiceShown = live(ChatFeed.rows(emptyList(), answer.copy(silentSession = false)))
        assertFalse(silentShown.showAnswer)
        assertTrue(silentShown.rating)
        assertEquals(silentShown.showAnswer, voiceShown.showAnswer)
        assertEquals(silentShown.rating, voiceShown.rating)
    }

    @Test
    fun `a rating before the answer is ignored in both modes`() {
        val gate = GradeGate().apply { newCard() }
        assertFalse(gate.tryClaim(3))
        assertFalse(gate.isClaimed())
        gate.reveal()
        assertTrue(gate.tryClaim(3))
        assertEquals(3, gate.ease())
    }

    @Test
    fun `silent off mid card resumes the question or the answer`() {
        assertEquals(
            CardResume.READ_QUESTION,
            AudioGate.resume(nowSilent = false, cardUp = true, answerShown = false)
        )
        assertEquals(
            CardResume.READ_ANSWER,
            AudioGate.resume(nowSilent = false, cardUp = true, answerShown = true)
        )
        assertEquals(
            CardResume.HOLD,
            AudioGate.resume(nowSilent = true, cardUp = true, answerShown = false)
        )
        assertEquals(
            CardResume.HOLD,
            AudioGate.resume(nowSilent = true, cardUp = true, answerShown = true)
        )
        assertEquals(
            CardResume.HOLD,
            AudioGate.resume(nowSilent = false, cardUp = false, answerShown = false)
        )
        assertEquals(
            CardResume.HOLD,
            AudioGate.resume(nowSilent = false, cardUp = false, answerShown = true)
        )
    }

    @Test
    fun `turning silent on holds the open card instead of advancing it`() {
        val onQuestion = AudioGate.resume(nowSilent = true, cardUp = true, answerShown = false)
        val onAnswer = AudioGate.resume(nowSilent = true, cardUp = true, answerShown = true)
        assertEquals(CardResume.HOLD, onQuestion)
        assertEquals(CardResume.HOLD, onAnswer)
        assertTrue(AudioGate.abortInProgress(nowSilent = true))
        assertFalse(AudioGate.micAllowed(silent = true))
        assertFalse(AudioGate.outputAllowed(silent = true))
    }

    private fun live(rows: List<ChatRow>): ChatRow.Live = rows.last() as ChatRow.Live
}
