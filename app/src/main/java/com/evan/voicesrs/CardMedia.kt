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
    private val bitmaps = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

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

    /**
     * Fetch the bytes for every image referenced by [html] and return the resolved
     * filename map. Runs off the main thread; this is the only place CardMedia does I/O.
     * Prefetch before taking the grade gate so the network read never blocks on it.
     *
     * [fetcher] is an optional seam for tests; production uses the bound AnkiConnect.
     */
    fun preload(html: String, fetcher: (String) -> ByteArray? = boundFetcher()): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (name in AnkiText.imageRefs(html)) {
            val bytes = try {
                fetcher(name)
            } catch (_: Exception) {
                null
            } ?: continue
            val bmp = decode(bytes) ?: continue
            synchronized(lock) { bitmaps[name] = bmp }
            out[name] = name
        }
        return out
    }

    /**
     * Fetch image bytes for [html] and return the resolved filename map. Alias for
     * [preload]; the name signals that this is the fetch step to run outside the gate.
     */
    fun prefetch(html: String): Map<String, String> = preload(html)

    /**
     * Render [html] using an already-fetched [resolved] map. Performs no fetch, so it is
     * safe to call while holding the grade gate.
     */
    fun display(html: String, resolved: Map<String, String>): String =
        CardImages.displayHtml(html, resolved)

    /** Fetch then render. Callers holding a long lock should use [prefetch] + [display]. */
    fun display(html: String): String = display(html, prefetch(html))

    private fun boundFetcher(): (String) -> ByteArray? = { name ->
        synchronized(lock) { cache }?.let { c ->
            try {
                c.get(name)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun bitmap(name: String): Bitmap? = synchronized(lock) { bitmaps[name] }

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return@runCatching null
        var sample = 1
        while (w / sample > MAX_EDGE || h / sample > MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}
