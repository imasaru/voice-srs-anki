package com.evan.voicesrs

import com.evan.voicesrs.MicRoute
import com.evan.voicesrs.MicRoute.AudioDevType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteTest {

    @Test
    fun `builtin mic maps to BUILTIN_MIC`() {
        assertEquals(AudioDevType.BUILTIN_MIC, MicRoute.typeOf(15))
    }

    @Test
    fun `builtin speaker maps to BUILTIN_SPEAKER`() {
        assertEquals(AudioDevType.BUILTIN_SPEAKER, MicRoute.typeOf(2))
    }

    @Test
    fun `builtin earpiece maps to BUILTIN_EARPIECE`() {
        assertEquals(AudioDevType.BUILTIN_EARPIECE, MicRoute.typeOf(1))
    }

    @Test
    fun `wired headset maps to WIRED_HEADSET`() {
        assertEquals(AudioDevType.WIRED_HEADSET, MicRoute.typeOf(3))
    }

    @Test
    fun `wired headphones maps to WIRED_HEADPHONES`() {
        assertEquals(AudioDevType.WIRED_HEADPHONES, MicRoute.typeOf(4))
    }

    @Test
    fun `bluetooth SCO maps to BLUETOOTH_SCO`() {
        assertEquals(AudioDevType.BLUETOOTH_SCO, MicRoute.typeOf(7))
    }

    @Test
    fun `bluetooth A2DP maps to BLUETOOTH_A2DP`() {
        assertEquals(AudioDevType.BLUETOOTH_A2DP, MicRoute.typeOf(8))
    }

    @Test
    fun `usb headset maps to USB_HEADSET`() {
        assertEquals(AudioDevType.USB_HEADSET, MicRoute.typeOf(22))
    }

    @Test
    fun `ble headset maps to BLE_HEADSET`() {
        assertEquals(AudioDevType.BLE_HEADSET, MicRoute.typeOf(26))
    }

    @Test
    fun `ble speaker maps to BLE_SPEAKER`() {
        assertEquals(AudioDevType.BLE_SPEAKER, MicRoute.typeOf(27))
    }

    @Test
    fun `unknown type falls back to OTHER`() {
        assertEquals(AudioDevType.OTHER, MicRoute.typeOf(999))
        assertEquals(AudioDevType.OTHER, MicRoute.typeOf(0))
    }

    // --- AudioRouteDecision -------------------------------------------------

    private fun dev(id: Int, type: AudioDevType, name: String = "") =
        MicRoute.AudioDev(id, type, name)

    @Test
    fun `speaker to BT headset switches and uses communication device`() {
        // The BT headset is now connected: it appears as a higher-priority
        // output than the built-in speaker, so pickInput routes to its mic.
        val out = listOf(
            dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"),
            dev(2, AudioDevType.BLUETOOTH_SCO, "BT Head"),
        )
        val btMic = dev(3, AudioDevType.BLUETOOTH_SCO, "BT Mic")
        val ins = listOf(btMic)

        val r = AudioRouteDecision.decide(previous = null, outputs = out, inputs = ins)

        assertEquals(btMic, r.chosen)
        assertTrue(r.changed)
        assertTrue(r.useCommunicationDevice)
    }

    @Test
    fun `BT headset back to built-in mic switches and drops communication device`() {
        val out = listOf(dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"))
        val builtInMic = dev(3, AudioDevType.BUILTIN_MIC, "Mic")
        val ins = listOf(builtInMic)
        val previous = dev(2, AudioDevType.BLUETOOTH_SCO, "BT Mic")

        val r = AudioRouteDecision.decide(previous = previous, outputs = out, inputs = ins)

        assertEquals(builtInMic, r.chosen)
        assertTrue(r.changed)
        assertFalse(r.useCommunicationDevice)
    }

    @Test
    fun `already on the right device does not change`() {
        val out = listOf(dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"))
        val builtInMic = dev(3, AudioDevType.BUILTIN_MIC, "Mic")
        val ins = listOf(builtInMic)
        val previous = dev(3, AudioDevType.BUILTIN_MIC, "Mic")

        val r = AudioRouteDecision.decide(previous = previous, outputs = out, inputs = ins)

        assertNull(r.chosen)
        assertFalse(r.changed)
        assertFalse(r.useCommunicationDevice)
    }

    @Test
    fun `wired and USB headset mics count as communication devices`() {
        val out = listOf(dev(1, AudioDevType.WIRED_HEADSET, "Headset"))
        val wiredMic = dev(2, AudioDevType.WIRED_HEADSET, "Headset Mic")
        val rWired = AudioRouteDecision.decide(null, out, listOf(wiredMic))
        assertTrue(rWired.useCommunicationDevice)

        val outUsb = listOf(dev(1, AudioDevType.USB_HEADSET, "USB"))
        val usbMic = dev(2, AudioDevType.USB_HEADSET, "USB Mic")
        val rUsb = AudioRouteDecision.decide(null, outUsb, listOf(usbMic))
        assertTrue(rUsb.useCommunicationDevice)
    }

    @Test
    fun `no suitable input does not change`() {
        val out = listOf(dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"))
        val r = AudioRouteDecision.decide(previous = null, outputs = out, inputs = emptyList())
        assertNull(r.chosen)
        assertFalse(r.changed)
    }

    // --- commAction (API guard) ---------------------------------------------

    @Test
    fun `commAction is NONE below API 31 regardless of preference`() {
        for (sdk in listOf(26, 30)) {
            assertEquals(
                AudioRouteDecision.CommAction.NONE,
                AudioRouteDecision.commAction(useCommunicationDevice = true, sdkInt = sdk),
            )
            assertEquals(
                AudioRouteDecision.CommAction.NONE,
                AudioRouteDecision.commAction(useCommunicationDevice = false, sdkInt = sdk),
            )
        }
    }

    @Test
    fun `commAction is SET on API 31+ when a communication device is used`() {
        for (sdk in listOf(31, 34)) {
            assertEquals(
                AudioRouteDecision.CommAction.SET,
                AudioRouteDecision.commAction(useCommunicationDevice = true, sdkInt = sdk),
            )
        }
    }

    @Test
    fun `commAction is CLEAR on API 31+ when no communication device is used`() {
        for (sdk in listOf(31, 34)) {
            assertEquals(
                AudioRouteDecision.CommAction.CLEAR,
                AudioRouteDecision.commAction(useCommunicationDevice = false, sdkInt = sdk),
            )
        }
    }
}
