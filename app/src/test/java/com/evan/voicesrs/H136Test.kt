package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class H136Test {
    private fun temp(f: (File) -> Unit) {
        val d = Files.createTempDirectory("h136").toFile()
        try { f(d) } finally { d.deleteRecursively() }
    }

    @Test fun `toggleLive adds on empty dir`() = temp { d ->
        assertTrue(BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis()))
        val b = BookmarkStore.load(d).single()
        assertEquals(1, BookmarkStore.load(d).size)
        assertEquals(0L, b.turnId)
        assertEquals(7L, b.cardId)
        assertEquals("What is Delta Lake?", b.quote)
    }

    @Test fun `second toggleLive trims quote and removes`() = temp { d ->
        BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis())
        assertFalse(BookmarkStore.toggleLive(d, 7L, " What is Delta Lake? ", System.currentTimeMillis()))
        assertTrue(BookmarkStore.load(d).isEmpty())
    }

    @Test fun `isLiveMarked reflects toggle`() = temp { d ->
        assertFalse(BookmarkStore.isLiveMarked(d, 7L, "What is Delta Lake?"))
        BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis())
        assertTrue(BookmarkStore.isLiveMarked(d, 7L, "What is Delta Lake?"))
        BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis())
        assertFalse(BookmarkStore.isLiveMarked(d, 7L, "What is Delta Lake?"))
    }

    @Test fun `another card keeps first`() = temp { d ->
        BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis())
        BookmarkStore.toggleLive(d, 8L, "Other question", System.currentTimeMillis())
        assertEquals(2, BookmarkStore.load(d).size)
    }

    @Test fun `past-turn bookmark survives`() = temp { d ->
        BookmarkStore.add(d, Bookmark(5, 1L, 42L, 7L, "Q", "What is Delta Lake?"))
        BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis())
        BookmarkStore.toggleLive(d, 7L, "What is Delta Lake?", System.currentTimeMillis())
        assertTrue(BookmarkStore.load(d).any { it.id == 5L && it.turnId == 42L })
    }

    @Test fun `source wires it`() {
        assertTrue(File("src/main/java/com/evan/voicesrs/ChatAdapter.kt").readText().contains("isBookmarkedLive(row)"))
        assertTrue(File("src/main/java/com/evan/voicesrs/ChatPane.kt").readText().contains("BookmarkStore.toggleLive("))
    }
}
