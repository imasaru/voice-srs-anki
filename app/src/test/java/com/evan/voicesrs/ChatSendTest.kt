package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChatSendTest {

    @Test
    fun `no card resolves to a general question and a card stays a card`() {
        val general = ChatTarget.composer(null)
        assertTrue(general is ChatTarget.Target.General)
        val blankOrdinal = ChatTarget.composer(ChatTarget.Screen(20L, 0, false))
        assertTrue(blankOrdinal is ChatTarget.Target.General)
        val live = ChatTarget.composer(ChatTarget.Screen(20L, 2, revealed = false))
        assertTrue(live is ChatTarget.Target.Live)
        assertEquals(20L, (live as ChatTarget.Target.Live).cardId)
        assertFalse((live as ChatTarget.Target.Live).revealed)
        assertFalse(ChatTarget.countsAsGrade())
    }

    @Test
    fun `a general prompt names the deck and says there is no card`() {
        val system = PromptBuilder.generalSystem(true)
        assertFalse(system == PromptBuilder.SYSTEM)
        assertEquals(2, PromptBuilder.PROMPT_VERSION)
        assertTrue(system.contains("no specific card"))
        val messages = PromptBuilder.generalMessages("Databricks DEA", "What is a lakehouse?")
        val blob = messages.joinToString("\n") { it.content }
        assertTrue(blob.contains("Databricks DEA"))
        assertTrue(blob.contains("no specific card"))
        assertTrue(blob.contains("What is a lakehouse?"))
        assertFalse(blob.contains("Card back:"))
        assertEquals(
            "General question · Databricks DEA",
            ChatAskLabel.general("  Databricks DEA  ")
        )
        assertEquals("General question", ChatAskLabel.general(" "))
        assertEquals("General question", ChatAskLabel.general(""))
    }

    @Test
    fun `empty input no deck network and ai errors are visible and do not pause`() {
        val empty = ChatSend.resolve("  ", ChatTarget.Screen(1L, 1, false), "Deck")
        assertTrue(empty is ChatSend.Outcome.Visible)
        assertEquals("Type a question first.", (empty as ChatSend.Outcome.Visible).message)

        val noDeck = ChatSend.resolve("Why?", null, "  ")
        assertTrue(noDeck is ChatSend.Outcome.Visible)
        assertEquals(
            "Select a deck in Settings, then ask again.",
            (noDeck as ChatSend.Outcome.Visible).message
        )

        val general = ChatSend.resolve("Why?", null, "Leaf")
        assertTrue(general is ChatSend.Outcome.General)
        assertEquals("Leaf", (general as ChatSend.Outcome.General).deck)
        assertEquals("Why?", general.question)
        assertFalse(general.pausesDrill)

        val card = ChatSend.resolve("hint?", ChatTarget.Screen(5L, 2, false), "Leaf")
        assertTrue(card is ChatSend.Outcome.Card)
        assertEquals(5L, (card as ChatSend.Outcome.Card).cardId)
        assertFalse(card.pausesDrill)

        assertEquals(
            "No connection. The question was not sent.",
            ChatSend.visible(ChatSend.kindOf(IOException("network")))
        )
        assertEquals(
            "The AI could not answer. Try again.",
            ChatSend.visible(ChatSend.kindOf(IllegalStateException("model")))
        )
        assertTrue(ChatSend.visible(ChatSend.Fail.EMPTY).isNotBlank())
        assertTrue(ChatSend.visible(ChatSend.Fail.NO_DECK).isNotBlank())
        assertTrue(ChatSend.visible(ChatSend.Fail.NO_NETWORK).isNotBlank())
        assertTrue(ChatSend.visible(ChatSend.Fail.AI).isNotBlank())
        assertFalse(ChatPause.pauses(drillRunning = false, ChatPause.Trigger.FOCUS_INPUT))
    }

    @Test
    fun `a general ask and a send error are visible rows without a live card`() {
        val rows = ChatFeed.rows(
            emptyList(),
            DrillUiState(drillRunning = false),
            asks = listOf(
                ChatRow.Ask(1L, "What is a lakehouse?", ExplState.Pending, "Leaf")
            ),
            notice = "Type a question first."
        )
        assertEquals(2, rows.size)
        val ask = rows[0] as ChatRow.Ask
        assertEquals("What is a lakehouse?", ask.question)
        assertEquals("Leaf", ask.deck)
        assertTrue(rows[1] is ChatRow.Notice)
        assertEquals("Type a question first.", (rows[1] as ChatRow.Notice).message)
        assertTrue(rows.none { it is ChatRow.Live })
    }
}