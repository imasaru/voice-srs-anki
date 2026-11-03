package com.evan.voicesrs

import java.io.File
import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** h125: Ask / report bubbles use BubbleMenu (long-press Copy) instead of text selection. */
@RunWith(RobolectricTestRunner::class)
class H125Test {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private val report = "## Today\n**12** cards reviewed\n- Good: 9"

    private fun holder() = ChatAdapter.AskHolder(
        LayoutInflater.from(ctx).inflate(R.layout.item_chat_ask, FrameLayout(ctx), false))

    private fun bound(state: ExplState) = holder().also { it.bind(ChatRow.Ask(1L, "Today's report", state)) }
    private fun reply(h: ChatAdapter.AskHolder) = h.itemView.findViewById<TextView>(R.id.askReply)

    @Test fun a_sourceUsesBubbleMenuNotSelection() {
        val src = File("src/main/java/com/evan/voicesrs/ChatAdapter.kt").readText()
        assertTrue(src.contains("BubbleMenu.attach(questionBubble, question)"))
        assertTrue(src.contains("BubbleMenu.attach(replyBubble, reply)"))
        assertFalse(src.contains("BubbleCopy.addCopyAll(reply)"))
        assertFalse(src.contains("BubbleCopy.selectable(question"))
        assertFalse(src.contains("BubbleCopy.selectable(reply"))
    }

    @Test fun b_readyReplyNotSelectableButBubblesLongClickable() {
        val h = bound(ExplState.Ready("A **bold** reply", false))
        assertFalse(reply(h).isTextSelectable)
        assertTrue(h.itemView.findViewById<View>(R.id.askQuestionBubble).isLongClickable)
        assertTrue(h.itemView.findViewById<View>(R.id.askReplyBubble).isLongClickable)
        assertTrue(h.itemView.findViewById<View>(R.id.askQuestion).isLongClickable)
        assertTrue(h.itemView.findViewById<View>(R.id.askReply).isLongClickable)
    }

    @Test fun c_retryableErrorReplyStillRetries() {
        val r = reply(bound(ExplState.Error("boom", true)))
        assertTrue(r.isClickable)
        assertTrue(r.hasOnClickListeners())
    }
}
