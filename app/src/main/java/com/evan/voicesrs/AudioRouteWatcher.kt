package com.evan.voicesrs

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

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

    /**
     * Whether a mic of this type should be treated as a communication device.
     * [channel] is [Prefs.READ_ALOUD_CALL] (today's behaviour) or [Prefs.READ_ALOUD_MEDIA]
     * (classic Bluetooth SCO is never a communication device).
     */
    private fun usesCommunicationDevice(
        type: MicRoute.AudioDevType,
        channel: String,
    ): Boolean = when (channel) {
        Prefs.READ_ALOUD_CALL -> when (type) {
            MicRoute.AudioDevType.BLUETOOTH_SCO,
            MicRoute.AudioDevType.BLE_HEADSET,
            MicRoute.AudioDevType.WIRED_HEADSET,
            MicRoute.AudioDevType.USB_HEADSET -> true
            else -> false
        }
        else -> when (type) {
            MicRoute.AudioDevType.BLE_HEADSET,
            MicRoute.AudioDevType.WIRED_HEADSET,
            MicRoute.AudioDevType.USB_HEADSET -> true
            else -> false
        }
    }

    /**
     * Decide the active input from the available devices. [previous] is the input
     * currently in use (null when none).
     */
    fun decide(
        previous: MicRoute.AudioDev?,
        outputs: List<MicRoute.AudioDev>,
        inputs: List<MicRoute.AudioDev>,
        channel: String = Prefs.READ_ALOUD_MEDIA,
    ): Result {
        // In MEDIA mode the classic Bluetooth SCO mic is never selectable, so the
        // phone never holds the headset in SCO and the read-aloud volume reaches A2DP.
        val pickInputs = if (channel == Prefs.READ_ALOUD_CALL) inputs
        else inputs.filter { it.type != MicRoute.AudioDevType.BLUETOOTH_SCO }
        val chosen = MicRoute.pickInput(outputs, pickInputs, previous)
        return Result(
            chosen = chosen,
            changed = chosen != null,
            useCommunicationDevice = chosen?.let { usesCommunicationDevice(it.type, channel) } ?: false,
        )
    }

    /**
     * h105: the headset-mic device for a listen, or null. When [useHeadsetMic]
     * is on, this returns the first Bluetooth SCO / BLE headset input regardless
     * of the read-aloud [channel], so setCommunicationDevice can grab it. When
     * off (today's behaviour — MEDIA never selects SCO), or when no headset input
     * is present, it returns null and the caller uses the normal [decide] route.
     */
    fun listenInput(
        useHeadsetMic: Boolean,
        inputs: List<MicRoute.AudioDev>,
        channel: String = Prefs.READ_ALOUD_MEDIA,
    ): MicRoute.AudioDev? {
        if (!useHeadsetMic) return null
        return inputs.firstOrNull {
            it.type == MicRoute.AudioDevType.BLUETOOTH_SCO ||
                it.type == MicRoute.AudioDevType.BLE_HEADSET
        }
    }

    /**
     * Whether an output of this type may carry a communication device for [channel].
     * In [Prefs.READ_ALOUD_MEDIA] classic Bluetooth (A2DP and SCO) is never a
     * communication output; [Prefs.READ_ALOUD_CALL] keeps today's set.
     */
    fun isCommunicationOutput(
        type: MicRoute.AudioDevType,
        channel: String,
    ): Boolean = when (channel) {
        Prefs.READ_ALOUD_CALL -> when (type) {
            MicRoute.AudioDevType.BLUETOOTH_SCO,
            MicRoute.AudioDevType.BLUETOOTH_A2DP,
            MicRoute.AudioDevType.BLE_HEADSET,
            MicRoute.AudioDevType.BLE_SPEAKER,
            MicRoute.AudioDevType.WIRED_HEADSET,
            MicRoute.AudioDevType.WIRED_HEADPHONES,
            MicRoute.AudioDevType.USB_HEADSET -> true
            else -> false
        }
        else -> when (type) {
            MicRoute.AudioDevType.BLE_HEADSET,
            MicRoute.AudioDevType.BLE_SPEAKER,
            MicRoute.AudioDevType.WIRED_HEADSET,
            MicRoute.AudioDevType.WIRED_HEADPHONES,
            MicRoute.AudioDevType.USB_HEADSET -> true
            else -> false
        }
    }

    /**
     * h143b: which output, if any, should carry the read-aloud TTS as a
     * communication device. Only [Prefs.READ_ALOUD_CALL] keeps the classic
     * Bluetooth SCO headset on the comm channel; [Prefs.READ_ALOUD_MEDIA] never
     * does (returns null). Returns the first BLUETOOTH_SCO output when the
     * channel is CALL and one is present, otherwise null. Pure — the Android
     * side enumerates the outputs and matches the returned device back to an
     * [android.media.AudioDeviceInfo].
     */
    fun ttsCommOutput(
        channel: String,
        outputs: List<MicRoute.AudioDev>,
    ): MicRoute.AudioDev? =
        if (channel == Prefs.READ_ALOUD_CALL)
            outputs.firstOrNull { it.type == MicRoute.AudioDevType.BLUETOOTH_SCO }
        else null

    /**
     * Which communication-device call a [Result] maps to. [CLEAR] and [SET] are
     * API 31+ only; [NONE] means the platform has no such API (below 31).
     */
    enum class CommAction { SET, CLEAR, NONE }

    /**
     * Pure API guard for the communication-device switch. Returns [NONE] on
     * pre-API-31 so the caller never reaches
     * [android.media.AudioManager.setCommunicationDevice]/[clearCommunicationDevice]
     * without a version check — the guard lives here, not at each call site.
     */
    fun commAction(useCommunicationDevice: Boolean, sdkInt: Int): CommAction =
        if (sdkInt < 31) CommAction.NONE
        else if (useCommunicationDevice) CommAction.SET
        else CommAction.CLEAR
}

/**
 * Watches for audio-device changes and re-routes the flashcard drill's input.
 *
 * The routing decision itself is pure ([MicRoute] + [AudioRouteDecision]); this
 * class only bridges Android's [AudioManager] into that logic and applies the
 * result.
 */
class AudioRouteWatcher(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val handler = Handler(Looper.getMainLooper())

    /** Set true when [evaluate] decides the route changed; cleared by [consumeChange]. */
    @Volatile
    var routeChanged = false
        private set

    private var previousInput: MicRoute.AudioDev? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) = evaluate()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) = evaluate()
    }

    /** Register the callback and evaluate the current device set once. */
    fun start() {
        audioManager.registerAudioDeviceCallback(callback, handler)
        evaluate()
    }

    /** Unregister the callback. Comm-device teardown is handled by the service. */
    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    /** h143 review: whether a route change is pending, without clearing it (startListening still consumes it to rebuild). */
    fun peekChange(): Boolean = routeChanged

    /** Returns whether a route change happened, and clears the flag. */
    fun consumeChange(): Boolean {
        val did = routeChanged
        routeChanged = false
        return did
    }

    /** Android API checks live in [AudioRouteDecision.commAction]; none remain here. */
    private fun evaluate() {
        val outputInfos =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        val inputInfos =
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) ?: emptyArray()

        val outputDevs = outputInfos.map { MicRoute.AudioDev(it.id, MicRoute.typeOf(it.type), it.productName.toString()) }
        val inputDevs = inputInfos.map { MicRoute.AudioDev(it.id, MicRoute.typeOf(it.type), it.productName.toString()) }

        val channel = runCatching { Prefs.load(context).readAloudChannel }.getOrDefault(Prefs.READ_ALOUD_MEDIA)
        val result = AudioRouteDecision.decide(previousInput, outputDevs, inputDevs, channel)
        Log.i(
            "MicRoute",
            "outs=${outputDevs.map { it.type }} ins=${inputDevs.map { it.type }} " +
                "chosen=${result.chosen?.type} changed=${result.changed} comm=${result.useCommunicationDevice} ch=$channel"
        )
        if (!result.changed) return

        previousInput = result.chosen
        routeChanged = true
    }

}
