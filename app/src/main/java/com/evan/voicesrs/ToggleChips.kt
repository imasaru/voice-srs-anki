package com.evan.voicesrs

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.button.MaterialButton

/** h122: small pure helpers for the two icon toggle chips (voice, drill-timer). */
object ToggleChips {
    fun voiceOn(silentMode: Boolean) = !silentMode

    fun silentFromVoice(voiceOn: Boolean) = !voiceOn

    fun stateText(ctx: Context, on: Boolean) = if (on) ctx.getString(R.string.chip_state_on) else ctx.getString(R.string.chip_state_off)

    fun bindSwitchA11y(b: MaterialButton, name: CharSequence) {
        b.contentDescription = name
        ViewCompat.setStateDescription(b, stateText(b.context, b.isChecked))
        ViewCompat.setAccessibilityDelegate(b, object : View.AccessibilityDelegate() {
            override fun getNodeInfo(host: View, node: AccessibilityNodeInfoCompat) {
                super.getNodeInfo(host, node)
                node.className = "android.widget.Switch"
                node.isCheckable = true
                node.isChecked = b.isChecked
            }
        })
    }

    fun paintVoiceIcon(b: MaterialButton) {
        b.setIcon(if (b.isChecked) R.drawable.ic_mic else R.drawable.ic_mic_off)
    }
}
