package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** h121d3: read-aloud (volume_up) corner icons on the Ask bubbles (part 3 of 3). */
@RunWith(RobolectricTestRunner::class)
class AskCornerIconsTest {
    private val ctx =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)

    private class Hooks {
        val calls = ArrayList<Triple<Long, Int, String>>()
    }

    private fun holder(hooks: Hooks): ChatAdapter.AskHolder = ChatAdapter.AskHolder(
        LayoutInflater.from(ctx).inflate(R.layout.item_chat_ask, FrameLayout(ctx), false)
    ) { id, index, text -> hooks.calls.add(Triple(id, index, text)) }

    private fun reply(h: ChatAdapter.AskHolder) = h.itemView.findViewById<TextView>(R.id.askReply)
    private fun replyBubble(h: ChatAdapter.AskHolder) = h.itemView.findViewById<View>(R.id.askReplyBubble)
    private fun replyPlay(h: ChatAdapter.AskHolder) = h.itemView.findViewById<ImageButton>(R.id.askReplyPlayIcon)
    private fun questionPlay(h: ChatAdapter.AskHolder) = h.itemView.findViewById<ImageButton>(R.id.askQuestionPlayIcon)

    /** Count every ImageButton reachable from [root] in the view tree. */
    private fun countButtons(root: View): Int {
        var n = 0
        if (root is ImageButton) n++
        if (root is ViewGroup) for (i in 0 until root.childCount) n += countButtons(root.getChildAt(i))
        return n
    }

    @Test
    fun `a ready reply play speaks plain non-blank text`() {
        val hooks = Hooks()
        val report = "## Today\n**12** cards reviewed\n- Good: 9"
        val h = holder(hooks).also { it.bind(ChatRow.Ask(7L, "Today's report", ExplState.Ready(report, false))) }
        replyPlay(h).performClick()
        assertEquals(1, hooks.calls.size)
        val (id, index, text) = hooks.calls[0]
        assertEquals(7L, id)
        assertEquals(-5, index)
        assertTrue("expected non-blank, was '$text'", text.isNotBlank())
        assertTrue("expected no markdown, was '$text'", !text.contains("**"))
        assertTrue("expected content kept", text.contains("cards reviewed"))
    }

    @Test
    fun `b question play speaks the question text`() {
        val hooks = Hooks()
        val q = "What hides a column?"
        val h = holder(hooks).also { it.bind(ChatRow.Ask(7L, q, ExplState.None)) }
        questionPlay(h).performClick()
        assertEquals(1, hooks.calls.size)
        val (id, index, text) = hooks.calls[0]
        assertEquals(7L, id)
        assertEquals(-4, index)
        assertEquals(q, text)
    }

    @Test
    fun `c none hides the reply bubble`() {
        val h = holder(Hooks()).also { it.bind(ChatRow.Ask(7L, "q", ExplState.None)) }
        assertEquals(View.GONE, replyBubble(h).visibility)
    }

    @Test
    fun `d pending shows bubble but hides play icon`() {
        val h = holder(Hooks()).also { it.bind(ChatRow.Ask(7L, "q", ExplState.Pending)) }
        assertEquals(View.VISIBLE, replyBubble(h).visibility)
        assertEquals(View.GONE, replyPlay(h).visibility)
    }

    @Test
    fun `e ask layout has no icon beyond the two play icons`() {
        val h = holder(Hooks()).also { it.bind(ChatRow.Ask(7L, "q", ExplState.None)) }
        assertEquals(3, countButtons(h.itemView))
    }
}
