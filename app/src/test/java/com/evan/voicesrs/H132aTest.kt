package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H132aTest {

    @Test
    fun rememberPutsNewestFirstAndDedupesTrailingSlash() {
        assertEquals(
            listOf("http://a/", "http://b"),
            SavedUrls.remember(listOf("http://b", "http://a"), "http://a/")
        )
    }

    @Test
    fun rememberIgnoresBlank() {
        assertEquals(emptyList<String>(), SavedUrls.remember(emptyList(), "   "))
    }

    @Test
    fun rememberKeepsAtMostMax() {
        var list = emptyList<String>()
        for (i in 0..10) list = SavedUrls.remember(list, "http://s$i")
        assertEquals(SavedUrls.MAX, list.size)
        assertEquals("http://s10", list.first())
    }

    @Test
    fun decodeNullIsEmptyAndDropsBlanks() {
        assertEquals(emptyList<String>(), SavedUrls.decode(null))
        assertEquals(
            listOf("http://a", "http://b"),
            SavedUrls.decode("http://a\n\n   \nhttp://b")
        )
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val list = listOf("http://a", "http://b", "http://c/")
        assertEquals(list, SavedUrls.decode(SavedUrls.encode(list)))
    }

    @Test
    fun isAddRowTrueOnlyAtSavedSize() {
        val saved = listOf("http://a", "http://b")
        assertFalse(SavedUrls.isAddRow(0, saved))
        assertFalse(SavedUrls.isAddRow(1, saved))
        assertTrue(SavedUrls.isAddRow(2, saved))
    }

    @Test
    fun keepPreviousTrueOnlyForBlankWhileAddingNew() {
        assertTrue(SavedUrls.keepPrevious("", addingNew = true))
        assertFalse(SavedUrls.keepPrevious("http://a", addingNew = true))
        assertFalse(SavedUrls.keepPrevious("", addingNew = false))
    }
}
