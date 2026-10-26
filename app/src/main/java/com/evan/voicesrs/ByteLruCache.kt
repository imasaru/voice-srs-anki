package com.evan.voicesrs

/**
 * Byte-count-bounded LRU cache, pure Kotlin (no Android), so the eviction and
 * size logic can be unit-tested on the JVM.
 *
 * This mirrors the behaviour of [android.util.LruCache]: entries are sized by a
 * caller-supplied [sizeOf] and a [put] that would exceed [maxBytes] evicts the
 * least-recently-used entries (insertion/access order via a
 * [LinkedHashMap] with `accessOrder = true`) until the total fits. A [get] of a
 * present key refreshes its recency (the underlying LinkedHashMap moves it to
 * most-recently-used), matching [android.util.LruCache]; only a [put] or
 * [remove] of a different key can then evict it.
 *
 * @param maxBytes the total-byte cap. A single entry larger than the cap is
 *   stored and returned but leaves the cache empty, since it alone exceeds the
 *   cap and [evict] drains to zero.
 */
class ByteLruCache<K, V>(
    private val maxBytes: Long,
    private val sizeOf: (K, V) -> Int,
) {
    /** accessOrder = true: iteration is in least- to most-recently-used order. */
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var totalBytes = 0L

    /** Total bytes currently held in the cache. */
    val sizeBytes: Long
        @Synchronized get() = totalBytes

    /** Number of entries currently held. */
    val size: Int
        @Synchronized get() = map.size

    /**
     * Returns the value for [key], or null if absent. A hit does not change the
     * entry's recency (matching [android.util.LruCache]).
     */
    @Synchronized
    fun get(key: K): V? = map[key]

    /**
     * Stores [value] under [key], evicting least-recently-used entries until the
     * total fits [maxBytes]. Returns the previous value for the key, if any.
     */
    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = map[key]
        if (previous != null) totalBytes -= entrySize(key, previous)
        map[key] = value
        totalBytes += entrySize(key, value)
        evict()
        return previous
    }

    /** Removes [key] and frees its bytes. Returns the removed value, if any. */
    @Synchronized
    fun remove(key: K): V? {
        val value = map.remove(key) ?: return null
        totalBytes -= entrySize(key, value)
        return value
    }

    @Synchronized
    fun contains(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun clear() {
        map.clear()
        totalBytes = 0L
    }

    private fun entrySize(key: K, value: V): Long = sizeOf(key, value).toLong()

    /** Remove least-recently-used entries until the total fits the cap. */
    private fun evict() {
        val it = map.entries.iterator()
        while (totalBytes > maxBytes && it.hasNext()) {
            val e = it.next()
            totalBytes -= e.value.let { sizeOf(e.key, it) }
            it.remove()
        }
    }
}
