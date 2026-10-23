package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAskLabelTest {
    @Test
    fun `line names the shared label and the deck`() {
        assertEquals(
            "Asking about: QUESTION 4 · Leaf",
            ChatAskLabel.line("QUESTION 4", "Leaf")
        )
        assertEquals(
            "Asking about: CARD 1 · Databricks",
            ChatAskLabel.line("CARD 1", "  Databricks  ")
        )
    }

    @Test
    fun `a blank label or deck hides the card line and a general line stays readable`() {
        assertNull(ChatAskLabel.line("", "Leaf"))
        assertNull(ChatAskLabel.line("CARD 2", " "))
        assertNull(ChatAskLabel.line("CARD 2", ""))
        assertEquals("General question · Leaf", ChatAskLabel.general("Leaf"))
        assertEquals("General question", ChatAskLabel.general(""))
    }

    @Test
    fun `hint is the card hint while a card is up and a general hint otherwise`() {
        assertEquals("Ask the AI about this card…", ChatAskLabel.hint(true))
        assertEquals("Ask the AI a general question…", ChatAskLabel.hint(false))
    }
}
