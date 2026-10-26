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
            synchronized(lock) { bitmaps[name] = bmp }
            out[name] = name
        }
        return out
    }

    fun bitmap(name: String): Bitmap? = synchronized(lock) { bitmaps[name] }

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
