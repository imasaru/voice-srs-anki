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

    /**
     * android.media.AudioDeviceInfo TYPE_* int values, written as literal
     * constants so this stays pure Kotlin (no android.* imports). See
     * android.media.AudioDeviceInfo for the canonical values.
     */
    private object DeviceTypes {
        const val TYPE_BUILTIN_MIC = 15
        const val TYPE_BUILTIN_SPEAKER = 2
        const val TYPE_BUILTIN_EARPIECE = 1
        const val TYPE_WIRED_HEADSET = 3
        const val TYPE_WIRED_HEADPHONES = 4
        const val TYPE_BLUETOOTH_SCO = 7
        const val TYPE_BLUETOOTH_A2DP = 8
        const val TYPE_USB_HEADSET = 22
        const val TYPE_BLE_HEADSET = 26
        const val TYPE_BLE_SPEAKER = 27

        /** Map a TYPE_* int to [AudioDevType]; unrecognised values fall to OTHER. */
        fun typeOf(androidType: Int): AudioDevType = when (androidType) {
            TYPE_BUILTIN_MIC -> AudioDevType.BUILTIN_MIC
            TYPE_BUILTIN_SPEAKER -> AudioDevType.BUILTIN_SPEAKER
            TYPE_BUILTIN_EARPIECE -> AudioDevType.BUILTIN_EARPIECE
            TYPE_WIRED_HEADSET -> AudioDevType.WIRED_HEADSET
            TYPE_WIRED_HEADPHONES -> AudioDevType.WIRED_HEADPHONES
            TYPE_BLUETOOTH_SCO -> AudioDevType.BLUETOOTH_SCO
            TYPE_BLUETOOTH_A2DP -> AudioDevType.BLUETOOTH_A2DP
            TYPE_USB_HEADSET -> AudioDevType.USB_HEADSET
            TYPE_BLE_HEADSET -> AudioDevType.BLE_HEADSET
            TYPE_BLE_SPEAKER -> AudioDevType.BLE_SPEAKER
            else -> AudioDevType.OTHER
        }
    }

    /** Map an android.media.AudioDeviceInfo TYPE_* int to [AudioDevType]. */
    fun typeOf(androidType: Int): AudioDevType = DeviceTypes.typeOf(androidType)
}
