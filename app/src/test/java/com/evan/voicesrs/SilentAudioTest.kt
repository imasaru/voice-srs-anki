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
        assertFalse(AudioGate.replayCurrentPrompt())
    }

    @Test
    fun `voice mode allows audio and a rating tap still interrupts tts`() {
        assertTrue(AudioGate.outputAllowed(silent = false))
        assertTrue(AudioGate.micAllowed(silent = false))
        assertTrue(AudioGate.earconAllowed(silent = false))
        assertFalse(AudioGate.abortInProgress(nowSilent = false))
        assertTrue(AudioGate.ratingTapInterruptsTts())
    }

    @Test
    fun `silent answering is immediate in both directions of the switch`() {
        val question = AnswerControls.of(
            silent = true,
            hasQuestion = true,
            answerShown = false,
            voiceRating = false
        )
        assertTrue(question.showAnswer)
        assertTrue(question.rating)

        val revealed = AnswerControls.of(
            silent = true,
            hasQuestion = true,
            answerShown = true,
            voiceRating = false
        )
        assertFalse(revealed.showAnswer)
        assertTrue(revealed.rating)

        val voiceQuestion = AnswerControls.of(
            silent = false,
            hasQuestion = true,
            answerShown = false,
            voiceRating = false
        )
        assertFalse(voiceQuestion.showAnswer)
        assertFalse(voiceQuestion.rating)

        val voiceAnswer = AnswerControls.of(
            silent = false,
            hasQuestion = true,
            answerShown = true,
            voiceRating = true
        )
        assertFalse(voiceAnswer.showAnswer)
        assertTrue(voiceAnswer.rating)
    }

    @Test
    fun `an instant silent grade is claimed before the answer is revealed`() {
        val gate = GradeGate().apply { newCard() }
        assertFalse(gate.tryClaim(3))
        assertTrue(gate.tryClaimInstant(3))
        assertEquals(3, gate.ease())
        assertFalse(gate.tryClaimInstant(1))
    }

    @Test
    fun `turning voice back on does not replay the current prompt`() {
        assertFalse(AudioGate.replayCurrentPrompt())
        assertTrue(AudioGate.outputAllowed(silent = false))
        assertTrue(AudioGate.abortInProgress(nowSilent = true))
    }
}
