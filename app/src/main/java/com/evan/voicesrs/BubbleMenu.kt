package com.evan.voicesrs

import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView

/**
 * Long-press menu on past chat bubbles (h121a). A long-press on a bubble shows a single
 * "Copy" [PopupMenu]; tapping it copies the bubble's visible text via [BubbleCopy].
 *
 * The tap-only behaviour (expand, explain, regrade) is untouched: the long-click listener
 * only opens the menu and consumes the long-press, so it never fires the bubble's click.
 */
object BubbleMenu {

    /**
     * Text of every VISIBLE TextView inside [bubble], depth-first (pre-order). Views that
     * are GONE/INVISIBLE are skipped along with their children; blank TextViews are skipped.
     */
    fun visibleText(bubble: View): String {
        val out = StringBuilder()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            when (v) {
                is TextView -> {
                    val t = v.text.toString().trim()
                    if (t.isNotEmpty()) {
                        if (out.isNotEmpty()) out.append('\n')
                        out.append(t)
                    }
                }
                is ViewGroup -> {
                    for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
            }
        }
        walk(bubble)
        return out.toString()
    }

    /**
     * Attach a long-press "Copy" PopupMenu to [bubble] and to each view in [also]. Every
     * long-press copies the primary [bubble]'s visible text (not the tapped view's own text).
     */
    fun attach(bubble: View, vararg also: View) {
        for (v in listOf(bubble) + also) {
            v.isLongClickable = true
            v.setOnLongClickListener {
                val popup = PopupMenu(bubble.context, bubble)
                popup.menu.add(R.string.chat_menu_copy)
                popup.setOnMenuItemClickListener {
                    BubbleCopy.copy(bubble.context, visibleText(bubble))
                    true
                }
                popup.show()
                true
            }
        }
    }
}
