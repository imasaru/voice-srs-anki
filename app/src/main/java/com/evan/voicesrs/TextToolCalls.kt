package com.evan.voicesrs

import org.json.JSONObject

/**
 * Parse a block a local model prints as plain text, so the app can run it
 * as a tool. Two forms are supported:
 *
 *  - XML parameter form: a function tag whose name comes from the name=
 *    attribute and whose parameters are child parameter tags.
 *  - JSON form: a single tag whose inner text is a JSON object with a
 *    name field and an arguments field.
 *
 * Each block is delimited by three backticks on both sides (for example
 * function=name ... /function>). Returns the extracted tool calls and the
 * content with the blocks removed and trimmed. A block with no closing tag
 * is left in place (harmless).
 */
object TextToolCalls {
    private const val BT = "\u0060\u0060\u0060" // three backticks

    /** Parse [content] into tool calls and the content with blocks stripped. */
    fun extract(content: String): Pair<List<ChatJson.ToolCall>, String> {
        val calls = ArrayList<ChatJson.ToolCall>()
        val out = StringBuilder()
        val n = content.length
        var i = 0
        while (i < n) {
            if (content.startsWith(BT + "function=", i)) {
                val p = i
                val nameEnd = content.indexOf('>', i + (BT + "function=").length)
                if (nameEnd < 0) { out.append(content, i, n); break }
                val name = content.substring(i + (BT + "function=").length, nameEnd).trim()
                val c = findClose(content, BT + "/function>" + BT, BT + "/function>", i)
                if (c.first < 0) { out.append(content, p, n); break }
                val inner = content.substring(nameEnd + 1, c.first)
                if (name.isNotEmpty()) {
                    calls += ChatJson.ToolCall("textcall_${calls.size}", name, parseFunctionInner(inner))
                }
                i = c.first + c.second
            } else if (content.startsWith(BT + "{", i)) {
                val p = i
                val close = content.indexOf(BT, i + BT.length)
                if (close < 0) { out.append(content, p, n); break }
                val (name, args) = parseJsonArgs(content.substring(i + BT.length, close))
                if (name.isNotEmpty()) {
                    calls += ChatJson.ToolCall("textcall_${calls.size}", name, args)
                    i = close + BT.length
                } else {
                    out.append(content[i]); i++
                }
            } else {
                out.append(content[i])
                i++
            }
        }
        return Pair(calls, out.toString().trim())
    }

    /** Remove any remaining a model left as text. Returns the content. */
    fun stripLeftover(text: String): String = extract(text).second

    /** Find a delimited close block (full = BT...BT). Returns [start, length],
     *  or [-1, 0] when neither the full nor the bare short marker is present. */
    private fun findClose(s: String, full: String, short: String, from: Int): Pair<Int, Int> {
        val f = s.indexOf(full, from)
        if (f >= 0) return Pair(f, full.length)
        val sh = s.indexOf(short, from)
        if (sh >= 0) return Pair(sh, short.length)
        return Pair(-1, 0)
    }

    private fun parseFunctionInner(inner: String): String {
        val params = LinkedHashMap<String, String>()
        val n = inner.length
        var i = 0
        while (i < n) {
            if (!inner.startsWith(BT + "parameter=", i)) { i++; continue }
            val nameStart = i + (BT + "parameter=").length
            val gt = inner.indexOf('>', nameStart)
            if (gt < 0) break
            val pName = inner.substring(nameStart, gt).trim()
            val valueStart = gt + 1 + BT.length
            val c = findClose(inner, BT + "/parameter>" + BT, BT + "/parameter>", i)
            val value = if (c.first < 0) inner.substring(valueStart).trim()
                        else inner.substring(valueStart, c.first).trim()
            if (pName.isNotEmpty()) params[pName] = value
            if (c.first < 0) break
            i = c.first + c.second
        }
        val obj = JSONObject()
        for ((k, v) in params) obj.put(k, coerceValue(v))
        return obj.toString()
    }

    /** Turn a string parameter value into a JSON number/bool when it parses,
     *  otherwise keep it as a string. */
    private fun coerceValue(v: String): Any {
        val t = v.trim()
        if (t.isEmpty()) return v
        t.toLongOrNull()?.let { return it }
        t.toDoubleOrNull()?.let { return it }
        if (t == "true" || t == "false") return t.toBoolean()
        return v
    }

    private fun parseJsonArgs(inner: String): Pair<String, String> {
        val obj = try { JSONObject(inner) } catch (_: Exception) { null } ?: return Pair("", "")
        val name = obj.optString("name", "").trim()
        if (name.isEmpty()) return Pair("", "")
        val args = obj.optString("arguments", "{}").ifBlank { "{}" }
        return Pair(name, args)
    }
}
