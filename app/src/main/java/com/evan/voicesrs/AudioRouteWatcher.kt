package com.evan.voicesrs

/**
 * Pure route decision built on top of [MicRoute.pickInput].
 *
 * [pickInput] returns the input device to switch to, or `null` when the right
 * input is already in use (it equals [previous]) or no suitable input exists.
 * So a non-null [Result.chosen] always differs from [previous] and means a
 * switch is required; `null` means leave the current route untouched.
 *
 * No Android platform calls live here — the caller feeds in the enumerated
 * [MicRoute.AudioDev] lists.
 */
object AudioRouteDecision {

    /** Outcome of [decide]. */
    data class Result(
        /** The input device to route to, or null when no switch is needed. */
        val chosen: MicRoute.AudioDev?,
        /** True when [chosen] differs from [previous] (a switch is required). */
        val changed: Boolean,
        /**
         * True when the chosen input is a communication device — a Bluetooth SCO
         * / BLE headset mic or a wired or USB headset mic — rather than the
         * built-in mic.
         */
        val useCommunicationDevice: Boolean,
    )

    /** Whether a mic of this type should be treated as a communication device. */
    private fun usesCommunicationDevice(type: MicRoute.AudioDevType): Boolean = when (type) {
        MicRoute.AudioDevType.BLUETOOTH_SCO,
        MicRoute.AudioDevType.BLE_HEADSET,
        MicRoute.AudioDevType.WIRED_HEADSET,
        MicRoute.AudioDevType.USB_HEADSET -> true
        else -> false
    }

    /**
     * Decide the active input from the available devices. [previous] is the input
     * currently in use (null when none).
     */
    fun decide(
        previous: MicRoute.AudioDev?,
        outputs: List<MicRoute.AudioDev>,
        inputs: List<MicRoute.AudioDev>,
    ): Result {
        val chosen = MicRoute.pickInput(outputs, inputs, previous)
        return Result(
            chosen = chosen,
            changed = chosen != null,
            useCommunicationDevice = chosen?.let { usesCommunicationDevice(it.type) } ?: false,
        )
    }
}
