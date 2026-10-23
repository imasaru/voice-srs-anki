package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookmarkStoreTest {
    @Test
    fun `json round trip and markdown is AI readable`() {
        val marks = listOf(
            Bookmark(1, 10L, 4L, 99L, "QUESTION 4", "Delta Lake is the ACID table format."),
            Bookmark(2, 20L, 5L, 100L, "CARD 5", "PIVOT rotates rows into columns.")
        )
        val decoded = BookmarkStore.decode(BookmarkStore.encode(marks))
        assertEquals(marks, decoded)
        val md = BookmarkStore.toMarkdown(marks)
        assertTrue(md.startsWith("# Voice SRS bookmarks"))
        assertTrue(md.contains("QUESTION 4"))
        assertTrue(md.contains("Delta Lake is the ACID table format."))
        assertTrue(md.contains("PIVOT rotates rows into columns."))
    }

    @Test
    fun `file store adds and removes`() {
        val dir = File.createTempFile("bmarks", "dir")
        dir.delete()
        dir.mkdirs()
        val first = BookmarkStore.add(
            dir,
            Bookmark(0, 1L, 2L, 3L, "QUESTION 1", "quote")
        )
        assertEquals(1, first.single().id)
        assertEquals(first, BookmarkStore.load(dir))
        val md = File(dir, BookmarkStore.MARKDOWN_NAME).readText()
        assertTrue(md.contains("quote"))
        BookmarkStore.remove(dir, first.single().id)
        assertTrue(BookmarkStore.load(dir).isEmpty())
    }

    @Test
    fun `bad json is empty`() {
        assertTrue(BookmarkStore.decode("nope").isEmpty())
        assertTrue(BookmarkStore.decode("{").isEmpty())
    }
}
