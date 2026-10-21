package com.evan.voicesrs

import org.json.JSONObject

/**
 * Incremental Server-Sent Events parser for OpenAI-style chat streams.
 * Accepts chunks split mid-line. Visible text is `delta.content` /
 * `message.content` only. `reasoning_content` and `reasoning` are activity,
 * not the answer. A chunk whose model is `keepalive` is ignored.
 * Stops at a `data: [DONE]` line.
 */
class SseParser {
    private val pending = StringBuilder()
    private val raw = StringBuilder()
    private var reasoningOnLast = false
    var done: Boolean = false
        private set
    var finishReason: String? = null
        private set

    /** Content pieces completed by this chunk. Incomplete lines stay buffered. */
    fun push(chunk: String): List<String> {
        reasoningOnLast = false
        if (done || chunk.isEmpty()) return emptyList()
        pending.append(chunk)
        val out = ArrayList<String>()
        while (!done) {
            val text = pending.toString()
            val nl = text.indexOf('\n')
            if (nl < 0) break
            var line = text.substring(0, nl)
            pending.delete(0, nl + 1)
            if (line.endsWith("\r")) line = line.dropLast(1)
            if (line.isEmpty() || line.startsWith(":")) continue
            if (!line.startsWith("data:")) continue
            var data = line.substring(5)
            if (data.startsWith(" ")) data = data.substring(1)
            if (data == "[DONE]") {
                done = true
                pending.setLength(0)
                break
            }
            val piece = parseEvent(data) ?: continue
            if (piece.finish != null) finishReason = piece.finish
            if (piece.keepalive) continue
            if (piece.reasoning) reasoningOnLast = true
            val content = piece.content
            if (content.isNullOrEmpty()) continue
            raw.append(content)
            out += content
        }
        return out
    }

    /** True when the last [push] saw reasoning tokens and no keepalive-only noise. */
    fun consumedReasoning(): Boolean = reasoningOnLast

    fun rawText(): String = raw.toString()

    /** Accumulated answer text with `<think>…</think>` removed. */
    fun visibleText(): String = ThinkStrip.strip(raw.toString())
}

object ThinkStrip {
    private val closed = Regex("(?is)<think\\b[^>]*>.*?</think>")
    private val opener = Regex("(?i)<think\\b[^>]*>")

    /**
     * Drops closed think blocks, and an unclosed `<think>` through the end of
     * the string (a leading unclosed block removes the whole remainder).
     * Leading and trailing whitespace is removed.
     */
    fun strip(raw: String): String {
        var s = closed.replace(raw, "")
        val open = opener.find(s)
        if (open != null) s = s.substring(0, open.range.first)
        return s.trim()
    }
}

/** Non-stream chat completion body. Reasoning is reported, never returned as content. */
object ChatJson {
    data class Completion(
        val content: String?,
        val reasoning: Boolean,
        val finishReason: String?
    )

    fun parse(body: String): Completion? {
        val obj = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: return null
        val finish = finishOf(choice)
        val node = choice.optJSONObject("message") ?: choice.optJSONObject("delta")
            ?: return Completion(null, false, finish)
        val content = if (node.has("content") && !node.isNull("content")) {
            node.optString("content", "")
        } else {
            null
        }
        val reasoning = nonEmpty(node, "reasoning_content") || nonEmpty(node, "reasoning")
        return Completion(content, reasoning, finish)
    }

    fun messageContent(body: String): String? = parse(body)?.content
}

private data class StreamEvent(
    val content: String?,
    val reasoning: Boolean,
    val keepalive: Boolean,
    val finish: String?
)

private fun parseEvent(data: String): StreamEvent? {
    val obj = try {
        JSONObject(data)
    } catch (_: Exception) {
        return null
    }
    if (obj.optString("model", "") == "keepalive") {
        return StreamEvent(null, false, true, null)
    }
    val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: return null
    val finish = finishOf(choice)
    val node = choice.optJSONObject("delta") ?: choice.optJSONObject("message")
        ?: return StreamEvent(null, false, false, finish)
    val content = if (node.has("content") && !node.isNull("content")) {
        node.optString("content", "")
    } else {
        null
    }
    val reasoning = nonEmpty(node, "reasoning_content") || nonEmpty(node, "reasoning")
    return StreamEvent(content, reasoning, false, finish)
}

private fun finishOf(choice: JSONObject): String? {
    if (!choice.has("finish_reason") || choice.isNull("finish_reason")) return null
    return choice.optString("finish_reason", "").ifBlank { null }
}

private fun nonEmpty(node: JSONObject, key: String): Boolean {
    if (!node.has(key) || node.isNull(key)) return false
    return node.optString(key, "").isNotEmpty()
}
