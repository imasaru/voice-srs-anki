package com.evan.voicesrs

import java.security.MessageDigest

/**
 * In-memory LRU cache of collection media bytes, pure Kotlin (no Android).
 *
 * Lookups miss to a [fetch] lambda (in the app this is
 * [com.evan.voicesrs.AnkiConnect.retrieveMediaFile], which returns null for a
 * missing file). Non-null results are cached while they fit under [maxBytes];
 * a single item larger than the cap is returned but not cached. A null result
 * is not cached as data but remembered in a miss set so [fetch] is not repeated
 * until [clearMisses]. Eviction drops the least-recently-used entries until the
 * total stays within the cap; accessing an entry refreshes its recency.
 */
class MediaCache(
    private val maxBytes: Long,
    private val fetch: (String) -> ByteArray?
) {
    private data class Entry(val bytes: ByteArray)

    /** accessOrder = true: iteration is in least- to most-recently-used order. */
    private val map = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val misses = HashSet<String>()
    private var totalBytes = 0L

    /** Bytes currently held in the cache. */
    val sizeBytes: Long
        @Synchronized get() = totalBytes

    @Synchronized
    fun get(filename: String): ByteArray? {
        val entry = map[filename]
        if (entry != null) return entry.bytes
        if (filename in misses) return null
        val data = fetch(filename) ?: run {
            misses.add(filename)
            return null
        }
        if (data.size.toLong() <= maxBytes) {
            map[filename] = Entry(data)
            totalBytes += data.size
            evict()
        }
        return data
    }

    @Synchronized
    fun contains(filename: String): Boolean = map.containsKey(filename)

    @Synchronized
    fun clear() {
        map.clear()
        misses.clear()
        totalBytes = 0L
    }

    @Synchronized
    fun clearMisses() {
        misses.clear()
    }

    /** Remove least-recently-used entries until the total fits the cap. */
    private fun evict() {
        val it = map.entries.iterator()
        while (totalBytes > maxBytes && it.hasNext()) {
            val e = it.next()
            totalBytes -= e.value.bytes.size
            it.remove()
        }
    }

    companion object {
        /**
         * Content-addressed key for one file: the filename, a '#', and the first
         * 12 hex chars of the SHA-256 of the bytes. Stable for equal bytes and
         * different for different bytes.
         */
        fun key(filename: String, bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hex = "0123456789abcdef"
            val sb = StringBuilder(12)
            for (i in 0 until 6) {
                val b = digest[i].toInt() and 0xff
                sb.append(hex[b ushr 4])
                sb.append(hex[b and 0x0f])
            }
            return filename + "#" + sb
        }
    }
}
