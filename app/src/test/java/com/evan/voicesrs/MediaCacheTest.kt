package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheTest {

    private fun content(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun `fetch runs once for a repeated get`() {
        var calls = 0
        val cache = MediaCache(1000L) { calls++; "hello".toByteArray() }
        val a = cache.get("a.png")
        val b = cache.get("a.png")
        assertEquals(1, calls)
        assertTrue(a != null && a.contentEquals(b))
    }

    @Test
    fun `evicts the least recently used entry`() {
        val cache = MediaCache(100L) { content(40) }
        cache.get("a")
        cache.get("b")
        cache.get("c")
        assertFalse(cache.contains("a"))
        assertTrue(cache.contains("b"))
        assertTrue(cache.contains("c"))
        assertEquals(80L, cache.sizeBytes)
    }

    @Test
    fun `refreshing an entry changes what gets evicted`() {
        val cache = MediaCache(100L) { content(40) }
        cache.get("a")
        cache.get("b")
        cache.get("a") // refresh a, so b becomes the least recently used
        cache.get("c")
        assertFalse(cache.contains("b"))
        assertTrue(cache.contains("a"))
        assertTrue(cache.contains("c"))
    }

    @Test
    fun `an oversize item is returned but not cached`() {
        var calls = 0
        val cache = MediaCache(10L) { calls++; content(50) }
        val a = cache.get("big.png")
        val b = cache.get("big.png")
        assertTrue(a != null && a.contentEquals(b))
        assertEquals(2, calls)
        assertFalse(cache.contains("big.png"))
        assertEquals(0L, cache.sizeBytes)
    }

    @Test
    fun `a missing file is not fetched again until misses are cleared`() {
        var calls = 0
        val cache = MediaCache(1000L) { calls++; if (it == "gone") null else content(8) }
        assertNull(cache.get("gone"))
        assertNull(cache.get("gone"))
        assertEquals(1, calls)
        cache.clearMisses()
        assertNull(cache.get("gone"))
        assertEquals(2, calls)
    }

    @Test
    fun `size bytes tracks the cached total`() {
        val cache = MediaCache(1000L) { content(30) }
        assertEquals(0L, cache.sizeBytes)
        cache.get("a")
        cache.get("b")
        assertEquals(60L, cache.sizeBytes)
        cache.clear()
        assertEquals(0L, cache.sizeBytes)
    }

    @Test
    fun `key is stable for equal bytes and differs for different bytes`() {
        val a = content(5)
        val b = content(6)
        assertEquals(MediaCache.key("f", a), MediaCache.key("f", a))
        assertNotEquals(MediaCache.key("f", a), MediaCache.key("f", b))
        assertNotEquals(MediaCache.key("f", a), MediaCache.key("g", a))
        val k = MediaCache.key("f", a)
        assertTrue(k.startsWith("f#"))
        assertEquals(12, k.substringAfter('#').length)
    }
}
