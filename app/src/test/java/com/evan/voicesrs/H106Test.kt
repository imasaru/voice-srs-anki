package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

/** h106: the wide layout embeds the answer/grade hint into the live row, so
 *  ChatFeed.rows() must set ChatRow.Live.guide to SkipQueue.guide() (0 = hidden);
 *  the narrow layout leaves it 0 because the bottom bar owns the hint. */
class H106Test {

    private fun guideRow(embed: Boolean, showBack: Boolean, silent: Boolean): ChatRow.Live {
        val rows = ChatFeed.rows(
            emptyList(),
            DrillUiState(frontHtml = "Q", backHtml = "A", showBack = showBack,
                silentSession = silent, drillRunning = true),
            embedControls = embed
        )
        return rows.filterIsInstance<ChatRow.Live>().single()
    }

    @Test
    fun `embedded guide mirrors SkipQueue and narrows stay hidden`() {
        for (showBack in listOf(false, true)) {
            for (silent in listOf(false, true)) {
                assertEquals(SkipQueue.guide(true, showBack, silent), guideRow(true, showBack, silent).guide)
                assertEquals(0, guideRow(false, showBack, silent).guide)
            }
        }
        // concrete mapping: answer_guide=3, grade_guide=1, grade_guide_tap=2, hidden=0
        assertEquals(3, guideRow(true, false, false).guide)
        assertEquals(1, guideRow(true, true, false).guide)
        assertEquals(2, guideRow(true, true, true).guide)
        assertEquals(0, guideRow(true, false, true).guide)
    }
}
