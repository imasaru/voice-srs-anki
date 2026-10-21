package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** The model call was cancelled by the caller's lambda. Not retried. */
class AiCancelled : Exception("cancelled")

/** HTTP status from the model server. The message is the status only. */
class AiHttpException(val code: Int) : IOException("HTTP $code")

/** The server rejected `stream: true`. The client retries once without streaming. */
class StreamRejected : Exception("stream rejected")

/** A finished call that produced nothing useful, or a client-side reject. */
class AiException(message: String, val retryable: Boolean) : Exception(message)

object AiTimeouts {
    const val CONNECT_MS = 5_000
    const val FIRST_TOKEN_MS = 90_000
    const val TOTAL_MS = 180_000
    const val IDLE_MS = 30_000
    const val TEMPERATURE = 0.2
    const val MAX_TOKENS = 300
}

/**
 * Blocking OpenAI-compatible client. HttpURLConnection + org.json.
 * [cancelled] is polled the same way EdgeSpeaker polls its cancel lambda.
 * Explanations are text only. This class never speaks.
 */
class AiClient {
    fun probe(baseUrl: String, apiKey: String, cancelled: () -> Boolean = { false }) {
        if (cancelled()) throw AiCancelled()
        val conn = open(baseUrl, apiKey, "GET", "/models")
        try {
            conn.connectTimeout = AiTimeouts.CONNECT_MS
            conn.readTimeout = AiTimeouts.FIRST_TOKEN_MS
            val code = conn.responseCode
            if (cancelled()) throw AiCancelled()
            if (code !in 200..299) throw AiHttpException(code)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * One chat completion. Tries SSE first. A stream rejection falls back to
     * a single JSON body. Think blocks are stripped before the text is returned.
     * [onDelta] receives the visible text so far, already think-stripped.
     */
    fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        user: String,
        maxTokens: Int = AiTimeouts.MAX_TOKENS,
        cancelled: () -> Boolean = { false },
        onDelta: (String) -> Unit = {}
    ): String {
        if (cancelled()) throw AiCancelled()
        return try {
            completeOnce(baseUrl, apiKey, model, system, user, maxTokens, stream = true, cancelled, onDelta)
        } catch (_: StreamRejected) {
            if (cancelled()) throw AiCancelled()
            completeOnce(baseUrl, apiKey, model, system, user, maxTokens, stream = false, cancelled, onDelta)
        }
    }

    private fun completeOnce(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        user: String,
        maxTokens: Int,
        stream: Boolean,
        cancelled: () -> Boolean,
        onDelta: (String) -> Unit
    ): String {
        val started = System.currentTimeMillis()
        val conn = open(baseUrl, apiKey, "POST", "/chat/completions")
        try {
            conn.doOutput = true
            conn.connectTimeout = AiTimeouts.CONNECT_MS
            conn.readTimeout = AiTimeouts.FIRST_TOKEN_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            val body = JSONObject()
                .put("model", model)
                .put("temperature", AiTimeouts.TEMPERATURE)
                .put("max_tokens", maxTokens)
                .put("stream", stream)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (cancelled()) throw AiCancelled()
            val code = conn.responseCode
            if (stream && code in STREAM_REJECT) throw StreamRejected()
            if (code !in 200..299) throw AiHttpException(code)
            val type = conn.contentType.orEmpty()
            val asSse = stream && (type.contains("event-stream") || type.isBlank())
            val text = if (asSse) {
                readSse(conn, started, cancelled, onDelta)
            } else {
                val raw = readBody(conn, started, cancelled)
                if (raw.trimStart().startsWith("data:")) {
                    val parser = SseParser()
                    parser.push(raw)
                    ThinkStrip.strip(parser.visibleText())
                } else {
                    val content = ChatJson.messageContent(raw)
                        ?: throw AiException("The model returned no text", retryable = true)
                    ThinkStrip.strip(content)
                }
            }
            if (text.isBlank()) throw AiException("The model returned no text", retryable = true)
            onDelta(text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun readSse(
        conn: HttpURLConnection,
        started: Long,
        cancelled: () -> Boolean,
        onDelta: (String) -> Unit
    ): String {
        conn.readTimeout = SLICE_MS
        val parser = SseParser()
        val reader = InputStreamReader(conn.inputStream, Charsets.UTF_8)
        val buf = CharArray(512)
        var waited = 0
        var idle = 0
        var saw = false
        val undecided = StringBuilder()
        var mode = 0 // 0 unknown, 1 sse, 2 json
        while (!parser.done) {
            if (cancelled()) throw AiCancelled()
            if (System.currentTimeMillis() - started > AiTimeouts.TOTAL_MS) {
                throw SocketTimeoutException("total")
            }
            val n = try {
                reader.read(buf)
            } catch (e: SocketTimeoutException) {
                if (!saw) {
                    waited += SLICE_MS
                    if (waited >= AiTimeouts.FIRST_TOKEN_MS) throw e
                } else {
                    idle += SLICE_MS
                    if (idle >= AiTimeouts.IDLE_MS) throw e
                }
                continue
            }
            if (n < 0) break
            val chunk = String(buf, 0, n)
            if (mode == 0) {
                undecided.append(chunk)
                val head = undecided.toString().trimStart()
                when {
                    head.startsWith("{") -> mode = 2
                    head.startsWith("data:") || head.startsWith(":") -> {
                        mode = 1
                        note(parser, parser.push(undecided.toString()), onDelta)
                        if (parser.rawText().isNotEmpty()) saw = true
                    }
                    undecided.length > 64 -> {
                        mode = 1
                        note(parser, parser.push(undecided.toString()), onDelta)
                        if (parser.rawText().isNotEmpty()) saw = true
                    }
                }
                continue
            }
            if (mode == 2) {
                undecided.append(chunk)
                continue
            }
            val pieces = parser.push(chunk)
            if (pieces.isNotEmpty()) {
                saw = true
                idle = 0
                onDelta(parser.visibleText())
            }
        }
        if (mode == 2 || (mode == 0 && undecided.toString().trimStart().startsWith("{"))) {
            val content = ChatJson.messageContent(undecided.toString())
                ?: throw AiException("The model returned no text", retryable = true)
            return ThinkStrip.strip(content)
        }
        return parser.visibleText()
    }

    private fun note(parser: SseParser, pieces: List<String>, onDelta: (String) -> Unit) {
        if (pieces.isNotEmpty()) onDelta(parser.visibleText())
    }

    private fun readBody(conn: HttpURLConnection, started: Long, cancelled: () -> Boolean): String {
        conn.readTimeout = SLICE_MS
        val reader = InputStreamReader(conn.inputStream, Charsets.UTF_8)
        val buf = CharArray(1024)
        val out = StringBuilder()
        var idle = 0
        while (true) {
            if (cancelled()) throw AiCancelled()
            if (System.currentTimeMillis() - started > AiTimeouts.TOTAL_MS) {
                throw SocketTimeoutException("total")
            }
            val n = try {
                reader.read(buf)
            } catch (e: SocketTimeoutException) {
                idle += SLICE_MS
                if (idle >= AiTimeouts.TOTAL_MS) throw e
                continue
            }
            if (n < 0) break
            idle = 0
            out.append(buf, 0, n)
        }
        return out.toString()
    }

    private fun open(baseUrl: String, apiKey: String, method: String, path: String): HttpURLConnection {
        val root = baseUrl.trim().trimEnd('/')
        val conn = (URL(root + path)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = AiTimeouts.CONNECT_MS
        conn.readTimeout = AiTimeouts.FIRST_TOKEN_MS
        if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
        return conn
    }

    companion object {
        private const val SLICE_MS = 1_000
        private val STREAM_REJECT = setOf(400, 404, 415, 422)

        fun classify(error: Exception): ExplainFail = when (error) {
            is AiCancelled -> ExplainFail.CANCEL
            is SocketTimeoutException -> ExplainFail.TIMEOUT
            is AiHttpException -> if (error.code in 500..599) ExplainFail.SERVER else ExplainFail.CLIENT
            is AiException -> if (error.retryable) ExplainFail.IO else ExplainFail.OTHER
            is IOException -> ExplainFail.IO
            else -> ExplainFail.OTHER
        }

        /** Safe to show. Never includes the API key. */
        fun publicMessage(error: Exception): String {
            val raw = error.message?.take(180)?.ifBlank { null } ?: error.javaClass.simpleName
            return raw
        }
    }
}
