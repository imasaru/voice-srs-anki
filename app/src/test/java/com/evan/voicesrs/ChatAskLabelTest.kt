package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAskLabelTest {
    @Test
    fun `line names the question and the deck`() {
        assertEquals(
            "Asking about: Question 3 · Leaf",
            ChatAskLabel.line(3, "Leaf")
        )
        assertEquals(
            "Asking about: Question 1 · Databricks",
            ChatAskLabel.line(1, "  Databricks  ")
        )
    }

    @Test
    fun `no card or no deck hides the line`() {
        assertNull(ChatAskLabel.line(0, "Leaf"))
        assertNull(ChatAskLabel.line(-1, "Leaf"))
        assertNull(ChatAskLabel.line(2, " "))
        assertNull(ChatAskLabel.line(2, ""))
    }
}
