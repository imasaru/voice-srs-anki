package com.evan.voicesrs

import com.evan.voicesrs.FocusPolicy.Action
import com.evan.voicesrs.FocusPolicy.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REPRO for h90: before FocusPolicy exists this file fails to compile, which
 * counts as the failure — the drill never requested audio focus, so an incoming
 * call / alarm / other app could talk over it and the listen was graded anyway.
 */
class FocusPolicyTest {

    private fun state() = FocusPolicy.State()

    @Test
    fun a_transient_loss_while_speech_pauses_speech() {
        val s = state()
        val a = FocusPolicy.onChange(FocusPolicy.LOSS_TRANSIENT, Phase.SPEAKING, s, 0)
        assertEquals(Action.PAUSE_SPEAK, a)
        assertTrue(s.transientLoss)
    }

    @Test
    fun b_can_duck_while_listening_is_ignored() {
        val s = state()
        val a = FocusPolicy.onChange(FocusPolicy.LOSS_TRANSIENT_CAN_DUCK, Phase.LISTENING, s, 0)
        assertEquals(Action.NONE, a)
        assertFalse(s.transientLoss)
    }

    @Test
    fun h97_recognizer_transient_focus_grab_does_not_abort_or_drop_listen() {
        // Logcat 10-30 12:04:34: startListening -> onAudioFocusChange(-2) from speech service -> (1).
        val s = state()
        assertEquals(Action.NONE, FocusPolicy.onChange(FocusPolicy.LOSS_TRANSIENT, Phase.LISTENING, s, 100))
        assertEquals(Action.NONE, FocusPolicy.onChange(FocusPolicy.GAIN, Phase.LISTENING, s, 220))
        assertFalse(s.transientLoss)
        assertEquals(0L, s.lostAt)
        assertTrue(FocusPolicy.shouldGrade(s.lostAt, 500))
    }

    @Test
    fun h97_permanent_loss_while_listening_still_aborts() {
        assertEquals(Action.ABORT_LISTEN, FocusPolicy.onChange(FocusPolicy.LOSS, Phase.LISTENING, state(), 100))
    }

    @Test
    fun c_gain_after_a_transient_loss_resumes() {
        val s = state()
        FocusPolicy.onChange(FocusPolicy.LOSS_TRANSIENT, Phase.SPEAKING, s, 100)
        val a = FocusPolicy.onChange(FocusPolicy.GAIN, Phase.SPEAKING, s, 200)
        assertEquals(Action.RESUME, a)
        assertEquals(1000L, FocusPolicy.RESUME_DELAY_MS)
    }

    @Test
    fun c_gain_after_transient_loss_while_listening_is_a_noop() {
        val s = state()
        FocusPolicy.onChange(FocusPolicy.LOSS_TRANSIENT, Phase.LISTENING, s, 100)
        val a = FocusPolicy.onChange(FocusPolicy.GAIN, Phase.LISTENING, s, 200)
        assertEquals(Action.NONE, a)
    }

    @Test
    fun d_gain_with_no_loss_does_nothing() {
        val s = state()
        val a = FocusPolicy.onChange(FocusPolicy.GAIN, Phase.SPEAKING, s, 0)
        assertEquals(Action.NONE, a)
    }

    @Test
    fun e_permanent_loss_never_auto_resumes() {
        val s = state()
        FocusPolicy.onChange(FocusPolicy.LOSS, Phase.SPEAKING, s, 100)
        assertEquals(Action.PAUSE_SPEAK, FocusPolicy.onChange(FocusPolicy.LOSS, Phase.SPEAKING, s, 100))
        assertEquals(Action.NONE, FocusPolicy.onChange(FocusPolicy.GAIN, Phase.SPEAKING, s, 200))
        assertFalse(s.transientLoss)
        assertTrue(s.lostForGood)

        val s2 = state()
        FocusPolicy.onChange(FocusPolicy.LOSS_TRANSIENT, Phase.LISTENING, s2, 100)
        FocusPolicy.onChange(FocusPolicy.LOSS, Phase.LISTENING, s2, 150)
        assertEquals(Action.NONE, FocusPolicy.onChange(FocusPolicy.GAIN, Phase.LISTENING, s2, 200))
    }

    @Test
    fun f_late_result_after_an_abort_is_not_graded() {
        assertFalse(FocusPolicy.shouldGrade(1000, 1500))
        assertTrue(FocusPolicy.shouldGrade(0, 1500))
        assertTrue(FocusPolicy.shouldGrade(2000, 1500))
    }
}
