package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillSettingsTest {

    @Test
    fun `copy the settings surface uses`() {
        assertEquals(1, DrillSettings.MIN_BATCH)
        assertEquals(100, DrillSettings.MAX_BATCH)
        assertEquals("Clamped to 100", DrillSettings.CLAMPED_NOTE)
        assertEquals("cards", DrillSettings.SUFFIX_CARDS)
        assertEquals("minutes", DrillSettings.SUFFIX_MINUTES)
        assertEquals(
            "Offline sessions end when the batch runs out.",
            DrillSettings.GOAL_BATCH_HINT
        )
    }

    @Test
    fun `batch field alpha follows the offline switch`() {
        assertEquals(1f, DrillSettings.fieldAlpha(true), 0.001f)
        assertEquals(0.38f, DrillSettings.fieldAlpha(false), 0.001f)
        assertEquals(DrillSettings.ENABLED_ALPHA, DrillSettings.fieldAlpha(true), 0.001f)
        assertEquals(DrillSettings.DISABLED_ALPHA, DrillSettings.fieldAlpha(false), 0.001f)
    }

    @Test
    fun `in-range batch is kept and not rewritten`() {
        val twenty = DrillSettings.clampBatch("20", 5)
        assertEquals(20, twenty.value)
        assertEquals("20", twenty.text)
        assertFalse(twenty.rewriteField)
        assertFalse(twenty.cappedAtMax)

        val ends = listOf("1", "100", " 40 ")
        assertEquals(1, DrillSettings.clampBatch(ends[0], 20).value)
        assertEquals(100, DrillSettings.clampBatch(ends[1], 20).value)
        assertFalse(DrillSettings.clampBatch(ends[1], 20).cappedAtMax)
        assertEquals(40, DrillSettings.clampBatch(ends[2], 20).value)
        assertFalse(DrillSettings.clampBatch(ends[2], 20).rewriteField)
    }

    @Test
    fun `typed batch above 100 rewrites the field and notes the cap`() {
        val over = DrillSettings.clampBatch("101", 20)
        assertEquals(100, over.value)
        assertEquals("100", over.text)
        assertTrue(over.rewriteField)
        assertTrue(over.cappedAtMax)

        val huge = DrillSettings.clampBatch("2147483647", 20)
        assertEquals(100, huge.value)
        assertTrue(huge.cappedAtMax)
        assertTrue(huge.rewriteField)
    }

    @Test
    fun `typed batch below 1 rewrites to 1 without the max note`() {
        val zero = DrillSettings.clampBatch("0", 20)
        assertEquals(1, zero.value)
        assertEquals("1", zero.text)
        assertTrue(zero.rewriteField)
        assertFalse(zero.cappedAtMax)

        val negative = DrillSettings.clampBatch("-4", 20)
        assertEquals(1, negative.value)
        assertTrue(negative.rewriteField)
        assertFalse(negative.cappedAtMax)
    }

    @Test
    fun `blank or junk batch keeps the fallback and does not rewrite`() {
        val blank = DrillSettings.clampBatch("", 20)
        assertEquals(20, blank.value)
        assertFalse(blank.rewriteField)
        assertFalse(blank.cappedAtMax)

        val spaces = DrillSettings.clampBatch("   ", 15)
        assertEquals(15, spaces.value)
        assertFalse(spaces.rewriteField)

        val junk = DrillSettings.clampBatch("20 cards", 15)
        assertEquals(15, junk.value)
        assertFalse(junk.rewriteField)

        val overflow = DrillSettings.clampBatch("2147483648", 15)
        assertEquals(15, overflow.value)
        assertFalse(overflow.cappedAtMax)

        val highFallback = DrillSettings.clampBatch("", 250)
        assertEquals(100, highFallback.value)
        assertFalse(highFallback.rewriteField)
        assertFalse(highFallback.cappedAtMax)

        val lowFallback = DrillSettings.clampBatch("nope", 0)
        assertEquals(1, lowFallback.value)
        assertFalse(lowFallback.rewriteField)
    }

    @Test
    fun `goal amount shows for cards and minutes and hides when off`() {
        assertFalse(DrillSettings.showGoalAmount(GoalModes.OFF))
        assertFalse(DrillSettings.showGoalAmount(null))
        assertFalse(DrillSettings.showGoalAmount("nope"))
        assertTrue(DrillSettings.showGoalAmount(GoalModes.CARDS))
        assertTrue(DrillSettings.showGoalAmount("Cards"))
        assertTrue(DrillSettings.showGoalAmount(GoalModes.MINUTES))
        assertTrue(DrillSettings.showGoalAmount("minutes"))
        assertEquals("", DrillSettings.goalSuffix(GoalModes.OFF))
        assertEquals("cards", DrillSettings.goalSuffix(GoalModes.CARDS))
        assertEquals("cards", DrillSettings.goalSuffix("card"))
        assertEquals("minutes", DrillSettings.goalSuffix(GoalModes.MINUTES))
        assertEquals("minutes", DrillSettings.goalSuffix("time"))
    }

    @Test
    fun `hint when a cards goal outlasts the offline batch`() {
        assertTrue(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "30", "20", 20)
        )
        assertTrue(
            DrillSettings.showGoalBatchHint("Cards", true, " 21 ", " 20 ", 10)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "20", "20", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "10", "20", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.MINUTES, true, "30", "20", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.OFF, true, "30", "20", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, false, "30", "20", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "", "20", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "abc", "20", 20)
        )
    }

    @Test
    fun `hint uses the clamped batch and the saved fallback`() {
        // 200 is stored as 100, so a goal of 150 does outlast the batch.
        assertTrue(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "150", "200", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "50", "200", 20)
        )
        assertTrue(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "30", "", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "20", "", 20)
        )
        assertFalse(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "10", "nope", 20)
        )
        assertTrue(
            DrillSettings.showGoalBatchHint(GoalModes.CARDS, true, "2", "0", 20)
        )
    }
}
