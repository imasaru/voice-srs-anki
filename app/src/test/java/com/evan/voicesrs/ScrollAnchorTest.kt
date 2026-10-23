package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollAnchorTest {

    @Test
    fun `follow mode anchors the question and a manual scroll does not`() {
        assertTrue(ScrollAnchor.following(atBottom = true, holdingOrdinal = 0, liveVisible = true))
        assertFalse(ScrollAnchor.following(atBottom = false, holdingOrdinal = 4, liveVisible = false))
        assertFalse(ScrollAnchor.following(atBottom = false, holdingOrdinal = 0, liveVisible = true))
        assertFalse(ScrollAnchor.following(atBottom = true, holdingOrdinal = 4, liveVisible = true))
        assertFalse(ScrollAnchor.following(atBottom = true, holdingOrdinal = 0, liveVisible = false))

        assertEquals(120, ScrollAnchor.scrollDelta(following = true, beforeTop = 400, afterTop = 520))
        assertEquals(-40, ScrollAnchor.scrollDelta(following = true, beforeTop = 400, afterTop = 360))
        assertEquals(0, ScrollAnchor.scrollDelta(following = true, beforeTop = 400, afterTop = 400))
        assertEquals(0, ScrollAnchor.scrollDelta(following = false, beforeTop = 400, afterTop = 900))
    }

    @Test
    fun `advance pins the question top for short and tall cards`() {
        assertEquals(0, ScrollAnchor.pinTopDelta(questionTop = 0, viewportTop = 0))
        assertEquals(48, ScrollAnchor.pinTopDelta(questionTop = 48, viewportTop = 0))
        assertEquals(48, ScrollAnchor.pinTopDelta(questionTop = 48, viewportTop = 0))
        assertEquals(800, ScrollAnchor.pinTopDelta(questionTop = 800, viewportTop = 0))
        assertEquals(0, ScrollAnchor.pinTopDelta(questionTop = 12, viewportTop = 12))
    }

    @Test
    fun `streaming paints coalesce to about 100 milliseconds`() {
        assertEquals(100L, ScrollAnchor.COALESCE_MS)
        assertTrue(ScrollAnchor.paintNow(nowMs = 0L, lastPaintMs = -1L, streamingOnly = true))
        assertFalse(ScrollAnchor.paintNow(nowMs = 50L, lastPaintMs = 0L, streamingOnly = true))
        assertTrue(ScrollAnchor.paintNow(nowMs = 100L, lastPaintMs = 0L, streamingOnly = true))
        assertTrue(ScrollAnchor.paintNow(nowMs = 40L, lastPaintMs = 0L, streamingOnly = false))
    }
}