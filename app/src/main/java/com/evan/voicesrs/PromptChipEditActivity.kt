package com.evan.voicesrs

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

/** Edit prompt / Add prompt. Nothing is stored until Save. */
class PromptChipEditActivity : AppCompatActivity() {
    private var chipId = ""
    private var builtIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt_chip_edit)
        val toolbar = findViewById<MaterialToolbar>(R.id.promptEditToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        val chips = Prefs.loadPromptChips(this)
        val wanted = intent.getStringExtra(PromptChipsActivity.EXTRA_CHIP_ID)
        val existing = chips.firstOrNull { it.id == wanted }
        chipId = existing?.id ?: PromptChipEdits.newId(chips)
        builtIn = existing?.builtIn ?: false
        toolbar.setTitle(if (existing == null) R.string.prompt_edit_add_title else R.string.prompt_edit_title)
        val name = findViewById<TextInputEditText>(R.id.promptEditName)
        val prompt = findViewById<TextInputEditText>(R.id.promptEditPrompt)
        val source = findViewById<MaterialButtonToggleGroup>(R.id.promptEditSource)
        val reset = findViewById<Button>(R.id.promptEditReset)
        val save = findViewById<Button>(R.id.promptEditSave)
        fun fill(c: PromptChip) {
            name.setText(c.name)
            prompt.setText(c.prompt)
            source.check(sourceButton(c.source))
        }
        if (existing != null) fill(existing) else source.check(R.id.promptEditSrcReviews)
        reset.visibility = if (chipId == PromptChips.REPORT_ID) View.VISIBLE else View.GONE
        reset.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.prompt_edit_reset_title)
                .setMessage(R.string.prompt_edit_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.prompt_edit_reset_confirm) { _, _ ->
                    fill(PromptChips.defaults()[0])
                    builtIn = true
                }
                .show()
        }
        fun refreshSave() {
            save.isEnabled = PromptChipEdits.canSave(name.text?.toString() ?: "", prompt.text?.toString() ?: "")
        }
        name.doAfterTextChanged { refreshSave() }
        prompt.doAfterTextChanged { refreshSave() }
        refreshSave()
        save.setOnClickListener {
            val chip = PromptChip(
                chipId,
                (name.text?.toString() ?: "").trim(),
                (prompt.text?.toString() ?: "").trim(),
                sourceOf(source.checkedButtonId),
                builtIn
            )
            Prefs.savePromptChips(this, PromptChipEdits.upsert(Prefs.loadPromptChips(this), chip))
            finish()
        }
    }

    private fun sourceButton(s: ChipSource): Int = when (s) {
        ChipSource.TODAY_REVIEWS -> R.id.promptEditSrcReviews
        ChipSource.CURRENT_CARD -> R.id.promptEditSrcCard
        ChipSource.CARD_CHATS -> R.id.promptEditSrcChats
    }

    private fun sourceOf(buttonId: Int): ChipSource = when (buttonId) {
        R.id.promptEditSrcCard -> ChipSource.CURRENT_CARD
        R.id.promptEditSrcChats -> ChipSource.CARD_CHATS
        else -> ChipSource.TODAY_REVIEWS
    }
}
