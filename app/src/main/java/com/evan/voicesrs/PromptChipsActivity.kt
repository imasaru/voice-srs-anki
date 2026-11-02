package com.evan.voicesrs

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Settings › One-tap prompts: reorder, edit, delete and add prompt chips. Every change is saved at once. */
class PromptChipsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CHIP_ID = "chip_id"

        fun sourceLabel(source: ChipSource): Int = when (source) {
            ChipSource.TODAY_REVIEWS -> R.string.prompt_src_reviews
            ChipSource.CURRENT_CARD -> R.string.prompt_src_card
            ChipSource.CARD_CHATS -> R.string.prompt_src_chats
            ChipSource.EXAM_READINESS -> R.string.prompt_src_exam
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt_chips)
        val toolbar = findViewById<MaterialToolbar>(R.id.promptChipsToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        findViewById<Button>(R.id.promptChipAdd).setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        renderList()
    }

    private fun commit(chips: List<PromptChip>) {
        Prefs.savePromptChips(this, chips)
        renderList()
    }

    private fun renderList() {
        val chips = Prefs.loadPromptChips(this)
        val list = findViewById<LinearLayout>(R.id.promptChipList)
        list.removeAllViews()
        findViewById<View>(R.id.promptChipEmpty).visibility = if (chips.isEmpty()) View.VISIBLE else View.GONE
        for ((i, chip) in chips.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_prompt_chip_row, list, false)
            row.findViewById<TextView>(R.id.promptRowName).text = chip.name
            val meta = getString(sourceLabel(chip.source))
            row.findViewById<TextView>(R.id.promptRowMeta).text =
                if (chip.builtIn) meta + " · " + getString(R.string.prompt_chip_builtin) else meta
            val up = row.findViewById<Button>(R.id.promptRowUp)
            val down = row.findViewById<Button>(R.id.promptRowDown)
            up.isEnabled = i > 0
            down.isEnabled = i < chips.size - 1
            up.setOnClickListener { commit(PromptChipEdits.move(chips, i, i - 1)) }
            down.setOnClickListener { commit(PromptChipEdits.move(chips, i, i + 1)) }
            row.findViewById<Button>(R.id.promptRowEdit).setOnClickListener { openEditor(chip.id) }
            val del = row.findViewById<Button>(R.id.promptRowDelete)
            del.visibility = if (chip.builtIn) View.INVISIBLE else View.VISIBLE
            del.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.prompt_delete_confirm, chip.name))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.prompt_delete) { _, _ ->
                        commit(PromptChipEdits.delete(Prefs.loadPromptChips(this), chip.id))
                    }
                    .show()
            }
            list.addView(row)
        }
    }

    /** ✎ (id = the chip) and ＋ Add prompt (id = null). */
    private fun openEditor(id: String?) {
        val i = android.content.Intent(this, PromptChipEditActivity::class.java)
        if (id != null) i.putExtra(EXTRA_CHIP_ID, id)
        startActivity(i)
    }
}
