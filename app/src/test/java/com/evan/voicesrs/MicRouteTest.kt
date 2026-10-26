package com.evan.voicesrs

import com.evan.voicesrs.MicRoute
import com.evan.voicesrs.MicRoute.AudioDev
import com.evan.voicesrs.MicRoute.AudioDevType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MicRouteTest {

    private fun dev(id: Int, type: AudioDevType, name: String = "") = AudioDev(id, type, name)

    @Test
    fun `speaker only falls back to built-in mic`() {
        val out = listOf(dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"))
        val ins = listOf(dev(2, AudioDevType.BUILTIN_MIC, "Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.BUILTIN_MIC, picked?.type)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `wired headset uses its own mic`() {
        val out = listOf(dev(1, AudioDevType.WIRED_HEADSET, "Headset"))
        val ins = listOf(dev(2, AudioDevType.WIRED_HEADSET, "Headset Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.WIRED_HEADSET, picked?.type)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `headphones without a mic fall back to built-in mic`() {
        val out = listOf(dev(1, AudioDevType.WIRED_HEADPHONES, "Headphones"))
        val ins = listOf(dev(2, AudioDevType.BUILTIN_MIC, "Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.BUILTIN_MIC, picked?.type)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `Bluetooth SCO uses a Bluetooth call mic`() {
        val out = listOf(dev(1, AudioDevType.BLUETOOTH_SCO, "BT Head"))
        val ins = listOf(
            dev(2, AudioDevType.BLE_HEADSET, "BLE Head"),
            dev(3, AudioDevType.BLUETOOTH_SCO, "BT Mic"),
        )
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.BLUETOOTH_SCO, picked?.type)
        assertEquals(3, picked?.id)
    }

    @Test
    fun `A2DP falls back to a SCO input when present`() {
        val out = listOf(dev(1, AudioDevType.BLUETOOTH_A2DP, "Speaker"))
        val ins = listOf(dev(2, AudioDevType.BLUETOOTH_SCO, "BT Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.BLUETOOTH_SCO, picked?.type)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `A2DP falls back to built-in mic when no Bluetooth mic exists`() {
        val out = listOf(dev(1, AudioDevType.BLUETOOTH_A2DP, "Speaker"))
        val ins = listOf(dev(2, AudioDevType.BUILTIN_MIC, "Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.BUILTIN_MIC, picked?.type)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `BLE headset uses its own mic`() {
        val out = listOf(dev(1, AudioDevType.BLE_HEADSET, "BLE Head"))
        val ins = listOf(dev(2, AudioDevType.BLE_HEADSET, "BLE Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(AudioDevType.BLE_HEADSET, picked?.type)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `Bluetooth output with no mic input returns null`() {
        val out = listOf(dev(1, AudioDevType.BLUETOOTH_SCO, "BT Head"))
        val ins = emptyList<AudioDev>()
        assertNull(MicRoute.pickInput(out, ins))
    }

    @Test
    fun `wired headset beats speaker for the active output`() {
        val out = listOf(
            dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"),
            dev(2, AudioDevType.WIRED_HEADSET, "Headset"),
        )
        val ins = listOf(dev(3, AudioDevType.WIRED_HEADSET, "Headset Mic"))
        val picked = MicRoute.pickInput(out, ins)
        assertEquals(3, picked?.id)
    }

    @Test
    fun `no change returns null when the picked input equals current`() {
        val cur = dev(9, AudioDevType.BUILTIN_MIC, "Mic")
        val out = listOf(dev(1, AudioDevType.BUILTIN_SPEAKER, "Speaker"))
        val ins = listOf(dev(2, AudioDevType.BUILTIN_MIC, "Mic"))
        // Different id/name, same type -> not equal, so it picks the input.
        val picked = MicRoute.pickInput(out, ins, cur)
        assertEquals(2, picked?.id)

        // Identical device -> nothing changes, null.
        assertNull(MicRoute.pickInput(out, ins, current = dev(2, AudioDevType.BUILTIN_MIC, "Mic")))
    }

    @Test
    fun `empty outputs returns null`() {
        assertNull(MicRoute.pickInput(emptyList(), listOf(dev(2, AudioDevType.BUILTIN_MIC))))
    }
}
