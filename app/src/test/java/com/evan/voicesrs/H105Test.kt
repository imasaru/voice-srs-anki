package com.evan.voicesrs

import com.evan.voicesrs.MicRoute.AudioDev
import com.evan.voicesrs.MicRoute.AudioDevType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** h105: deadline keeps words, answer silence, headset-mic route, beeps. */
class H105Test {

    private val builtin = AudioDev(1, AudioDevType.BUILTIN_MIC)
    private val sco = AudioDev(2, AudioDevType.BLUETOOTH_SCO, "SCO")
    private val ble = AudioDev(3, AudioDevType.BLE_HEADSET, "BLE")

    @Test
    fun `deadlineText keeps last non-blank partial`() {
        val text = ListenWindow.deadlineText(
            emptyList(),
            listOf("I'm", "I'm curious", "I'm frequently frequently", "")
        )
        assertEquals("I'm frequently frequently", text)
    }

    @Test
    fun `deadlineText non-blank final wins over partials`() {
        assertEquals("final", ListenWindow.deadlineText(listOf("final"), listOf("p1", "p2")))
    }

    @Test
    fun `deadlineText all blank is null`() {
        assertNull(ListenWindow.deadlineText(emptyList(), listOf("", "  ")))
    }

    @Test
    fun `stop grace ms is about a second`() {
        val ms = ListenWindow.STOP_GRACE_MS
        assertTrue("STOP_GRACE_MS=$ms", ms in 800..1200)
    }

    @Test
    fun `answer silence is four seconds and longer than default`() {
        val ms = ListenWindow.ANSWER_SILENCE_MS
        assertTrue("ANSWER_SILENCE_MS=$ms", ms in 3500..4500)
        assertTrue(
            "ANSWER_SILENCE_MS=$ms DEFAULT=${ListenWindow.DEFAULT_SILENCE_MS}",
            ms > ListenWindow.DEFAULT_SILENCE_MS
        )
    }

    @Test
    fun `listen cue tones`() {
        assertTrue(ListenCue.playStartTone(firstListen = true, restartQuiet = false))
        assertTrue(!ListenCue.playStartTone(firstListen = false, restartQuiet = true))
        assertTrue(ListenCue.playEndTone())
        val tone = ListenCue.TONE_MS
        assertTrue("TONE_MS=$tone", tone in 200..300)
    }

    @Test
    fun `listenInput uses headset mic regardless of media channel`() {
        val on = AudioRouteDecision.listenInput(
            useHeadsetMic = true,
            inputs = listOf(builtin, sco),
            channel = Prefs.READ_ALOUD_MEDIA
        )
        assertEquals(AudioDevType.BLUETOOTH_SCO, on?.type)

        val onBle = AudioRouteDecision.listenInput(
            useHeadsetMic = true,
            inputs = listOf(builtin, ble),
            channel = Prefs.READ_ALOUD_MEDIA
        )
        assertEquals(AudioDevType.BLE_HEADSET, onBle?.type)
    }

    @Test
    fun `listenInput off in media is null`() {
        assertNull(
            AudioRouteDecision.listenInput(
                useHeadsetMic = false,
                inputs = listOf(builtin, sco),
                channel = Prefs.READ_ALOUD_MEDIA
            )
        )
    }

    @Test
    fun `listenInput no headset input is null`() {
        assertNull(
            AudioRouteDecision.listenInput(
                useHeadsetMic = true,
                inputs = listOf(builtin),
                channel = Prefs.READ_ALOUD_MEDIA
            )
        )
    }

    @Test
    fun `use headset mic defaults on`() {
        assertTrue(AnkiConfig().useHeadsetMic)
    }

    @Test
    fun `mic route timing delay`() {
        assertEquals(500L, MicRouteTiming.delayMs(1000L, 1500L))
    }

    @Test
    fun `mic route timing never negative`() {
        assertTrue(MicRouteTiming.delayMs(2000L, 1500L) >= 0L)
    }

    @Test
    fun `mic route timing timeout is one to two seconds`() {
        val t = MicRouteTiming.TIMEOUT_MS
        assertTrue("TIMEOUT_MS=$t", t in 1000..2000)
    }
}
