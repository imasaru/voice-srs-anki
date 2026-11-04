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
    fun `collapsed card keeps two lines, expanded is the full stem`() {
        val html = "<p>First line of the stem</p><p>Second line stays</p><p>Third is hidden until expand</p>"
        assertEquals(
            "First line of the stem\nSecond line stays",
            BubbleModel.cardText(html, expanded = false)
        )
        val full = BubbleModel.cardText(html, expanded = true)
        assertTrue(full.contains("First line of the stem"))
        assertTrue(full.contains("Second line stays"))
        assertTrue(full.contains("Third is hidden until expand"))
        assertFalse(full.contains("<p>"))
    }

    @Test
    fun `user bubble labels the answer and a colored rating`() {
        val good = BubbleModel.userBubble("B", 3)
        assertEquals("B", good.answer)
        assertTrue(good.answered)
        assertEquals("Good", good.rating)
        assertEquals(RatingPill.GOOD_BG, RatingPill.of(good.ease)!!.background)
        assertEquals(RatingPill.GOOD_FG, RatingPill.of(good.ease)!!.text)
        assertEquals("B", BubbleModel.userBubble("answer b", 3).answer)
        assertEquals("Again", BubbleModel.userBubble("Answer B", 1).rating)
        assertEquals(RatingPill.AGAIN_BG, RatingPill.of(1)!!.background)
        assertEquals(RatingPill.AGAIN_FG, RatingPill.of(1)!!.text)

        val blank = BubbleModel.userBubble("", 4)
        assertEquals(BubbleModel.NOT_ANSWERED, blank.answer)
        assertFalse(blank.answered)
        assertEquals("Easy", blank.rating)
        assertEquals(RatingPill.EASY_BG, RatingPill.of(4)!!.background)
        assertEquals("Hard", BubbleModel.userBubble("hard one", 2).rating)
        assertEquals(RatingPill.HARD_BG, RatingPill.of(2)!!.background)
        assertEquals(
            "ANSWER\nNot answered\nRATING\nAgain",
            BubbleModel.userPlain("   ", 1)
        )
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
        assertEquals("ANSWER\nB\nRATING\nGood", echoed.user)
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
        assertEquals("", BubbleModel.displayAi(ExplState.Ready(text, cached = false), front, back, true).footer)
        assertTrue(row.unread)
        assertFalse(row.aiBody.contains("Card front:"))
        assertFalse(row.aiBody.startsWith(front))
    }

    @Test
    fun `source and license lines move to the explanation footer`() {
        val text = "Delta is the storage layer.\nSource: https://example.com/delta\nLicense: MIT\nhttps://example.com/only"
        val ai = BubbleModel.displayAi(ExplState.Ready(text, cached = false), front, back, unread = false)
        assertEquals("Delta is the storage layer.", ai.body)
        assertTrue(ai.footer.contains("Source:"))
        assertTrue(ai.footer.contains("License:"))
        assertTrue(ai.footer.contains("https://example.com/only"))
        assertFalse(ai.body.contains("Source:"))
        assertFalse(ai.body.contains("https://"))
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
    fun `no built-in url defaults`() {
        assertEquals("scottlowry/Ornith-1.5-35B-A3B-oQ5e-mtp:precise", Prefs.DEFAULT_AI_MODEL)
        assertTrue(Prefs.promptForUrl(""))
        assertFalse(Prefs.promptForUrl("http://anki.example.invalid:8765"))
    }
}
