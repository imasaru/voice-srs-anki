package com.evan.voicesrs

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast

/**
 * Chat bubble text selection + copy. RecyclerView recycles rows and [TextView.setText]
 * resets the selection state, so the XML `textIsSelectable` flag is not enough: every
 * bind re-applies [selectable] after the text is set.
 */
object BubbleCopy {

    /** Turn text selection on/off; when on, enable long-press (which starts selection). */
    fun selectable(view: TextView, on: Boolean) {
        view.setTextIsSelectable(on)
        if (on) view.isLongClickable = true
    }

    /** Rendered text: applySections already turned markdown into spans, so this is plain text. */
    fun plain(view: TextView): String = view.text.toString().trim()

    /** Copy [text] to the clipboard and confirm with a short toast. */
    fun copy(ctx: Context, text: String) {
        val clip = ClipData.newPlainText("Voice SRS", text)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clip)
        Toast.makeText(ctx, R.string.chat_copied, Toast.LENGTH_SHORT).show()
    }

    /** Add a "Copy all" entry to the text-selection action-mode menu. */
    fun addCopyAll(view: TextView) {
        var copyAll: MenuItem? = null
        view.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                copyAll = menu.add(R.string.chat_copy_all)
                copyAll?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item === copyAll) {
                    copy(view.context, plain(view))
                    mode.finish()
                    return true
                }
                // Leave the system Copy / Select-all items working.
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                copyAll = null
            }
        }
    }
}
