package com.evan.voicesrs

import com.evan.voicesrs.MicRoute.AudioDevType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REPRO + coverage for the read-aloud channel preference (h86 / btvol).
 *
 * MEDIA (default) must keep classic Bluetooth SCO off the communication link so
 * EdgeSpeaker's media playback reaches the A2DP/AVRCP volume; CALL keeps the
 * historic h76 behaviour (SCO stays the communication device).
 */
class ReadAloudChannelTest {

    private fun dev(id: Int, type: AudioDevType, name: String = "") =
        MicRoute.AudioDev(id, type, name)

    @Test
    fun `a media - classic BT A2DP plus SCO never sets a communication device`() {
        // REPRO: A2DP output + SCO mic present. In MEDIA the SCO mic must not be
        // chosen and no communication device is set.
        val out = listOf(
            dev(1, AudioDevType.BLUETOOTH_A2DP, "A2DP"),
            dev(2, AudioDevType.BLUETOOTH_SCO, "SCO"),
        )
        val scoSco = dev(3, AudioDevType.BLUETOOTH_SCO, "SCO Mic")
        val ins = listOf(scoSco)

        val r = AudioRouteDecision.decide(null, out, ins)

        assertFalse(r.useCommunicationDevice)
        assertTrue(r.chosen?.type != AudioDevType.BLUETOOTH_SCO)
        assertEquals(
            AudioRouteDecision.CommAction.CLEAR,
            AudioRouteDecision.commAction(r.useCommunicationDevice, 31),
        )
    }

    @Test
    fun `b media - phone mic is chosen when BT SCO mic is present`() {
        val out = listOf(
            dev(1, AudioDevType.BLUETOOTH_A2DP, "A2DP"),
            dev(2, AudioDevType.BLUETOOTH_SCO, "SCO"),
        )
        val ins = listOf(
            dev(4, AudioDevType.BUILTIN_MIC, "Phone Mic"),
            dev(3, AudioDevType.BLUETOOTH_SCO, "SCO Mic"),
        )

        val r = AudioRouteDecision.decide(null, out, ins)

        assertEquals(4, r.chosen?.id)
        assertFalse(r.useCommunicationDevice)
    }

    @Test
    fun `c call - classic BT keeps the SCO communication device`() {
        val out = listOf(
            dev(1, AudioDevType.BLUETOOTH_A2DP, "A2DP"),
            dev(2, AudioDevType.BLUETOOTH_SCO, "SCO"),
        )
        val scoSco = dev(3, AudioDevType.BLUETOOTH_SCO, "SCO Mic")
        val ins = listOf(scoSco)

        val r = AudioRouteDecision.decide(null, out, ins, channel = Prefs.READ_ALOUD_CALL)

        assertEquals(3, r.chosen?.id)
        assertTrue(r.useCommunicationDevice)
        assertEquals(
            AudioRouteDecision.CommAction.SET,
            AudioRouteDecision.commAction(r.useCommunicationDevice, 31),
        )
    }

    @Test
    fun `d media keeps BLE wired and USB headsets`() {
        for (type in listOf(
            AudioDevType.BLE_HEADSET,
            AudioDevType.WIRED_HEADSET,
            AudioDevType.USB_HEADSET,
        )) {
            val out = listOf(dev(1, type, "Dev"))
            val mic = dev(2, type, "Dev Mic")

            val rMedia = AudioRouteDecision.decide(null, out, listOf(mic))
            assertTrue("media $type", rMedia.useCommunicationDevice)

            val rCall = AudioRouteDecision.decide(null, out, listOf(mic), channel = Prefs.READ_ALOUD_CALL)
            assertTrue("call $type", rCall.useCommunicationDevice)
        }
    }

    @Test
    fun `e media comm output never A2DP or SCO`() {
        assertFalse("A2DP/MEDIA", AudioRouteDecision.isCommunicationOutput(AudioDevType.BLUETOOTH_A2DP, Prefs.READ_ALOUD_MEDIA))
        assertFalse("SCO/MEDIA", AudioRouteDecision.isCommunicationOutput(AudioDevType.BLUETOOTH_SCO, Prefs.READ_ALOUD_MEDIA))
        assertTrue("BLE_HEADSET/MEDIA", AudioRouteDecision.isCommunicationOutput(AudioDevType.BLE_HEADSET, Prefs.READ_ALOUD_MEDIA))
        assertTrue("A2DP/CALL", AudioRouteDecision.isCommunicationOutput(AudioDevType.BLUETOOTH_A2DP, Prefs.READ_ALOUD_CALL))
        assertTrue("SCO/CALL", AudioRouteDecision.isCommunicationOutput(AudioDevType.BLUETOOTH_SCO, Prefs.READ_ALOUD_CALL))
    }

    @Test
    fun `f pref default is MEDIA`() {
        val cfg = AnkiConfig(url = "http://anki.example.invalid", apiKey = "k", deck = "d")
        assertEquals(Prefs.READ_ALOUD_MEDIA, cfg.readAloudChannel)
    }
}
