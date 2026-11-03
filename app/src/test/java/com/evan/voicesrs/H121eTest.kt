package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** h121e: bookmark toggle, add dedupe by turn, cached turnIds. */
class H121eTest {
    private fun tempDir() = File.createTempFile("h121e", "d").apply { delete(); mkdirs() }
    private fun mark(turnId: Long) = Bookmark(0L, 1L, turnId, 0L, "Q", "quote")

    @Test fun `toggle adds then removes the same turn`() {
        val dir = tempDir()
        assertTrue(BookmarkStore.toggle(dir, mark(7L)))
        assertEquals(1, BookmarkStore.load(dir).size)
        assertEquals(setOf(7L), BookmarkStore.turnIds(dir))
        assertFalse(BookmarkStore.toggle(dir, mark(7L)))
        assertTrue(BookmarkStore.load(dir).isEmpty())
        assertTrue(BookmarkStore.turnIds(dir).isEmpty())
    }

    @Test fun `add dedupes a repeated non-zero turn`() {
        val dir = tempDir()
        BookmarkStore.add(dir, mark(5L))
        BookmarkStore.add(dir, mark(5L))
        assertEquals(1, BookmarkStore.load(dir).size)
    }

    @Test fun `add keeps duplicates for turn 0`() {
        val dir = tempDir()
        BookmarkStore.add(dir, mark(0L))
        BookmarkStore.add(dir, mark(0L))
        assertEquals(2, BookmarkStore.load(dir).size)
    }
}
