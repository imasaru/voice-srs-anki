package com.evan.voicesrs

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/** h112: chat bubbles are selectable and the report reply copies its plain text. */
@RunWith(RobolectricTestRunner::class)
class H112Test {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val report = "## Today\n**12** cards reviewed\n- Good: 9"

    private fun holder() = ChatAdapter.AskHolder(
        LayoutInflater.from(ctx).inflate(R.layout.item_chat_ask, FrameLayout(ctx), false))

    private fun bound(state: ExplState) = holder().also { it.bind(ChatRow.Ask(1L, "Today's report", state)) }
    private fun reply(h: ChatAdapter.AskHolder) = h.itemView.findViewById<TextView>(R.id.askReply)

    @Test fun a_readyReplySelectableAfterBind() {
        val h = bound(ExplState.Ready(report, false))
        assertFalse(reply(h).isTextSelectable)
        assertTrue(reply(h).isLongClickable)
        assertTrue(h.itemView.findViewById<TextView>(R.id.askQuestion).isLongClickable)
    }

    @Test fun b_rebindKeepsSelectable() {
        val h = holder()
        h.bind(ChatRow.Ask(1L, "q", ExplState.Error("boom", true)))
        h.bind(ChatRow.Ask(2L, "q", ExplState.Ready(report, false)))
        assertTrue(reply(h).isLongClickable)
    }

    @Test fun c_retryableErrorStaysTappable() {
        val r = reply(bound(ExplState.Error("boom", true)))
        assertFalse(r.isTextSelectable)
        assertTrue(r.isClickable && r.hasOnClickListeners())
    }

    @Test fun d_copyIsPlainTextWithToast() {
        val text = BubbleCopy.plain(reply(bound(ExplState.Ready(report, false))))
        assertFalse(text.contains("**") || text.contains("## "))
        assertTrue(text.contains("cards reviewed"))
        BubbleCopy.copy(ctx, text)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(text, cm.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(ctx.getString(R.string.chat_copied), ShadowToast.getTextOfLatestToast())
    }
}
