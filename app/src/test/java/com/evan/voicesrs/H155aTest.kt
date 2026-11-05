package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** h155a: skip notice reaches the chat list for the skipped card and the next one. */
class H155aTest {
    private fun st(ord: Int, running: Boolean = true, skip: String = "Skipped.", at: Int = 3) =
        DrillUiState(drillRunning = running, questionOrdinal = ord, skipNotice = skip, skipNoticeOrdinal = at)

    @Test fun a_generalNoticeWins() {
        assertEquals("Chat failed", DrillNotice.chatNotice("Chat failed", st(3)))
    }

    @Test fun b_skipShownOnSkippedAndNextCard() {
        assertEquals("Skipped.", DrillNotice.chatNotice(null, st(3)))
        assertEquals("Skipped.", DrillNotice.chatNotice("  ", st(4)))
    }

    @Test fun c_skipGoneOutsideWindow() {
        assertNull(DrillNotice.chatNotice(null, st(5)))
        assertNull(DrillNotice.chatNotice(null, st(2)))
        assertNull(DrillNotice.chatNotice(null, st(0, at = 3)))
    }

    @Test fun d_hiddenWhenIdleOrBlank() {
        assertNull(DrillNotice.chatNotice(null, st(3, running = false)))
        assertNull(DrillNotice.chatNotice(null, st(3, skip = "  ")))
        assertNull(DrillNotice.chatNotice(null, DrillUiState()))
    }

    @Test fun e_lastSkipTextKept() {
        assertEquals(SkipQueue.SAY_LAST, DrillNotice.chatNotice(null, st(4, skip = SkipQueue.SAY_LAST)))
    }
}
