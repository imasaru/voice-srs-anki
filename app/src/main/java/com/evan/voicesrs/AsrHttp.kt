package com.evan.voicesrs

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * ASR calls to the example.local server. Same connection shape as [EdgeSpeaker]:
 * HttpURLConnection, X-Token, no caches, no redirects.
 */
object AsrHttp {
    const val CONNECT_MS = 3_000
    const val READ_MS = 15_000

    fun healthOk(base: String, token: String): Boolean {
        val conn = try {
            open(base.trimEnd('/') + "/asr/health", "GET", token, CONNECT_MS, CONNECT_MS)
        } catch (_: Exception) {
            return false
        }
        return try {
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    /** Null on HTTP or IO failure. Blank text is an empty string, not a failure. */
    fun transcribe(base: String, token: String, wav: ByteArray): String? {
        val conn = try {
            open(base.trimEnd('/') + "/asr/transcribe", "POST", token, CONNECT_MS, READ_MS)
        } catch (_: Exception) {
            return null
        }
        return try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.outputStream.use { it.write(wav) }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.use { it.readBytes() }.decodeToString()
            JSONObject(body).optString("text", "")
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Streams a zip to [dest]. False on any HTTP or IO failure. The model zip is larger than a transcript, so the read timeout is longer than [READ_MS]. */
    fun download(url: String, token: String, dest: File, readMs: Int = 120_000): Boolean {
        val conn = try {
            open(url, "GET", token, CONNECT_MS, readMs)
        } catch (_: Exception) {
            return false
        }
        return try {
            if (conn.responseCode !in 200..299) return false
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.isFile && dest.length() > 0L
        } catch (_: Exception) {
            dest.delete()
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun open(
        url: String,
        method: String,
        token: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.useCaches = false
        conn.instanceFollowRedirects = false
        if (!token.isNullOrBlank()) conn.setRequestProperty("X-Token", token)
        return conn
    }
}
