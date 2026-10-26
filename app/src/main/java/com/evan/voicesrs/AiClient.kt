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
class AiHttpException(val code: Int, val body: String = "") : IOException("HTTP $code")

/** The server rejected the tools schema. General chat falls back to the report chip. */
class ToolsRejected(message: String) : IOException(message)

/** The server rejected `stream: true`. The client retries once without streaming. */
class StreamRejected : Exception("stream rejected")

/**
 * Streaming finished with no visible `content` deltas (reasoning-only, think-only,
 * or keepalive). The client retries once without streaming.
 */
class EmptyStream : Exception("empty stream")

/** A finished call that produced nothing useful, or a client-side reject. */
class AiException(message: String, val retryable: Boolean) : Exception(message)

object AiTimeouts {
    const val CONNECT_MS = 10_000
    const val FIRST_TOKEN_MS = 180_000
    const val TOTAL_MS = 300_000
    const val IDLE_MS = 60_000
    const val TEMPERATURE = 0.2

    /** Explanation completions. Reasoning models need room past the visible answer. */
    const val MAX_TOKENS = 4_096

    /** Settings Test button. Must leave room for a reasoning preamble. */
    const val TEST_MAX_TOKENS = 2_048

    /**
     * Per-provider ceiling. 0 keeps [MAX_TOKENS] and [TEST_MAX_TOKENS].
     * A positive value replaces the requested budget for every completion.
     */
    const val PROVIDER_MAX_TOKENS = 0

    fun budget(requested: Int): Int {
        if (PROVIDER_MAX_TOKENS > 0) return PROVIDER_MAX_TOKENS
        return requested
    }
}

/** User-visible model failures. Reasoning is never the answer. */
object AiMessages {
    const val NO_TEXT = "Model returned no text"
    const val BUDGET =
        "Model used its whole token budget on reasoning; try a :no-think model or raise max tokens"
    const val THINKING = "Thinking…"

    fun require(content: String, finishReason: String?): String {
        val text = ThinkStrip.strip(content)
        if (text.isNotBlank()) return text
        if (finishReason == "length") throw AiException(BUDGET, retryable = false)
        throw AiException(NO_TEXT, retryable = false)
    }
}

/**
 * Blocking OpenAI-compatible client. HttpURLConnection + org.json.
 * [cancelled] is polled the same way EdgeSpeaker polls its cancel lambda.
 * Explanations are text only. This class never speaks.
 */
class AiClient {
    @Volatile var lastSawToolCalls: Boolean = false
        private set

    fun probe(baseUrl: String, apiKey: String, cancelled: () -> Boolean = { false }) {
        if (cancelled()) throw AiCancelled()
        val conn = open(baseUrl, apiKey, "GET", "/models")
        try {
            conn.connectTimeout = AiTimeouts.CONNECT_MS
            conn.readTimeout = AiTimeouts.CONNECT_MS
            val code = conn.responseCode
            if (cancelled()) throw AiCancelled()
            if (code !in 200..299) throw AiHttpException(code, readError(conn))
        } finally {
            conn.disconnect()
        }
    }

    /** Model ids from `GET /models`. Empty when the body is not a model list. */
    fun listModels(baseUrl: String, apiKey: String, cancelled: () -> Boolean = { false }): List<String> {
        if (cancelled()) throw AiCancelled()
        val conn = open(baseUrl, apiKey, "GET", "/models")
        try {
            conn.connectTimeout = AiTimeouts.CONNECT_MS
            conn.readTimeout = AiTimeouts.CONNECT_MS
            val code = conn.responseCode
            if (cancelled()) throw AiCancelled()
            if (code !in 200..299) throw AiHttpException(code, readError(conn))
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return ModelList.parse(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * One chat completion. Tries SSE first. A stream rejection falls back to
     * a single JSON body. Think blocks are stripped before the text is returned.
     * Reasoning tokens reset the idle timer and call [onThinking]. They are not
     * part of the returned text. [onDelta] receives the visible text so far.
     */
    fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        user: String,
        maxTokens: Int = AiTimeouts.MAX_TOKENS,
        cancelled: () -> Boolean = { false },
        onThinking: () -> Unit = {},
        onDelta: (String) -> Unit = {}
    ): String {
        if (cancelled()) throw AiCancelled()
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        return completeMessages(baseUrl, apiKey, model, messages, maxTokens, cancelled, onThinking, onDelta)
    }

    /**
     * Multi-turn chat. [system] is the first message. [messages] are the
     * user and assistant turns that follow (the card, then the thread).
     */
    fun completeChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        messages: List<PromptBuilder.ChatMsg>,
        maxTokens: Int = AiTimeouts.MAX_TOKENS,
        cancelled: () -> Boolean = { false },
        onThinking: () -> Unit = {},
        tools: JSONArray? = null,
        onTool: ((name: String, args: String) -> String)? = null,
        maxToolRounds: Int = StudyTools.MAX_ROUNDS,
        onDelta: (String) -> Unit = {}
    ): String {
        if (cancelled()) throw AiCancelled()
        lastSawToolCalls = false
        val array = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (message in messages) {
            array.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        if (tools != null && tools.length() > 0 && onTool != null) {
            return completeWithTools(
                baseUrl, apiKey, model, array, maxTokens, tools, onTool, maxToolRounds, cancelled, onThinking, onDelta
            )
        }
        return completeMessages(baseUrl, apiKey, model, array, maxTokens, cancelled, onThinking, onDelta)
    }

    /**
     * Non-stream tool loop. Max [maxToolRounds] rounds of tool_calls.
     * HTTP 400 mentioning tools becomes [ToolsRejected].
     */
    private fun completeWithTools(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        tools: JSONArray,
        onTool: (String, String) -> String,
        maxToolRounds: Int,
        cancelled: () -> Boolean,
        onThinking: () -> Unit,
        onDelta: (String) -> Unit
    ): String {
        val budget = AiTimeouts.budget(maxTokens)
        val rounds = maxToolRounds.coerceIn(1, StudyTools.MAX_ROUNDS)
        lastSawToolCalls = false
        repeat(rounds) {
            if (cancelled()) throw AiCancelled()
            val parsed = try {
                completeJson(baseUrl, apiKey, model, messages, budget, tools, cancelled)
            } catch (e: AiHttpException) {
                if (StudyTools.toolsRejected(e.code, e.body)) throw ToolsRejected(e.body)
                throw e
            }
            val textExtracted =
                if (parsed.toolCalls.isEmpty()) TextToolCalls.extract(parsed.content.orEmpty()) else null
            val toolCalls: List<ChatJson.ToolCall> =
                if (parsed.toolCalls.isNotEmpty()) parsed.toolCalls else textExtracted?.first.orEmpty()
            val assistantContent: String? =
                if (parsed.toolCalls.isNotEmpty()) parsed.content else textExtracted?.second
            if (toolCalls.isEmpty()) {
                val text = AiMessages.require(parsed.content.orEmpty(), parsed.finishReason)
                val clipped = SelfTalk.clip(TextToolCalls.stripLeftover(text))
                onDelta(clipped)
                return clipped
            }
            lastSawToolCalls = true
            val assistant = JSONObject().put("role", "assistant")
            if (!assistantContent.isNullOrBlank()) assistant.put("content", assistantContent)
            val calls = JSONArray()
            for (call in toolCalls) {
                calls.put(
                    JSONObject()
                        .put("id", call.id)
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject().put("name", call.name).put("arguments", call.arguments)
                        )
                )
            }
            assistant.put("tool_calls", calls)
            messages.put(assistant)
            for (call in toolCalls) {
                if (cancelled()) throw AiCancelled()
                val result = try {
                    onTool(call.name, call.arguments)
                } catch (e: Exception) {
                    JSONObject().put("error", e.javaClass.simpleName).toString()
                }
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", result)
                )
            }
        }
        val last = completeJson(baseUrl, apiKey, model, messages, budget, null, cancelled)
        val text = AiMessages.require(last.content.orEmpty(), last.finishReason)
        val clipped = SelfTalk.clip(TextToolCalls.stripLeftover(text))
        onDelta(clipped)
        return clipped
    }

    private fun completeJson(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        tools: JSONArray?,
        cancelled: () -> Boolean
    ): ChatJson.Completion {
        val started = System.currentTimeMillis()
        val conn = open(baseUrl, apiKey, "POST", "/chat/completions")
        try {
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = AiTimeouts.CONNECT_MS
            conn.readTimeout = AiTimeouts.FIRST_TOKEN_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty("Expect", "")
            val body = AiRequest.completionBody(model, messages, maxTokens, stream = false, tools = tools)
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            if (cancelled()) throw AiCancelled()
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = readError(conn)
                throw AiHttpException(code, err)
            }
            val raw = readBody(conn, started, cancelled)
            return ChatJson.parse(raw) ?: throw AiException(AiMessages.NO_TEXT, retryable = false)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * One System One decision. Short timeouts. The caller parses the body.
     * Does not log the API key or the request.
     */
    fun postDecision(
        baseUrl: String,
        apiKey: String,
        jsonBody: String,
        cancelled: () -> Boolean = { false }
    ): String {
        if (cancelled()) throw AiCancelled()
        val url = OwnWordsJudge.endpoint(baseUrl)
        if (url.isBlank()) throw AiException("Decision URL is blank", retryable = false)
        val conn = openAbsolute(url, apiKey, "POST")
        try {
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = OwnWordsJudge.CONNECT_MS
            conn.readTimeout = OwnWordsJudge.READ_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty("Expect", "")
            val bytes = jsonBody.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            if (cancelled()) throw AiCancelled()
            val code = conn.responseCode
            if (code !in 200..299) throw AiHttpException(code, readError(conn))
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun completeMessages(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        cancelled: () -> Boolean,
        onThinking: () -> Unit,
        onDelta: (String) -> Unit
    ): String {
        val budget = AiTimeouts.budget(maxTokens)
        return try {
            TextToolCalls.stripLeftover(
                completeOnce(baseUrl, apiKey, model, messages, budget, stream = true, cancelled, onThinking, onDelta)
            )
        } catch (e: Exception) {
            if (e !is StreamRejected && e !is EmptyStream) throw e
            if (cancelled()) throw AiCancelled()
            TextToolCalls.stripLeftover(
                completeOnce(baseUrl, apiKey, model, messages, budget, stream = false, cancelled, onThinking, onDelta)
            )
        }
    }

    private fun completeOnce(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        stream: Boolean,
        cancelled: () -> Boolean,
        onThinking: () -> Unit,
        onDelta: (String) -> Unit
    ): String {
        val started = System.currentTimeMillis()
        val conn = open(baseUrl, apiKey, "POST", "/chat/completions")
        val sink: (String) -> Unit = { onDelta(SelfTalk.clip(it)) }
        try {
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = AiTimeouts.CONNECT_MS
            conn.readTimeout = AiTimeouts.FIRST_TOKEN_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            conn.setRequestProperty("Connection", "close")
            // Send the body in one known-length write. Chunked/Expect: 100-continue
            // deadlocks a naive reverse proxy (the emulator relay) until read timeout.
            conn.setRequestProperty("Expect", "")
            val body = AiRequest.completionBody(model, messages, maxTokens, stream)
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            if (cancelled()) throw AiCancelled()
            val code = conn.responseCode
            if (stream && code in STREAM_REJECT) throw StreamRejected()
            if (code !in 200..299) throw AiHttpException(code, readError(conn))
            val type = conn.contentType.orEmpty()
            val eventStream = type.contains("event-stream")
            val jsonType = type.contains("json")
            val text = if (eventStream || (stream && !jsonType)) {
                readSse(conn, started, cancelled, onThinking, sink)
            } else {
                finishBody(readBody(conn, started, cancelled))
            }
            val clipped = SelfTalk.clip(text)
            sink(clipped)
            return clipped
        } finally {
            conn.disconnect()
        }
    }

    private fun readSse(
        conn: HttpURLConnection,
        started: Long,
        cancelled: () -> Boolean,
        onThinking: () -> Unit,
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
                        note(parser, parser.push(undecided.toString()), onThinking, onDelta)
                        if (parser.rawText().isNotEmpty() || parser.consumedReasoning()) saw = true
                    }
                    undecided.length > 64 -> {
                        mode = 1
                        note(parser, parser.push(undecided.toString()), onThinking, onDelta)
                        if (parser.rawText().isNotEmpty() || parser.consumedReasoning()) saw = true
                    }
                }
                continue
            }
            if (mode == 2) {
                undecided.append(chunk)
                continue
            }
            val pieces = parser.push(chunk)
            val reasoning = parser.consumedReasoning()
            if (pieces.isNotEmpty() || reasoning) {
                saw = true
                idle = 0
            }
            if (reasoning && parser.visibleText().isBlank()) onThinking()
            if (pieces.isNotEmpty()) {
                val visible = parser.visibleText()
                if (visible.isNotBlank()) onDelta(visible)
            }
        }
        if (mode == 2 || (mode == 0 && undecided.toString().trimStart().startsWith("{"))) {
            return finishBody(undecided.toString())
        }
        if (parser.visibleText().isBlank()) throw EmptyStream()
        return AiMessages.require(parser.rawText(), parser.finishReason)
    }

    private fun note(
        parser: SseParser,
        pieces: List<String>,
        onThinking: () -> Unit,
        onDelta: (String) -> Unit
    ) {
        if (parser.consumedReasoning() && parser.visibleText().isBlank()) onThinking()
        if (pieces.isNotEmpty()) {
            val visible = parser.visibleText()
            if (visible.isNotBlank()) onDelta(visible)
        }
    }

    private fun finishBody(raw: String): String {
        val head = raw.trimStart()
        if (head.startsWith("data:")) {
            val parser = SseParser()
            parser.push(raw)
            return AiMessages.require(parser.rawText(), parser.finishReason)
        }
        val parsed = ChatJson.parse(raw) ?: throw AiException(AiMessages.NO_TEXT, retryable = false)
        return AiMessages.require(parsed.content.orEmpty(), parsed.finishReason)
    }

    private fun readBody(conn: HttpURLConnection, started: Long, cancelled: () -> Boolean): String {
        conn.readTimeout = SLICE_MS
        val reader = InputStreamReader(conn.inputStream, Charsets.UTF_8)
        val buf = CharArray(1024)
        val out = StringBuilder()
        var waited = 0
        var idle = 0
        var saw = false
        while (true) {
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
            saw = true
            idle = 0
            out.append(buf, 0, n)
        }
        return out.toString()
    }

    private fun readError(conn: HttpURLConnection): String {
        return try {
            (conn.errorStream ?: conn.inputStream)?.readBytes()?.decodeToString().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun open(baseUrl: String, apiKey: String, method: String, path: String): HttpURLConnection {
        val root = baseUrl.trim().trimEnd('/')
        return openAbsolute(root + path, apiKey, method)
    }

    private fun openAbsolute(url: String, apiKey: String, method: String): HttpURLConnection {
        val conn = (URL(url)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.useCaches = false
        conn.connectTimeout = AiTimeouts.CONNECT_MS
        conn.readTimeout = AiTimeouts.FIRST_TOKEN_MS
        conn.setRequestProperty("Connection", "close")
        conn.setRequestProperty("Expect", "")
        // header only when the user actually set a key.
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
