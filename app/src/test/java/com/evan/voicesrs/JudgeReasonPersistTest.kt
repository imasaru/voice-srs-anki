package com.evan.voicesrs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class JudgeReasonPersistTest {
    @Before fun setUp() { ReviewLog.clear(); JudgeNote.clear() }
    @After fun tearDown() { ReviewLog.clear(); JudgeNote.clear() }

    private fun graded(cardId: Long, ease: Int): ReviewTurn = ReviewLog.appendAfterGrade(
        ease = ease, cardId = cardId, noteId = null, frontText = "Q$cardId", backText = "A",
        contentHash = "h$cardId", transcript = "said", cachedText = null, aiEnabled = false
    )!!

    @Test
    fun `a the reason is saved on the turn and survives save and restore`() {
        JudgeNote.put(7L, 2, "missed that the pool keeps idle VMs pre-warmed")
        val turn = graded(7L, 2)
        assertEquals("missed that the pool keeps idle VMs pre-warmed", turn.judgeReason)
        assertEquals(2, turn.judgeEase)
        val restored = ChatHistoryStore.decode(ChatHistoryStore.encode(listOf(StoredChat.ofTurn(turn))))
            .single().toTurn()
        assertEquals("missed that the pool keeps idle VMs pre-warmed", restored.judgeReason)
        assertEquals(2, restored.judgeEase)
        assertEquals("missed that the pool keeps idle VMs pre-warmed", JudgeNote.shown(restored))
    }

    @Test
    fun `b old history without a reason loads fine`() {
        val old = """{"messages":[{"kind":"turn","id":5,"at":1,"question":"q","answer":"","cardId":7,"ease":3,"transcript":"t"}]}"""
        val turn = ChatHistoryStore.decode(old).single().toTurn()
        assertEquals(3, turn.ease)
        assertEquals("", turn.judgeReason)
        assertNull(turn.judgeEase)
        assertNull(JudgeNote.shown(turn))
        // and a turn with no reason writes no reason keys
        val json = ChatHistoryStore.encode(listOf(StoredChat.ofTurn(turn)))
        assertEquals(false, json.contains("judgeReason"))
        assertEquals(false, json.contains("judgeEase"))
    }

    @Test
    fun `c a regrade to another rating hides the reason, and old turns keep their own`() {
        JudgeNote.put(7L, 2, "first try was partial")
        val first = graded(7L, 2)
        JudgeNote.put(7L, 3, "second try was complete")
        val second = graded(7L, 3)                     // same card, later turn
        val byId = { id: Long -> ReviewLog.snapshot().single { it.turnId == id } }
        assertEquals("first try was partial", JudgeNote.shown(byId(first.turnId)))
        assertEquals("second try was complete", JudgeNote.shown(byId(second.turnId)))
        ReviewLog.updateGrade(first.turnId, "said", 4)  // regrade Hard -> Easy
        assertNull(JudgeNote.shown(byId(first.turnId)))
        assertEquals("first try was partial", byId(first.turnId).judgeReason) // kept on disk, just hidden
        assertEquals("second try was complete", JudgeNote.shown(byId(second.turnId)))
        // a card graded without a judge verdict gets no reason
        assertNull(JudgeNote.shown(graded(8L, 3)))
    }
}
