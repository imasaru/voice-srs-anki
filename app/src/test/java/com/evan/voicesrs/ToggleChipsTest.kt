package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** h122b: the two icon toggle-chip helpers (voice, drill-timer). */
@RunWith(RobolectricTestRunner::class)
class ToggleChipsTest {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)

    @Test fun `voiceOn is the inverse of silent mode`() {
        assertTrue(ToggleChips.voiceOn(silentMode = false))
        assertFalse(ToggleChips.voiceOn(silentMode = true))
    }

    @Test fun `silentFromVoice is the inverse of voice on`() {
        assertTrue(ToggleChips.silentFromVoice(voiceOn = false))
        assertFalse(ToggleChips.silentFromVoice(voiceOn = true))
    }

    /** Records the icon resource id (MaterialButton wraps/tints the drawable, so constantState differs). */
    private class IconSpy(c: android.content.Context) : MaterialButton(c) {
        var lastIconRes = 0
        override fun setIconResource(iconResourceId: Int) { lastIconRes = iconResourceId; super.setIconResource(iconResourceId) }
    }

    @Test fun `paintVoiceIcon swaps the mic icon with the toggle`() {
        val b = IconSpy(ctx); b.isCheckable = true
        ToggleChips.paintVoiceIcon(b)
        assertEquals("unchecked -> mic_off", R.drawable.ic_mic_off, b.lastIconRes)
        b.isChecked = true
        ToggleChips.paintVoiceIcon(b)
        assertEquals("checked -> mic", R.drawable.ic_mic, b.lastIconRes)
    }

    @Test fun `bindSwitchA11y exposes a checkable switch with an OnOff state`() {
        val on = MaterialButton(ctx); on.isCheckable = true; on.isChecked = true
        ToggleChips.bindSwitchA11y(on, R.string.chip_state_on)
        assertEquals("On", on.contentDescription.toString())
        val nOn = AccessibilityNodeInfoCompat.obtain()
        ViewCompat.getAccessibilityDelegate(on)?.onInitializeAccessibilityNodeInfo(on, nOn)
        assertEquals("android.widget.Switch", nOn.className.toString())
        assertTrue(nOn.isCheckable)
        assertEquals("On", ViewCompat.getStateDescription(on)?.toString())
        val off = MaterialButton(ctx); off.isCheckable = true; off.isChecked = false
        ToggleChips.bindSwitchA11y(off, R.string.chip_state_off)
        assertEquals("Off", off.contentDescription.toString())
        assertEquals("Off", ViewCompat.getStateDescription(off)?.toString())
    }
}
