package com.evan.voicesrs

import android.content.Context
import android.annotation.SuppressLint
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
 * result. It is intentionally not wired into [DrillService] yet.
 */
class AudioRouteWatcher(context: Context) {

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

    /** Unregister the callback and clear any communication device. */
    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    /** Returns whether a route change happened, and clears the flag. */
    fun consumeChange(): Boolean {
        val did = routeChanged
        routeChanged = false
        return did
    }

    /** Android API check lives in [AudioRouteDecision.commAction], so silence NewApi here. */
    @SuppressLint("NewApi")
    private fun evaluate() {
        val outputInfos =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        val inputInfos =
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) ?: emptyArray()

        val outputDevs = outputInfos.map { MicRoute.AudioDev(it.id, MicRoute.typeOf(it.type), it.productName.toString()) }
        val inputDevs = inputInfos.map { MicRoute.AudioDev(it.id, MicRoute.typeOf(it.type), it.productName.toString()) }

        val result = AudioRouteDecision.decide(previousInput, outputDevs, inputDevs)
        if (!result.changed) return

        previousInput = result.chosen
        routeChanged = true
        result.chosen?.let { Log.i(LOG_TAG, "audio route changed to ${it.type}") }

        when (AudioRouteDecision.commAction(result.useCommunicationDevice, Build.VERSION.SDK_INT)) {
            AudioRouteDecision.CommAction.SET ->
                result.chosen?.let { matchingOutput(it, outputInfos) }?.let {
                    audioManager.setCommunicationDevice(it)
                }
            AudioRouteDecision.CommAction.CLEAR -> audioManager.clearCommunicationDevice()
            AudioRouteDecision.CommAction.NONE -> Unit
        }
    }

    /** The output device matching [chosen] by type, else any external communication output. */
    private fun matchingOutput(
        chosen: MicRoute.AudioDev,
        outputs: Array<AudioDeviceInfo>,
    ): AudioDeviceInfo? =
        outputs.firstOrNull { MicRoute.typeOf(it.type) == chosen.type }
            ?: outputs.firstOrNull { isCommunicationOutput(MicRoute.typeOf(it.type)) }

    private fun isCommunicationOutput(type: MicRoute.AudioDevType): Boolean = when (type) {
        MicRoute.AudioDevType.BLUETOOTH_SCO,
        MicRoute.AudioDevType.BLUETOOTH_A2DP,
        MicRoute.AudioDevType.BLE_HEADSET,
        MicRoute.AudioDevType.BLE_SPEAKER,
        MicRoute.AudioDevType.WIRED_HEADSET,
        MicRoute.AudioDevType.WIRED_HEADPHONES,
        MicRoute.AudioDevType.USB_HEADSET -> true
        else -> false
    }

    private companion object {
        const val LOG_TAG = "AudioRoute"
    }
}
