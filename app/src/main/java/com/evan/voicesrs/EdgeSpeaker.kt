package com.evan.voicesrs

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Edge neural TTS over HTTP. Blocks until the MP3 finishes (or fails).
 * Clips are cached under cacheDir/neural-tts by (text, voice, rate).
 * Does not log the token or the request body.
 */
class EdgeSpeaker(private val context: Context) {

    /**
     * GET /health. [token] is sent when non-blank (servers that require it).
     * Both connect and read use [timeoutMs], about 3s, so a dead host cannot
     * stall the drill. Clip cache is not consulted.
     */
    fun healthy(
        baseUrl: String,
        token: String? = null,
        timeoutMs: Int = NeuralTts.HEALTH_TIMEOUT_MS
    ): Boolean {
        if (baseUrl.isBlank()) return false
        val url = baseUrl.trim().trimEnd('/') + "/health"
        val budget = timeoutMs.coerceIn(500, 10_000)
        return try {
            val conn = open(url, "GET", token, null, budget, budget)
            try {
                val code = conn.responseCode
                val body = streamText(conn, code)
                code in 200..299 && body.contains("\"ok\"") && body.contains("true")
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun fetchVoices(baseUrl: String, token: String): List<NeuralVoice> {
        if (baseUrl.isBlank() || token.isBlank()) return emptyList()
        val url = baseUrl.trim().trimEnd('/') + "/voices?locale=en-US,en-GB,ja-JP"
        return try {
            val conn = open(url, "GET", token, null)
            try {
                val code = conn.responseCode
                if (code !in 200..299) return emptyList()
                NeuralTts.parseVoices(streamText(conn, code))
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * @return true when playback ran to completion.
     * [cancelled] is polled so a drill stop does not wait out the clip.
     * [onPlaybackStart] fires once audio actually begins; a download or
     * prepare miss never calls it, so the voice label stays as it was.
     */
    fun speakBlocking(
        baseUrl: String,
        token: String,
        voice: String,
        rate: Float,
        text: String,
        cancelled: () -> Boolean = { false },
        onPlaybackStart: () -> Unit = {}
    ): Boolean {
        val spoken = text.trim()
        if (spoken.isEmpty() || spoken.length > NeuralTts.MAX_TEXT) return false
        if (baseUrl.isBlank() || token.isBlank()) return false
        if (cancelled()) return false
        val voiceName = voice.ifBlank { NeuralTts.DEFAULT_VOICE }
        val serverRate = NeuralTts.serverRate(rate)
        val file = cacheFile(spoken, voiceName, serverRate)
        if (!isPlayable(file)) {
            val tmp = File(file.parentFile, file.name + ".part")
            if (!download(baseUrl, token, voiceName, serverRate, spoken, tmp)) {
                tmp.delete()
                return false
            }
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            trimCache(file.parentFile)
        }
        if (cancelled()) return false
        val played = play(file, cancelled, onPlaybackStart)
        if (!played && !cancelled()) file.delete()
        return played
    }

    /**
     * 58E: speak the whole [text] in one request with Edge word marks.
     * Cache: <key>.mp3 + <key>.marks.json. [onMarks] gets the marks (maybe empty) before playback;
     * [onPosition] gets (positionMs, durationMs) about every 200 ms while playing.
     * @return true when playback ran to completion.
     */
    fun speakWithMarks(
        baseUrl: String,
        token: String,
        voice: String,
        rate: Float,
        text: String,
        cancelled: () -> Boolean = { false },
        onMarks: (List<ReadingTimeline.Mark>) -> Unit = {},
        onPlaybackStart: () -> Unit = {},
        onPosition: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean {
        val spoken = text.trim()
        if (spoken.isEmpty() || spoken.length > NeuralTts.MAX_TEXT) return false
        if (baseUrl.isBlank() || token.isBlank()) return false
        if (cancelled()) return false
        val voiceName = voice.ifBlank { NeuralTts.DEFAULT_VOICE }
        val serverRate = NeuralTts.serverRate(rate)
        val file = cacheFile(spoken, voiceName, serverRate)
        val marksFile = marksFile(file)
        if (!isPlayable(file) || !marksFile.isFile) {
            val tmp = File(file.parentFile, file.name + ".part")
            val marks = downloadWithMarks(baseUrl, token, voiceName, serverRate, spoken, tmp)
                ?: if (download(baseUrl, token, voiceName, serverRate, spoken, tmp)) emptyList() else null
            if (marks == null) { tmp.delete(); return false }
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            marksFile.writeText(NeuralTts.marksJson(marks))
            trimCache(file.parentFile)
        }
        val marks = try { NeuralTts.parseMarks(marksFile.readText()) } catch (_: Exception) { emptyList() }
        runCatching { onMarks(marks) }
        if (cancelled()) return false
        val played = play(file, cancelled, onPlaybackStart, onPosition)
        if (!played && !cancelled()) { file.delete(); marksFile.delete() }
        return played
    }

    private fun marksFile(mp3: File): File = File(mp3.parentFile, mp3.nameWithoutExtension + ".marks.json")

    /** POST /speak2; writes the decoded mp3 to [dest] and returns its marks, or null on any failure (including 404 before the server has /speak2). */
    private fun downloadWithMarks(baseUrl: String, token: String, voice: String, rate: Float, text: String, dest: File): List<ReadingTimeline.Mark>? {
        val endpoint = baseUrl.trim().trimEnd('/') + "/speak2"
        val payload = JSONObject().put("text", text).put("voice", voice).put("rate", rate.toDouble()).toString()
        val conn = try { open(endpoint, "POST", token, payload) } catch (_: Exception) { return null }
        return try {
            if (conn.responseCode !in 200..299) return null
            val body = JSONObject(conn.inputStream.readBytes().decodeToString())
            val bytes = android.util.Base64.decode(body.optString("audio", ""), android.util.Base64.DEFAULT)
            if (bytes.size < 64 || bytes[0] == '{'.code.toByte()) return null
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            NeuralTts.parseMarks(body.optJSONArray("marks")?.toString() ?: "[]")
        } catch (_: Exception) {
            dest.delete(); null
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheFile(text: String, voice: String, rate: Float): File {
        val dir = File(context.cacheDir, "neural-tts")
        dir.mkdirs()
        return File(dir, NeuralTts.cacheKey(text, voice, rate) + ".mp3")
    }

    private fun isPlayable(file: File): Boolean {
        if (!file.isFile || file.length() < 64) return false
        val head = ByteArray(1)
        val n = try {
            file.inputStream().use { it.read(head) }
        } catch (_: Exception) {
            return false
        }
        return n == 1 && head[0] != '{'.code.toByte()
    }

    private fun download(
        baseUrl: String,
        token: String,
        voice: String,
        rate: Float,
        text: String,
        dest: File
    ): Boolean {
        val endpoint = baseUrl.trim().trimEnd('/') + "/speak"
        val payload = JSONObject()
            .put("text", text)
            .put("voice", voice)
            .put("rate", rate.toDouble())
            .toString()
        val conn = try {
            open(endpoint, "POST", token, payload)
        } catch (_: Exception) {
            return false
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) return false
            val type = conn.contentType.orEmpty()
            val audio = type.isEmpty() || type.contains("audio") ||
                type.contains("mpeg") || type.contains("octet-stream")
            if (!audio) return false
            val bytes = conn.inputStream.readBytes()
            if (bytes.size < 64 || bytes[0] == '{'.code.toByte()) return false
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            true
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
        body: String?,
        connectTimeoutMs: Int = NeuralTts.CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = NeuralTts.READ_TIMEOUT_MS
    ): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.useCaches = false
        conn.instanceFollowRedirects = false
        if (!token.isNullOrBlank()) conn.setRequestProperty("X-Token", token)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return conn
    }

    private fun streamText(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.readBytes()?.decodeToString().orEmpty()
    }

    private fun play(
        file: File,
        cancelled: () -> Boolean,
        onPlaybackStart: () -> Unit = {},
        onPosition: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean {
        val player = MediaPlayer()
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(true)
        active = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { latch.countDown() }
            player.setOnErrorListener { _, _, _ ->
                ok.set(false)
                latch.countDown()
                true
            }
            player.prepare()
            if (cancelled()) return false
            player.start()
            runCatching { onPlaybackStart() }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(120_000)
            while (latch.count > 0) {
                if (cancelled()) {
                    try { player.stop() } catch (_: Exception) {}
                    return false
                }
                if (System.nanoTime() > deadline) return false
                try {
                    runCatching { onPosition(player.currentPosition.toLong(), player.duration.toLong()) }
                    latch.await(POSITION_POLL_MS, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    try { player.stop() } catch (_: Exception) {}
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return ok.get()
        } catch (_: Exception) {
            return false
        } finally {
            if (active === player) active = null
            try { player.reset() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
        }
    }

    private fun trimCache(dir: File?) {
        val files = dir?.listFiles { f -> f.isFile && f.extension == "mp3" } ?: return
        if (files.size <= NeuralTts.MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - NeuralTts.MAX_CACHE_FILES)
            .forEach {
                runCatching { it.delete() }
                runCatching { File(it.parentFile, it.nameWithoutExtension + ".marks.json").delete() }
            }
    }

    companion object {
        /** h99: position poll for the read-aloud highlight (was 200 ms, visible lag). */
        const val POSITION_POLL_MS = 40L
        @Volatile
        var active: MediaPlayer? = null

        fun stopActive() {
            val player = active ?: return
            try { player.stop() } catch (_: Exception) {}
        }
    }
}
