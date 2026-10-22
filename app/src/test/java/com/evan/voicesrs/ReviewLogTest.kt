package com.evan.voicesrs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewLogTest {
    @Before
    fun reset() {
        ReviewLog.clear()
    }

    @After
    fun done() {
        ReviewLog.clear()
    }

    @Test
    fun `a turn is recorded only after a grade`() {
        assertNull(
            ReviewLog.appendAfterGrade(
                ease = null,
                cardId = 1L,
                noteId = 2L,
                frontText = "Q",
                backText = "A",
                contentHash = "h",
                transcript = "said",
                cachedText = null,
                aiEnabled = true
            )
        )
        assertTrue(ReviewLog.snapshot().isEmpty())
    }

    @Test
    fun `a cache hit is ready and never unread`() {
        val turn = append(ease = 3, cachedText = "from cache")
        val ready = turn.explanation as ExplState.Ready
        assertEquals("from cache", ready.text)
        assertTrue(ready.cached)
        assertFalse(turn.unread)
        assertEquals(0, ReviewLog.unreadCount())
        assertFalse(ReviewLog.markGenerated(turn.turnId, "fresh"))
        val kept = ReviewLog.snapshot().single()
        assertFalse(kept.unread)
        assertEquals(3, kept.ease)
        assertEquals(ready, kept.explanation)
    }

    @Test
    fun `a fresh explanation sets unread and leaves the ease alone`() {
        val turn = append(ease = 2, cachedText = null)
        assertTrue(turn.explanation is ExplState.Queued)
        assertFalse(turn.unread)
        assertTrue(ReviewLog.markGenerated(turn.turnId, "new text"))
        val updated = ReviewLog.snapshot().single()
        assertEquals(2, updated.ease)
        assertTrue(updated.unread)
        val ready = updated.explanation as ExplState.Ready
        assertEquals("new text", ready.text)
        assertFalse(ready.cached)
        assertEquals(1, ReviewLog.unreadCount())
    }

    @Test
    fun `a new session clears unread and does not reuse turn ids`() {
        val first = append(ease = 4, cachedText = null)
        ReviewLog.markGenerated(first.turnId, "done")
        assertEquals(1, ReviewLog.unreadCount())
        ReviewLog.clear()
        assertEquals(0, ReviewLog.unreadCount())
        assertTrue(ReviewLog.snapshot().isEmpty())
        val second = append(ease = 1, cachedText = "old row")
        assertTrue(second.turnId > first.turnId)
        assertFalse(second.unread)
        assertTrue((second.explanation as ExplState.Ready).cached)
    }

    @Test
    fun `ai off still keeps the turn and hides the explanation`() {
        val turn = append(ease = 3, cachedText = null, aiEnabled = false)
        assertTrue(turn.explanation is ExplState.None)
        assertFalse(turn.unread)
        assertEquals("said it", turn.transcript)
    }

    @Test
    fun `mark read clears the badge once`() {
        val turn = append(ease = 3, cachedText = null)
        ReviewLog.markGenerated(turn.turnId, "text")
        assertTrue(ReviewLog.markRead(turn.turnId))
        assertEquals(0, ReviewLog.unreadCount())
        assertFalse(ReviewLog.markRead(turn.turnId))
    }

    @Test
    fun `a past grade update replaces the transcript and ease`() {
        val turn = append(ease = 1, cachedText = "why A")
        assertTrue(ReviewLog.updateGrade(turn.turnId, transcript = "B", ease = 3))
        val updated = ReviewLog.snapshot().single()
        assertEquals("B", updated.transcript)
        assertEquals(3, updated.ease)
        assertEquals("QUESTION 4", updated.questionLabel)
        assertTrue(updated.explanation is ExplState.Ready)
        assertFalse(ReviewLog.updateGrade(turnId = 0L, transcript = "C", ease = 1))
    }

    @Test
    fun `ratings that do not auto explain stay without a queued job`() {
        val none = ReviewLog.appendAfterGrade(
            ease = 3,
            cardId = 1L,
            noteId = 2L,
            frontText = "front",
            backText = "back",
            contentHash = "h",
            transcript = "B",
            cachedText = null,
            aiEnabled = true,
            autoExplain = false,
            questionLabel = "CARD 1"
        )!!
        assertTrue(none.explanation is ExplState.None)
        assertEquals("CARD 1", none.questionLabel)
        ReviewLog.forceQueued(none.turnId)
        assertTrue(ReviewLog.snapshot().single().explanation is ExplState.Queued)
    }

    private fun append(ease: Int, cachedText: String?, aiEnabled: Boolean = true): ReviewTurn {
        return ReviewLog.appendAfterGrade(
            ease = ease,
            cardId = 9L,
            noteId = 8L,
            frontText = "front",
            backText = "back",
            contentHash = "hash",
            transcript = "said it",
            cachedText = cachedText,
            aiEnabled = aiEnabled,
            questionLabel = "QUESTION 4"
        )!!
    }
}
