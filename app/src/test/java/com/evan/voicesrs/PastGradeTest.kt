package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PastGradeTest {

    @Test
    fun `a past wrong tap previews again and a right tap previews good`() {
        val wrong = PastGrade.preview(
            letter = "A",
            correctLetter = "B",
            currentCard = false,
            rated = true,
            mcq = true
        )
        assertEquals("A", wrong!!.letter)
        assertFalse(wrong.correct)
        assertEquals(1, wrong.ease)
        assertEquals("Change rating to Again?", wrong.prompt)

        val right = PastGrade.preview("B", "B", currentCard = false, rated = true, mcq = true)
        assertTrue(right!!.correct)
        assertEquals(3, right.ease)
        assertEquals("Change rating to Good?", right.prompt)
    }

    @Test
    fun `the current card an unrated row and a non mcq are left alone`() {
        assertNull(PastGrade.preview("A", "B", currentCard = true, rated = true, mcq = true))
        assertNull(PastGrade.preview("A", "B", currentCard = false, rated = false, mcq = true))
        assertNull(PastGrade.preview("A", "B", currentCard = false, rated = true, mcq = false))
        assertNull(PastGrade.preview("G", "B", currentCard = false, rated = true, mcq = true))
        assertFalse(PastGrade.touchesCurrentCard())
    }

    @Test
    fun `confirm replaces a pending grade and answer cards otherwise`() {
        val pending = listOf(
            PendingGrade(4L, 3, 10L),
            PendingGrade(9L, 1, 20L)
        )
        val replaced = PastGrade.replacePending(pending, cardId = 9L, ease = 3, nowMs = 50L)
        assertTrue(replaced.replaced)
        assertEquals(listOf(PendingGrade(4L, 3, 10L), PendingGrade(9L, 3, 50L)), replaced.grades)
        assertEquals(PastGrade.Route.QUEUE, replaced.route)

        val absent = PastGrade.replacePending(pending, cardId = 8L, ease = 1, nowMs = 50L)
        assertFalse(absent.replaced)
        assertEquals(pending, absent.grades)
        assertEquals(PastGrade.Route.ANSWER_CARDS, absent.route)
        assertFalse(absent.usesSetDueDate)
    }

    @Test
    fun `confirm updates the stored grade and regenerates only when the letter changes`() {
        val changed = PastGrade.commit(
            oldLetter = "A",
            newLetter = "B",
            ease = 3,
            hasExplanation = true
        )
        assertEquals("B", changed.transcript)
        assertEquals(3, changed.ease)
        assertTrue(changed.regenerate)

        val same = PastGrade.commit(oldLetter = "B", newLetter = "b", ease = 3, hasExplanation = true)
        assertFalse(same.regenerate)

        val noExpl = PastGrade.commit(oldLetter = "A", newLetter = "C", ease = 1, hasExplanation = false)
        assertFalse(noExpl.regenerate)

        assertEquals("A", PastGrade.cancel(previousLetter = "A"))
        assertNull(PastGrade.cancel(previousLetter = null))
    }

    @Test
    fun `previewEase maps each ease to its rating prompt`() {
        val again = PastGrade.previewEase(ease = 1, currentCard = false, rated = true)!!
        assertEquals("", again.letter)
        assertFalse(again.correct)
        assertEquals(1, again.ease)
        assertEquals("Change rating to Again?", again.prompt)

        val hard = PastGrade.previewEase(ease = 2, currentCard = false, rated = true)!!
        assertFalse(hard.correct)
        assertEquals(2, hard.ease)
        assertEquals("Change rating to Hard?", hard.prompt)

        val good = PastGrade.previewEase(ease = 3, currentCard = false, rated = true)!!
        assertTrue(good.correct)
        assertEquals(3, good.ease)
        assertEquals("Change rating to Good?", good.prompt)

        val easy = PastGrade.previewEase(ease = 4, currentCard = false, rated = true)!!
        assertTrue(easy.correct)
        assertEquals(4, easy.ease)
        assertEquals("Change rating to Easy?", easy.prompt)
    }

    @Test
    fun `previewEase is null off-range, on the current card, or unrated`() {
        assertNull(PastGrade.previewEase(ease = 0, currentCard = false, rated = true))
        assertNull(PastGrade.previewEase(ease = 5, currentCard = false, rated = true))
        assertNull(PastGrade.previewEase(ease = 3, currentCard = true, rated = true))
        assertNull(PastGrade.previewEase(ease = 3, currentCard = false, rated = false))
    }
}