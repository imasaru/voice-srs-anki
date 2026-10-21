package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleModelTest {
    private val front = "Which Databricks technology provides ACID guarantees?"
    private val back = "Delta Lake"

    @Test
    fun `first line strips html and keeps only the opening block`() {
        assertEquals("Hello world", BubbleModel.firstLine("<p>Hello <b>world</b></p>"))
        assertEquals("First", BubbleModel.firstLine("<p>First</p><p>Second paragraph</p>"))
        assertEquals("Line one", BubbleModel.firstLine("Line one<br>Line two"))
        assertEquals("Long card", BubbleModel.firstLine("<h2>Long card</h2><p>This is a very long question.</p>"))
        assertEquals("Hello", BubbleModel.firstLine("Hello\nWorld"))
    }

    @Test
    fun `collapsed card is the first line, expanded is the full stem`() {
        val html = "<p>First line of the stem</p><p>More detail the learner should not see yet</p>"
        assertEquals("First line of the stem", BubbleModel.cardText(html, expanded = false))
        val full = BubbleModel.cardText(html, expanded = true)
        assertTrue(full.contains("First line of the stem"))
        assertTrue(full.contains("More detail the learner should not see yet"))
        assertFalse(full.contains("<p>"))
    }

    @Test
    fun `user label is answer times rating`() {
        assertEquals("B · Good", BubbleModel.userLabel("B", 3))
        assertEquals("B · Good", BubbleModel.userLabel("answer b", 3))
        assertEquals("B · Again", BubbleModel.userLabel("Answer B", 1))
        assertEquals("tokyo · Good", BubbleModel.userLabel("tokyo", 3))
        assertEquals("— · Good", BubbleModel.userLabel("", 3))
        assertEquals("— · Easy", BubbleModel.userLabel("   ", 4))
        assertEquals("hard one · Hard", BubbleModel.userLabel("hard one", 2))
    }

    @Test
    fun `prompt is never displayed as the explanation`() {
        val prompt = PromptBuilder.userMessage(front, back)
        assertEquals("Card front: $front\nCard back: $back", prompt)
        assertTrue(BubbleModel.isPromptEcho(prompt, front, back))
        assertEquals("", BubbleModel.visibleExplanation(prompt, front, back))
        assertEquals("", BubbleModel.visibleExplanation(PromptBuilder.SYSTEM, front, back))
        assertEquals(
            "",
            BubbleModel.visibleExplanation("<think>plan</think>\n$prompt", front, back)
        )
        val mixed = prompt + "\n\nDelta Lake adds ACID transactions on object storage."
        val shown = BubbleModel.visibleExplanation(mixed, front, back)
        assertFalse(shown.contains("Card front:"))
        assertFalse(shown.contains("Card back:"))
        assertTrue(shown.contains("ACID transactions"))

        val echoed = BubbleModel.exchange(
            front, back, "B", 3, ExplState.Ready(prompt, cached = false)
        )
        assertEquals("Which Databricks technology provides ACID guarantees?", echoed.card)
        assertEquals("B · Good", echoed.user)
        assertEquals("", echoed.aiBody)
        assertEquals(AiMessages.NO_TEXT, echoed.aiStatus)
        assertFalse(echoed.card.contains("Card front:"))
        assertFalse(echoed.user.contains("Card front:"))
        assertFalse(echoed.aiBody.contains(front))
        assertFalse(echoed.aiStatus.contains("Card front:"))
    }

    @Test
    fun `a real explanation is shown and does not repeat the question as the body`() {
        val text = "Delta Lake is the ACID storage layer on the lakehouse. The trap is picking Parquet alone."
        val row = BubbleModel.exchange(front, back, "B", 3, ExplState.Ready(text, cached = false), unread = true)
        assertEquals(text, row.aiBody)
        assertEquals("", row.aiStatus)
        assertTrue(row.unread)
        assertFalse(row.aiBody.contains("Card front:"))
        assertFalse(row.aiBody.startsWith(front))
    }

    @Test
    fun `queued thinking generating and error states`() {
        assertEquals(
            BubbleModel.QUEUED,
            BubbleModel.exchange(front, back, "B", 3, ExplState.Queued).aiStatus
        )
        assertEquals(
            BubbleModel.THINKING,
            BubbleModel.exchange(
                front, back, "B", 3, ExplState.Streaming(partial = "", thinking = true)
            ).aiStatus
        )
        val partial = BubbleModel.exchange(
            front, back, "B", 3, ExplState.Streaming(partial = "Delta is", thinking = false)
        )
        assertEquals(BubbleModel.GENERATING, partial.aiStatus)
        assertEquals("Delta is", partial.aiBody)
        val err = BubbleModel.exchange(
            front, back, "B", 3, ExplState.Error(AiMessages.NO_TEXT, retryable = true)
        )
        assertEquals(AiMessages.NO_TEXT, err.aiStatus)
        assertTrue(err.retry)
        assertEquals("", err.aiBody)
    }

    @Test
    fun `defaults point at the Aperture gateway`() {
        assertEquals("http://example.local/v1", Prefs.DEFAULT_AI_URL)
        assertEquals("scottlowry/Ornith-1.5-35B-A3B-oQ5e-mtp:precise", Prefs.DEFAULT_AI_MODEL)
    }
}
