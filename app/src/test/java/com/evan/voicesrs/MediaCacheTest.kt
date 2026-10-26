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

class ByteLruCacheTest {

    private data class Item(val bytes: Int)

    private fun cache(max: Long) =
        ByteLruCache<String, Item>(max) { _, it -> it.bytes }

    @Test
    fun `evicts least recently used by byte count`() {
        val c = cache(100L)
        c.put("a", Item(40))
        c.put("b", Item(40))
        c.put("c", Item(40))
        assertEquals(80L, c.sizeBytes)
        assertEquals(2, c.size)
        assertFalse(c.contains("a"))
        assertTrue(c.contains("b"))
        assertTrue(c.contains("c"))
    }

    @Test
    fun `putting a bigger item evicts older entries until it fits`() {
        val c = cache(100L)
        c.put("a", Item(30))
        c.put("b", Item(30))
        c.put("big", Item(90))
        assertEquals(90L, c.sizeBytes)
        assertEquals(1, c.size)
        assertFalse(c.contains("a"))
        assertFalse(c.contains("b"))
        assertTrue(c.contains("big"))
    }

    @Test
    fun `get refreshes recency so a later put evicts a different entry`() {
        val c = cache(100L)
        c.put("a", Item(40))
        c.put("b", Item(40))
        c.get("a")
        c.put("c", Item(40))
        assertTrue(c.contains("a"))
        assertFalse(c.contains("b"))
        assertTrue(c.contains("c"))
    }

    @Test
    fun `size bytes tracks the cached total`() {
        val c = cache(1000L)
        assertEquals(0L, c.sizeBytes)
        c.put("a", Item(30))
        c.put("b", Item(30))
        assertEquals(60L, c.sizeBytes)
        c.clear()
        assertEquals(0L, c.sizeBytes)
    }

    @Test
    fun `replacing a key keeps only the new size`() {
        val c = cache(1000L)
        c.put("a", Item(30))
        c.put("a", Item(50))
        assertEquals(50L, c.sizeBytes)
    }

    @Test
    fun `remove frees the bytes it held`() {
        val c = cache(1000L)
        c.put("a", Item(30))
        c.remove("a")
        assertEquals(0L, c.sizeBytes)
        assertNull(c.get("a"))
    }
}
