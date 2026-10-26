package com.evan.voicesrs

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Process-wide media bytes and decoded bitmaps for card images.
 * [preload] must run off the main thread.
 */
object CardMedia {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val MAX_EDGE = 1280
    private val lock = Any()
    private var cache: MediaCache? = null
    /**
     * Byte-count-bounded LRU of decoded bitmaps (pure Kotlin, unit-tested in
     * ByteLruCacheTest). Replaces the previous unbounded LinkedHashMap so a
     * large deck can no longer hold every decoded image in memory; entries are
     * evicted by byte count once total exceeds ~1/8 of the process max memory.
     */
    private val bitmaps = ByteLruCache<String, Bitmap>(Runtime.getRuntime().maxMemory() / 8) { _, bmp -> bmp.byteCount }

    fun bind(anki: AnkiConnect) {
        synchronized(lock) {
            cache = MediaCache(MAX_BYTES) { name ->
                try {
                    anki.retrieveMediaFile(name)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun preload(html: String): Map<String, String> {
        val c = synchronized(lock) { cache } ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (name in AnkiText.imageRefs(html)) {
            val bytes = try {
                c.get(name)
            } catch (_: Exception) {
                null
            } ?: continue
            val bmp = decode(bytes) ?: continue
            synchronized(lock) { bitmaps.put(name, bmp) }
            out[name] = name
        }
        return out
    }

    fun bitmap(name: String): Bitmap? = synchronized(lock) { bitmaps.get(name) }

    fun display(html: String): String = CardImages.displayHtml(html, preload(html))

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / sample > MAX_EDGE || h / sample > MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
