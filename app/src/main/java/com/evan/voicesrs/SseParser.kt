package com.evan.voicesrs

import org.json.JSONObject

/**
 * Incremental Server-Sent Events parser for OpenAI-style chat streams.
 * Accepts chunks split mid-line. Reads `choices[0].delta.content` and
 * `choices[0].message.content`. Stops at a `data: [DONE]` line.
 */
class SseParser {
    private val pending = StringBuilder()
    private val raw = StringBuilder()
    var done: Boolean = false
        private set

    /** Content pieces completed by this chunk. Incomplete lines stay buffered. */
    fun push(chunk: String): List<String> {
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
            val piece = deltaContent(data) ?: continue
            if (piece.isEmpty()) continue
            raw.append(piece)
            out += piece
        }
        return out
    }

    fun rawText(): String = raw.toString()

    /** Accumulated text with `<think>…</think>` removed. */
    fun visibleText(): String = ThinkStrip.strip(raw.toString())
}

object ThinkStrip {
    private val closed = Regex("(?is)<think\\b[^>]*>.*?</think>")
    private val opener = Regex("(?i)<think\\b[^>]*>")

    fun strip(raw: String): String {
        var s = closed.replace(raw, "")
        val open = opener.find(s)
        if (open != null) s = s.substring(0, open.range.first)
        return s.trim()
    }
}

/** Non-stream chat completion body. */
object ChatJson {
    fun messageContent(body: String): String? {
        val obj = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: return null
        val message = choice.optJSONObject("message")
        if (message != null && message.has("content") && !message.isNull("content")) {
            return message.optString("content", "")
        }
        val delta = choice.optJSONObject("delta")
        if (delta != null && delta.has("content") && !delta.isNull("content")) {
            return delta.optString("content", "")
        }
        return null
    }
}

private fun deltaContent(data: String): String? {
    val obj = try {
        JSONObject(data)
    } catch (_: Exception) {
        return null
    }
    val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: return null
    val delta = choice.optJSONObject("delta")
    if (delta != null && delta.has("content") && !delta.isNull("content")) {
        return delta.optString("content", "")
    }
    val message = choice.optJSONObject("message")
    if (message != null && message.has("content") && !message.isNull("content")) {
        return message.optString("content", "")
    }
    return null
}
