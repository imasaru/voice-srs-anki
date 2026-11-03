package com.evan.voicesrs

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class H126Test {
    private val ctx = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_VoiceSrs)
    private fun holder(): ChatAdapter.AskHolder = ChatAdapter.AskHolder(LayoutInflater.from(ctx).inflate(R.layout.item_chat_ask, FrameLayout(ctx), false)) { _, _, _ -> }
    private fun noteIcon(h: ChatAdapter.AskHolder) = h.itemView.findViewById<ImageButton>(R.id.askReplyNoteIcon)
    @Test fun `a MakeCardFlow split into start startAsk and draft`() {
        val src = File("src/main/java/com/evan/voicesrs/MakeCardFlow.kt").readText()
        assertEquals("startAsk present", true, src.contains("fun startAsk(context: Context, question: String, reply: String)"))
        assertEquals("draft(question)", true, src.contains("draft(context, question, \"\", reply)"))
        assertEquals("start forwards", true, src.contains("draft(context, turn.frontText, turn.backText, explanation)"))
        assertEquals("draft uses front/back", true, src.contains("MakeCard.userMessage(front, back, explanation)"))
        assertEquals("old inline call gone", false, src.contains("MakeCard.userMessage(turn.frontText"))
    }

    @Test fun `b ChatAdapter wires startAsk on the note icon`() {
        val src = File("src/main/java/com/evan/voicesrs/ChatAdapter.kt").readText()
        assertEquals("startAsk wired", true, src.contains("MakeCardFlow.startAsk(itemView.context, question.text.toString(), BubbleCopy.plain(reply))"))
    }

    @Test fun `c ready reply shows the note icon with a card click and label`() {
        val h = holder().also { it.bind(ChatRow.Ask(7L, "What is Delta?", ExplState.Ready("Delta is **ACID** storage", false))) }
        val icon = noteIcon(h)
        assertEquals(View.VISIBLE, icon.visibility)
        assertEquals(true, icon.hasOnClickListeners())
        assertEquals(ctx.getString(R.string.corner_make_card), icon.contentDescription)
    }

    @Test fun `d pending and error hide the note icon`() {
        val pending = holder().also { it.bind(ChatRow.Ask(7L, "q", ExplState.Pending)) }
        assertEquals(View.GONE, noteIcon(pending).visibility)
        val errored = holder().also { it.bind(ChatRow.Ask(7L, "q", ExplState.Error("boom", true))) }
        assertEquals(View.GONE, noteIcon(errored).visibility)
    }

    @Test fun `e note icon sits left of the play icon in the reply corner`() {
        val h = holder().also { it.bind(ChatRow.Ask(7L, "q", ExplState.Ready("x", false))) }
        val corner = h.itemView.findViewById<ViewGroup>(R.id.askReplyCorner)
        assertEquals(R.id.askReplyNoteIcon, corner.getChildAt(0).id)
        assertEquals(R.id.askReplyPlayIcon, corner.getChildAt(1).id)
    }
}
