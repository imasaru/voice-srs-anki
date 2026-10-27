package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptChipEditsTest {
    private val a = PromptChip("a", "A", "pa", ChipSource.TODAY_REVIEWS)
    private val b = PromptChip("b", "B", "pb", ChipSource.CURRENT_CARD)
    private val c = PromptChip("c", "C", "pc", ChipSource.CARD_CHATS)
    private val abc = listOf(a, b, c)

    @Test fun moveFirstToLast() {
        assertEquals(listOf(b, c, a), PromptChipEdits.move(abc, 0, 2))
    }

    @Test fun moveLastToFirst() {
        assertEquals(listOf(c, a, b), PromptChipEdits.move(abc, 2, 0))
    }

    @Test fun moveSameIndexUnchanged() {
        assertEquals(abc, PromptChipEdits.move(abc, 1, 1))
    }

    @Test fun moveOutOfRangeUnchanged() {
        assertEquals(abc, PromptChipEdits.move(abc, -1, 1))
        assertEquals(abc, PromptChipEdits.move(abc, 0, 3))
    }

    @Test fun deleteKnownId() {
        assertEquals(listOf(a, c), PromptChipEdits.delete(abc, "b"))
    }

    @Test fun deleteUnknownIdUnchanged() {
        assertEquals(abc, PromptChipEdits.delete(abc, "zzz"))
    }

    @Test fun upsertReplaceInPlace() {
        assertEquals(listOf(a, b.copy(name = "B2"), c), PromptChipEdits.upsert(abc, b.copy(name = "B2")))
    }

    @Test fun upsertNewIdAppended() {
        val out = PromptChipEdits.upsert(abc, PromptChip("d", "D", "pd", ChipSource.TODAY_REVIEWS))
        assertEquals(4, out.size)
        assertEquals("d", out.last().id)
    }

    @Test fun newIdEmpty() {
        assertEquals("chip_1", PromptChipEdits.newId(emptyList()))
    }

    @Test fun newIdSmallestUnused() {
        assertEquals("chip_2", PromptChipEdits.newId(listOf(a.copy(id = "chip_1"), b.copy(id = "chip_3"))))
    }

    @Test fun canSaveCases() {
        assertTrue(PromptChipEdits.canSave("Quiz", "Quiz me"))
        assertFalse(PromptChipEdits.canSave("  ", "x"))
        assertFalse(PromptChipEdits.canSave("x", ""))
    }
}
