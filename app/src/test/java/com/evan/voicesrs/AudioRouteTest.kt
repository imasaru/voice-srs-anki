package com.evan.voicesrs

import com.evan.voicesrs.MicRoute
import com.evan.voicesrs.MicRoute.AudioDevType
import org.junit.Assert.assertEquals
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
}
