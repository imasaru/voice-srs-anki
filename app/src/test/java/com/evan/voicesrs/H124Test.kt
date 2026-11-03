package com.evan.voicesrs

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** h124: read-aloud section ids -1..-5 live in SpeakSection. */
class H124Test {
    @Test
    fun hConstantsHaveTheirValuesAndAreDistinct() {
        assertEquals(-1, SpeakSection.CARD)
        assertEquals(-2, SpeakSection.USER)
        assertEquals(-3, SpeakSection.AI)
        assertEquals(-4, SpeakSection.ASK_QUESTION)
        assertEquals(-5, SpeakSection.ASK_REPLY)
        assertEquals(5, setOf(SpeakSection.CARD, SpeakSection.USER, SpeakSection.AI, SpeakSection.ASK_QUESTION, SpeakSection.ASK_REPLY).size)
    }

    @Test
    fun hChatAdapterUsesConstantsNotLiterals() {
        val src = File("src/main/java/com/evan/voicesrs/ChatAdapter.kt").readText()
        assertFalse("no magic negative section ids left in onSpeakSection/onSpeak calls", Regex("""(onSpeakSection|onSpeak)\([^)]*, -[0-9]""").containsMatchIn(src))
        assertTrue("uses SpeakSection.ASK_REPLY", src.contains("SpeakSection.ASK_REPLY"))
    }
}
