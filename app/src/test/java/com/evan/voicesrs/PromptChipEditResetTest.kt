package com.evan.voicesrs

import android.content.DialogInterface
import android.content.Intent
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** 57E2b: Reset to default asks first (confirmation required before reset). */
@RunWith(RobolectricTestRunner::class)
class PromptChipEditResetTest {
    private fun open(): PromptChipEditActivity {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Prefs.savePromptChips(app, listOf(PromptChip(PromptChips.REPORT_ID, "My report", "Custom prompt", ChipSource.CARD_CHATS, false)))
        val i = Intent(app, PromptChipEditActivity::class.java).putExtra(PromptChipsActivity.EXTRA_CHIP_ID, PromptChips.REPORT_ID)
        val a = Robolectric.buildActivity(PromptChipEditActivity::class.java, i).setup().get()
        a.findViewById<Button>(R.id.promptEditReset).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }
    private fun name(a: PromptChipEditActivity) = a.findViewById<TextInputEditText>(R.id.promptEditName).text.toString()
    private fun prompt(a: PromptChipEditActivity) = a.findViewById<TextInputEditText>(R.id.promptEditPrompt).text.toString()
    private fun dialog() = ShadowDialog.getLatestDialog() as AlertDialog

    @Test fun a_resetShowsDialog() {
        val a = open()
        val d = dialog()
        assertTrue(d.isShowing)
        assertEquals("Custom prompt", prompt(a))
        assertEquals(a.getString(R.string.prompt_edit_reset_confirm), d.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
    }

    @Test fun b_cancelLeavesFields() {
        val a = open()
        dialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("My report", name(a))
        assertEquals("Custom prompt", prompt(a))
    }

    @Test fun c_resetRefillsFieldsOnly() {
        val a = open()
        dialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val def = PromptChips.defaults()[0]
        assertEquals(def.name, name(a))
        assertEquals(def.prompt, prompt(a))
        // not saved until Save
        assertEquals("Custom prompt", Prefs.loadPromptChips(a).first { it.id == PromptChips.REPORT_ID }.prompt)
    }
}
