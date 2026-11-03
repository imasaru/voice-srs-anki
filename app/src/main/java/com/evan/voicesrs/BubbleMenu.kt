package com.evan.voicesrs

import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView

/**
 * Long-press menu on past chat bubbles (h121a/h121b). A long-press on a bubble shows a
 * PopupMenu with "Copy", and optionally "Ask about this" and "Flag card"; tapping an item
 * runs its callback.
 *
 * The tap-only behaviour (expand, explain, regrade) is untouched: the long-click listener
 * only opens the menu and consumes the long-press, so it never fires the bubble's click.
 */
object BubbleMenu {

    /** Distinct menu-item ids (see attach: the 1-arg add(titleRes) leaves itemId=0). */
    private const val ID_COPY = 0x7a01
    private const val ID_ASK = 0x7a02
    private const val ID_FLAG = 0x7a03
    private const val ID_OPEN_ANKI = 0x7a04

    /**
     * Quote [text] for a chat prompt: every line prefixed with "> " and joined by "\n", plus
     * a trailing newline.
     */
    fun quote(text: String): String {
        val out = StringBuilder()
        for (line in text.split("\n")) {
            out.append("> ").append(line).append('\n')
        }
        return out.toString()
    }

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
     * Attach a long-press PopupMenu to [bubble] and to each view in [also]. Shows "Copy", and
     * "Ask about this" when [onAsk] is non-null and "Flag card" when [onFlag] is non-null and
     * "Open in Anki" when [onOpenAnki] is non-null. Every
     * long-press copies/asks/flags/opens the primary [bubble]'s content (not the tapped view's own).
     */
    fun attach(
        bubble: View,
        onAsk: ((String) -> Unit)?,
        onFlag: (() -> Unit)?,
        vararg also: View,
        onOpenAnki: (() -> Unit)? = null
    ) {
        for (v in listOf(bubble) + also) {
            v.isLongClickable = true
            v.setOnLongClickListener {
                val popup = PopupMenu(bubble.context, bubble)
                // 4-arg add sets an explicit itemId (the 1-arg add(titleRes) leaves itemId=0, which
                // breaks when() dispatch). Match on itemId below.
                popup.menu.add(ID_COPY, ID_COPY, 0, R.string.chat_menu_copy)
                if (onAsk != null) popup.menu.add(ID_ASK, ID_ASK, 0, R.string.chat_menu_ask)
                if (onFlag != null) popup.menu.add(ID_FLAG, ID_FLAG, 0, R.string.chat_menu_flag)
                if (onOpenAnki != null) popup.menu.add(ID_OPEN_ANKI, ID_OPEN_ANKI, 0, R.string.chat_menu_open_anki)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        ID_COPY -> {
                            BubbleCopy.copy(bubble.context, visibleText(bubble))
                            true
                        }
                        ID_ASK -> {
                            onAsk?.invoke(quote(visibleText(bubble)))
                            true
                        }
                        ID_FLAG -> {
                            onFlag?.invoke()
                            true
                        }
                        ID_OPEN_ANKI -> {
                            onOpenAnki?.invoke()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
                true
            }
        }
    }

    /**
     * Attach a long-press "Copy" PopupMenu to [bubble] and to each view in [also]. Every
     * long-press copies the primary [bubble]'s visible text (not the tapped view's own text).
     */
    fun attach(bubble: View, vararg also: View) {
        attach(bubble, null, null, *also)
    }
}
