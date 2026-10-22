package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset

class ThreadStoreTest {
    @Test
    fun `threads round trip and stay with that card`() {
        val dir = File("build/tmp/thread-store-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val thread = StoredThread(
            cardId = 9L,
            noteId = 4L,
            contentHash = "hash-a",
            messages = listOf(
                ThreadMessage("user", "Why not Parquet?", 1_700_000_000_000L),
                ThreadMessage("assistant", "**Delta** adds `ACID`.", 1_700_000_060_000L)
            )
        )
        val other = thread.copy(cardId = 10L, noteId = 5L, contentHash = "hash-b", messages = emptyList())
        ThreadStore.save(dir, listOf(thread, other))
        val loaded = ThreadStore.load(dir)
        assertEquals(2, loaded.size)
        val found = ThreadStore.find(loaded, cardId = 9L, noteId = 4L, hash = "hash-a")
        assertEquals(thread, found)
        assertNull(ThreadStore.find(loaded, cardId = 9L, noteId = 4L, hash = "edited"))
        assertNull(ThreadStore.find(loaded, cardId = 3L, noteId = 8L, hash = "hash-a"))

        val followUps = ThreadStore.toFollowUps(found!!.messages)
        assertEquals(1, followUps.size)
        assertEquals("Why not Parquet?", followUps[0].q)
        val ready = followUps[0].state as ExplState.Ready
        assertEquals("**Delta** adds `ACID`.", ready.text)
        assertEquals(ThreadStore.messagesOf(followUps), thread.messages)

        ThreadStore.upsert(
            dir,
            thread.copy(messages = thread.messages + ThreadMessage("user", "And OPTIMIZE?", 1_700_000_120_000L))
        )
        val again = ThreadStore.find(ThreadStore.load(dir), 9L, 4L, "hash-a")
        assertEquals(3, again!!.messages.size)
        assertEquals(other, ThreadStore.find(ThreadStore.load(dir), 10L, 5L, "hash-b"))
        dir.deleteRecursively()
    }

    @Test
    fun `corrupt history does not throw and a new thread persists`() {
        val dir = File("build/tmp/thread-store-bad").apply {
            deleteRecursively()
            mkdirs()
        }
        File(dir, ThreadStore.FILE_NAME).writeText("not-json")
        assertTrue(ThreadStore.load(dir).isEmpty())
        ThreadStore.upsert(
            dir,
            StoredThread(1L, null, "h", listOf(ThreadMessage("user", "Q", 5L)))
        )
        val saved = ThreadStore.load(dir).single()
        assertNull(saved.noteId)
        assertEquals("Q", saved.messages.single().text)
        val pending = ThreadStore.toFollowUps(saved.messages).single()
        assertTrue(pending.state is ExplState.Pending)
        dir.deleteRecursively()
    }

    @Test
    fun `link text counts replies and formats the last clock`() {
        assertEquals("💬 Reply in thread", ThreadLabel.text(0, null, ZoneOffset.UTC))
        val at = 1_700_000_000_000L
        val label = ThreadLabel.text(2, at, ZoneOffset.UTC)
        assertTrue(label.startsWith("💬 2 replies · last "))
        assertTrue(label.contains(":"))
        assertFalse(label.contains("**"))
        assertEquals(
            "💬 1 reply · last 22:13",
            ThreadLabel.text(1, at, ZoneOffset.UTC)
        )
    }

    @Test
    fun `follow-up prompt keeps the card answer and explanation`() {
        val prior = listOf(
            FollowUp("first?", ExplState.Ready("because ACID", cached = false), at = 1L),
            FollowUp("second?", ExplState.Pending, at = 2L)
        )
        val messages = PromptBuilder.followUpMessages(
            front = "What is Delta?",
            back = "ACID tables",
            transcript = "B",
            explanation = "Delta is the storage layer.",
            prior = prior,
            question = "What about OPTIMIZE?"
        )
        assertTrue(PromptBuilder.followUpSystem().contains("≤100 words"))
        assertEquals("user", messages.first().role)
        assertTrue(messages.first().content.contains("Card front: What is Delta?"))
        assertTrue(messages.first().content.contains("Card back: ACID tables"))
        assertTrue(messages.first().content.contains("Learner said: B"))
        assertTrue(messages.first().content.contains("Delta is the storage layer."))
        assertTrue(messages.any { it.role == "assistant" && it.content == "because ACID" })
        assertEquals("What about OPTIMIZE?", messages.last().content)
        assertFalse(messages.last().content.contains("Card front:"))
    }

    @Test
    fun `chat feed puts the new divider before the live question`() {
        ReviewLog.clear()
        val turn = ReviewLog.appendAfterGrade(
            ease = 3,
            cardId = 1L,
            noteId = 2L,
            frontText = "Past question",
            backText = "Past answer",
            contentHash = "h",
            transcript = "B",
            cachedText = "**bold**",
            aiEnabled = true
        )!!
        val rows = ChatFeed.rows(
            ReviewLog.snapshot(),
            DrillUiState(
                drillRunning = true,
                silentSession = true,
                frontHtml = "Current question line",
                showBack = false,
                ratingVisible = false
            )
        )
        assertTrue(rows[0] is ChatRow.Past)
        assertEquals(turn.turnId, (rows[0] as ChatRow.Past).turn.turnId)
        assertTrue(rows[1] is ChatRow.Divider)
        val live = rows[2] as ChatRow.Live
        assertEquals("Current question line", live.front)
        assertTrue(live.showAnswer)
        assertFalse(live.rating)
        assertFalse(live.showBack)
        val voice = ChatFeed.rows(
            emptyList(),
            DrillUiState(
                drillRunning = true,
                silentSession = false,
                frontHtml = "Current question line",
                showBack = false
            )
        ).last() as ChatRow.Live
        assertEquals(live.showAnswer, voice.showAnswer)
        assertEquals(live.rating, voice.rating)
        ReviewLog.clear()
    }
}
