package com.evan.voicesrs

import android.text.Spanned
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingLiveBubbleTest {
    private val front = "Masking hides a column. It runs at query time."

    @After fun reset() { DrillUi.update { DrillUiState() } }

    private fun bindLive(sentence: String): TextView {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        app.setTheme(R.style.Theme_VoiceSrs)
        DrillUi.update { it.copy(drillRunning = true, cardId = 7L, frontHtml = front, readingSentence = sentence, readingIndex = 0) }
        val adapter = ChatAdapter({}, {}, {})
        val holder = adapter.onCreateViewHolder(FrameLayout(app), ChatAdapter.TYPE_LIVE) as ChatAdapter.LiveHolder
        holder.bind(ChatRow.Live(front = front, back = "", showBack = false, heard = "", showAnswer = true, rating = false, selectedEase = null, cardId = 7L))
        return holder.itemView.findViewById(R.id.liveQuestion)
    }

    private fun spans(v: TextView): List<String> {
        val t = v.text as? Spanned ?: return emptyList()
        return t.getSpans(0, t.length, CardFaceBinder.ReadingHighlightSpan::class.java)
            .map { t.subSequence(t.getSpanStart(it), t.getSpanEnd(it)).toString() }
    }

    @Test
    fun `a live question gets the reading span`() {
        val q = bindLive("It runs at query time.")
        assertEquals(listOf("It runs at query time."), spans(q))
    }

    @Test
    fun `b spoken first part of the question lands in the live bubble`() {
        val parts = SentenceReading.parts(AnkiText.sanitizeForSpeech(front))
        assertTrue(parts.isNotEmpty())
        val q = bindLive(parts.first())
        assertEquals(1, spans(q).size)
        assertTrue(q.text.toString().contains(spans(q).single().trim()))
    }

    @Test
    fun `c another card's sentence is not painted`() {
        DrillUi.update { DrillUiState() }
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        app.setTheme(R.style.Theme_VoiceSrs)
        DrillUi.update { it.copy(drillRunning = true, cardId = 8L, frontHtml = front, readingSentence = "It runs at query time.") }
        val adapter = ChatAdapter({}, {}, {})
        val holder = adapter.onCreateViewHolder(FrameLayout(app), ChatAdapter.TYPE_LIVE) as ChatAdapter.LiveHolder
        holder.bind(ChatRow.Live(front = front, back = "", showBack = false, heard = "", showAnswer = true, rating = false, selectedEase = null, cardId = 7L))
        assertEquals(emptyList<String>(), spans(holder.itemView.findViewById(R.id.liveQuestion)))
    }
}
