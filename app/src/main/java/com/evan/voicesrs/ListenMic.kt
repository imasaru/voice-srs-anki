package com.evan.voicesrs

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The input [AudioRouteDecision] would pick for a listen, as a real device.
 * Headset-mic (h105) wins when that setting is on and a headset input exists.
 * Does not change [MicRoute] or [AudioRouteWatcher].
 */
internal object ListenMic {
    /** Only surface [dev] for setPreferredDevice when the comm route actually came up (latch hit, not timeout). */
    fun <T> preferWhen(commActive: Boolean, dev: T?): T? =
        if (commActive) dev else null

    fun preferred(context: Context): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS) ?: return null
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        val inDevs = inputs.map { info ->
            MicRoute.AudioDev(info.id, MicRoute.typeOf(info.type), info.productName?.toString().orEmpty())
        }
        val outDevs = outputs.map { info ->
            MicRoute.AudioDev(info.id, MicRoute.typeOf(info.type), info.productName?.toString().orEmpty())
        }
        val cfg = runCatching { Prefs.load(context) }.getOrNull()
        val channel = cfg?.readAloudChannel ?: Prefs.READ_ALOUD_MEDIA
        val headset = AudioRouteDecision.listenInput(cfg?.useHeadsetMic ?: true, inDevs, channel)
        val chosen = headset ?: AudioRouteDecision.decide(null, outDevs, inDevs, channel).chosen
        val id = chosen?.id ?: return null
        return inputs.firstOrNull { it.id == id }
    }
}
