package com.evan.voicesrs

import java.security.MessageDigest

/**
 * Cache key for one card side pair at one prompt version.
 * SHA-256 hex of `promptVersion‖stripHtml(front)‖stripHtml(back)`.
 * The separator is U+2016 (‖), so a field cannot collide with the version.
 */
object ContentHash {
    private const val SEP = "\u2016"
    private val HEX = "0123456789abcdef".toCharArray()

    fun of(promptVersion: Int, front: String, back: String): String {
        val payload = promptVersion.toString() + SEP +
            AnkiText.stripHtml(front) + SEP +
            AnkiText.stripHtml(back)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return hex(digest)
    }

    private fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xff
            out[i++] = HEX[v ushr 4]
            out[i++] = HEX[v and 0x0f]
        }
        return String(out)
    }
}
