package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatHistoryStoreTest {
    @Test
    fun `save load round trip`() {
        val dir = File("build/tmp/chat-history-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val rows = listOf(
            StoredChat(StoredChat.ASK, 1L, 10L, "How did I do?", "You did well.", deck = "DEA"),
            StoredChat(StoredChat.TURN, 2L, 20L, "What is Delta?", "A lakehouse table.", cardId = 9L, ease = 3, transcript = "B")
        )
        ChatHistoryStore.save(dir, rows)
        val loaded = ChatHistoryStore.load(dir)
        assertEquals(rows, loaded)
        assertEquals(rows, ChatHistoryStore.decode(ChatHistoryStore.encode(rows)))
        dir.deleteRecursively()
    }

    @Test
    fun `cap trims the oldest messages`() {
        val many = (1L..5L).map { StoredChat(StoredChat.ASK, it, it, "Q$it", "A$it") }
        val trimmed = ChatHistoryStore.trim(many, cap = 3)
        assertEquals(listOf(3L, 4L, 5L), trimmed.map { it.id })
        assertEquals(5, ChatHistoryStore.trim(many, cap = 2000).size)
    }

    @Test
    fun `corrupt file starts empty without crashing`() {
        val dir = File("build/tmp/chat-history-bad").apply {
            deleteRecursively()
            mkdirs()
        }
        File(dir, ChatHistoryStore.FILE_NAME).writeText("not-json{{{")
        assertTrue(ChatHistoryStore.load(dir).isEmpty())
        assertTrue(ChatHistoryStore.decode("{").isEmpty())
        assertTrue(ChatHistoryStore.decode("").isEmpty())
        ChatHistoryStore.upsert(StoredChat(StoredChat.ASK, 7L, 1L, "Hi", "Hello"))
        assertEquals(1, ChatHistoryStore.snapshot().size)
        dir.deleteRecursively()
    }

    @Test
    fun `chat feed shows stored asks that are not in the live list`() {
        val history = listOf(
            StoredChat(StoredChat.ASK, 9L, 1L, "Today's report", "3 cards, 80% accuracy")
        )
        val rows = ChatFeed.rows(
            emptyList(),
            DrillUiState(),
            history = history
        )
        assertEquals(1, rows.size)
        val ask = rows[0] as ChatRow.Ask
        assertEquals("Today's report", ask.question)
        assertEquals("3 cards, 80% accuracy", (ask.state as ExplState.Ready).text)
    }
}
