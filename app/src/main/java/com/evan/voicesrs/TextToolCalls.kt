package com.evan.voicesrs

import org.json.JSONObject

/**
 * Parse a block a local model prints as plain text, so the app can run it
 * as a tool. The real Qwen text form wraps each call in angle-bracket tags
 * with NO backticks:
 *
 *    FIRSTWRAP
 *    FUNCOPEN NAME >
 *    PARAMOPEN KEY >
 *    VALUE
 *    FUNCEND
 *    FIRSTEND
 *
 * where FIRSTWRAP/FIRSTEND wrap the whole call, FUNCOPEN/FUNCEND wrap the
 * function, and PARAMOPEN/PARAMEND wrap each parameter. NAME and KEY have no
 * surrounding spaces. This object builds every real tag from escape
 * sequences (see the constants below) so the surrounding environment's own
 * tool-call parser never intercepts them.
 *
 * Two forms are accepted inside the wrapper:
 *  - parameter form: a function tag with zero or more child parameter tags;
 *  - JSON form: the inner text is a JSON object with a name field and an
 *    arguments field.
 *
 * Text outside the wrapper is returned unchanged (trimmed). A block with no
 * closing wrapper is left in place.
 */
object TextToolCalls {
    // Build every angle-bracket tag from escapes so the environment's own
    // tool-call parser never intercepts them.
    private const val LT = "\u003c"
    private const val GT = "\u003e"

    private val TOOL_CALL_OPEN = LT + "tool_call" + GT
    private val TOOL_CALL_CLOSE = LT + "/tool_call" + GT
    private val FUNCTION_OPEN_PREFIX = LT + "function="
    private val FUNCTION_CLOSE = LT + "/function" + GT
    private val PARAM_OPEN_PREFIX = LT + "parameter="
    private val PARAM_CLOSE = LT + "/parameter" + GT

    /** Parse [content] into tool calls and the content with wrappers stripped. */
    fun extract(content: String): Pair<List<ChatJson.ToolCall>, String> {
        val calls = ArrayList<ChatJson.ToolCall>()
        val out = StringBuilder()
        val n = content.length
        var i = 0
        while (i < n) {
            if (content.startsWith(TOOL_CALL_OPEN, i)) {
                val p = i
                val end = content.indexOf(TOOL_CALL_CLOSE, i + TOOL_CALL_OPEN.length)
                if (end < 0) { out.append(content, p, n); break }
                val inner = content.substring(i + TOOL_CALL_OPEN.length, end)
                val parsed = parseWrapper(inner)
                if (parsed != null) {
                    calls += ChatJson.ToolCall("textcall_${calls.size}", parsed.first, parsed.second)
                    i = end + TOOL_CALL_CLOSE.length
                } else {
                    out.append(content, p, i + TOOL_CALL_OPEN.length)
                    i = i + TOOL_CALL_OPEN.length
                }
            } else {
                out.append(content[i])
                i++
            }
        }
        return Pair(calls, out.toString().trim())
    }

    /** Remove any wrapper a model left as text. Returns the content. */
    fun stripLeftover(text: String): String = extract(text).second

    /** Parse the text between the wrapper tags. Returns [name, argumentsJson]
     *  or null when the inner text is not a recognized tool block. */
    private fun parseWrapper(inner: String): Pair<String, String>? {
        val trimmed = inner.trim()
        // JSON form first: the whole inner text is a JSON object with
        // name/arguments and no function= prefix.
        val json = try { JSONObject(trimmed) } catch (_: Exception) { null }
        if (json != null && json.has("name")) {
            val args = json.optString("arguments", "{}").ifBlank { "{}" }
            return Pair(json.optString("name", "").trim(), args)
        }
        val funcOpen = trimmed.indexOf(FUNCTION_OPEN_PREFIX)
        if (funcOpen < 0) return null
        val nameEnd = trimmed.indexOf(GT, funcOpen + FUNCTION_OPEN_PREFIX.length)
        if (nameEnd < 0) return null
        val name = trimmed.substring(funcOpen + FUNCTION_OPEN_PREFIX.length, nameEnd).trim()
        if (name.isEmpty()) return null
        val afterName = nameEnd + 1
        val afterFunc = afterName
        val funcClose = trimmed.indexOf(FUNCTION_CLOSE, afterName)
        val innerText = if (funcClose < 0) "" else trimmed.substring(afterFunc, funcClose)
        // Parameter form.
        val params = LinkedHashMap<String, Any?>()
        var i = 0
        val m = innerText.length
        while (i < m) {
            val pStart = innerText.indexOf(PARAM_OPEN_PREFIX, i)
            if (pStart < 0) break
            val keyStart = pStart + PARAM_OPEN_PREFIX.length
            val keyEnd = innerText.indexOf(GT, keyStart)
            if (keyEnd < 0) break
            val key = innerText.substring(keyStart, keyEnd).trim()
            val valueStart = keyEnd + 1
            val pClose = innerText.indexOf(PARAM_CLOSE, valueStart)
            val raw = if (pClose < 0) innerText.substring(valueStart)
                      else innerText.substring(valueStart, pClose)
            val value = raw.trim()
            if (key.isNotEmpty()) params[key] = coerceValue(value)
            if (pClose < 0) break
            i = pClose + PARAM_CLOSE.length
        }
        val obj = JSONObject()
        for ((k, v) in params) obj.put(k, v ?: JSONObject.NULL)
        return Pair(name, obj.toString())
    }

    /** Turn a string parameter value into a JSON number/bool/object when it
     *  parses, otherwise keep it as a string. */
    private fun coerceValue(v: String): Any? {
        val t = v.trim()
        if (t.isEmpty()) return v
        t.toLongOrNull()?.let { return it }
        t.toDoubleOrNull()?.let { return it }
        if (t == "true" || t == "false") return t.toBoolean()
        val o = try { JSONObject(t) } catch (_: Exception) { null }
        if (o != null) return o
        return v
    }
}
