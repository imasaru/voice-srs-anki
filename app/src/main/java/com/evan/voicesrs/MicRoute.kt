package com.evan.voicesrs

/**
 * Pure mic/output route picker. No android.* imports — this is plain Kotlin,
 * unit-tested in isolation. The Android side is responsible for enumerating
 * real devices into [AudioDev] lists and then calling [pickInput]; the decision
 * logic here never touches the platform.
 *
 * The picker has two jobs:
 *  1. Choose the active output from the available list (priority: wired
 *     headset/headphones/USB > BLE/BT > speaker).
 *  2. From that output, choose the matching input device.
 *
 * It returns null when there is no suitable input, or when the picked input is
 * identical to [current] (nothing would change).
 */
object MicRoute {

    enum class AudioDevType {
        BUILTIN_SPEAKER,
        BUILTIN_EARPIECE,
        BUILTIN_MIC,
        WIRED_HEADSET,
        WIRED_HEADPHONES,
        BLUETOOTH_SCO,
        BLUETOOTH_A2DP,
        BLE_HEADSET,
        BLE_SPEAKER,
        USB_HEADSET,
        USB_DEVICE,
        HEARING_AID,
        OTHER,
    }

    data class AudioDev(val id: Int, val type: AudioDevType, val name: String = "")

    /** Lower number = higher priority for the active output. */
    private fun outputPriority(type: AudioDevType): Int = when (type) {
        // Wired headset/headphones and USB win outright (best analog link).
        AudioDevType.WIRED_HEADSET,
        AudioDevType.WIRED_HEADPHONES,
        AudioDevType.USB_HEADSET,
        AudioDevType.USB_DEVICE -> 0

        // Bluetooth: SCO for calls, A2DP for media, plus BLE variants.
        AudioDevType.BLUETOOTH_SCO,
        AudioDevType.BLUETOOTH_A2DP,
        AudioDevType.BLE_HEADSET,
        AudioDevType.BLE_SPEAKER -> 1

        // Speakers and everything unclassified sit at the bottom.
        else -> 2
    }

    /** First input matching any of [types], or null when none match. */
    private fun pick(
        inputs: List<AudioDev>,
        vararg types: AudioDevType,
    ): AudioDev? {
        for (t in types) {
            val hit = inputs.firstOrNull { it.type == t }
            if (hit != null) return hit
        }
        return null
    }

    /**
     * Decide the active output/input pair. [current] is the device already in
     * use; returning null means the caller should leave routing untouched.
     */
    fun pickInput(
        outputs: List<AudioDev>,
        inputs: List<AudioDev>,
        current: AudioDev? = null,
    ): AudioDev? {
        if (outputs.isEmpty()) return null

        val out = outputs.minByOrNull { outputPriority(it.type) } ?: return null

        val input = when (out.type) {
            // Headset with a built-in mic: use its own mic.
            AudioDevType.WIRED_HEADSET -> pick(inputs, AudioDevType.WIRED_HEADSET)
            AudioDevType.USB_HEADSET -> pick(inputs, AudioDevType.USB_HEADSET)

            // Any Bluetooth output: prefer a Bluetooth call mic, else the built-in.
            AudioDevType.BLUETOOTH_SCO,
            AudioDevType.BLUETOOTH_A2DP,
            AudioDevType.BLE_HEADSET,
            AudioDevType.BLE_SPEAKER ->
                pick(inputs, AudioDevType.BLUETOOTH_SCO, AudioDevType.BLE_HEADSET)
                    ?: pick(inputs, AudioDevType.BUILTIN_MIC)

            // Headphones without a mic, speakers, or anything else: built-in mic.
            else -> pick(inputs, AudioDevType.BUILTIN_MIC)
        }

        if (input == null) return null
        if (input == current) return null
        return input
    }
}
