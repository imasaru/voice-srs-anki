package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun `hint returns the card resource id while a card is up and the general id otherwise`() {
        assertEquals(R.string.chat_ask_hint, ChatAskLabel.hint(true))
        assertEquals(R.string.chat_hint_general, ChatAskLabel.hint(false))
    }

    @Test
    fun `the card and general hints live in the strings resource with the expected texts`() {
        val xml = stringsXml()
        assertNotNull("strings.xml not found", xml)
        val text = xml?.readText() ?: return
        assertTrue(
            "card hint missing from strings.xml",
            text.contains("""name="chat_ask_hint">Ask the AI about this card…""")
        )
        assertTrue(
            "general hint missing from strings.xml",
            text.contains("""name="chat_hint_general">Ask the AI a general question…""")
        )
    }

    private fun stringsXml(): File? {
        val candidates = listOf(
            "src/main/res/values/strings.xml",
            "app/src/main/res/values/strings.xml"
        )
        for (c in candidates) {
            val f = File(c)
            if (f.exists()) return f
        }
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val found = File(dir, "src/main/res/values/strings.xml")
            if (found.exists()) return found
            dir = dir.parentFile
        }
        return null
    }
}
