package com.evan.voicesrs

/**
 * Card-face markup. [AnkiText.displayHtml] keeps `<code>` and `<pre>` so the
 * chips can be drawn. Spoken text and [ContentHash] still use [AnkiText.stripHtml],
 * which drops the tags and does not add pauses or punctuation.
 * Inline `<code>` is a chip. `<pre>` is a block. Entities inside either are decoded.
 */
object CodeMarkup {
    enum class Kind { TEXT, CODE, PRE }

    data class Run(val kind: Kind, val text: String)

    fun hasCode(markup: String): Boolean {
        return markup.contains("<code", ignoreCase = true) ||
            markup.contains("<pre", ignoreCase = true)
    }

    /**
     * Visible pieces of a display string. `<br>` and newlines are line breaks
     * inside a text run. Other tags are dropped. Entities are decoded.
     */
    fun runs(markup: String): List<Run> {
        if (markup.isEmpty()) return emptyList()
        val out = ArrayList<Run>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isEmpty()) return
            val text = decode(buf.toString())
            buf.setLength(0)
            if (text.isNotEmpty()) out += Run(Kind.TEXT, text)
        }
        var i = 0
        while (i < markup.length) {
            if (markup[i] != '<') {
                buf.append(markup[i])
                i++
                continue
            }
            val close = markup.indexOf('>', i)
            if (close < 0) {
                buf.append(markup.substring(i))
                break
            }
            val raw = markup.substring(i + 1, close).trim()
            val closing = raw.startsWith("/")
            val name = tagName(if (closing) raw.substring(1) else raw)
            when {
                !closing && name == "code" -> {
                    val end = closeTag(markup, "code", close + 1)
                    flush()
                    val text = inlineText(markup.substring(close + 1, end.content))
                    if (text.isNotEmpty()) out += Run(Kind.CODE, text)
                    i = end.next
                }
                !closing && name == "pre" -> {
                    val end = closeTag(markup, "pre", close + 1)
                    flush()
                    val text = blockText(markup.substring(close + 1, end.content))
                    if (text.isNotEmpty()) out += Run(Kind.PRE, text)
                    i = end.next
                }
                name == "br" -> {
                    buf.append('\n')
                    i = close + 1
                }
                else -> i = close + 1
            }
        }
        flush()
        return out
    }

    /**
     * HTML for [android.text.Html]. `<code>` and `<pre>` become private tags
     * the chip handler styles. Newlines become `<br>` so a stem stays multi-line.
     */
    fun prepareForHtml(markup: String): String {
        if (markup.isEmpty()) return markup
        var s = markup
        s = Regex("(?is)<pre\\b[^>]*>(.*?)</pre>").replace(s) { m ->
            val inner = m.groupValues[1]
                .replace(Regex("(?is)</?code\\b[^>]*>"), "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "<br>")
            "<vsrspre>$inner</vsrspre>"
        }
        s = Regex("(?is)<code\\b[^>]*>").replace(s, "<vsrscode>")
        s = Regex("(?is)</code>").replace(s, "</vsrscode>")
        s = s.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>")
        return s
    }

    private data class Close(val content: Int, val next: Int)

    private fun tagName(raw: String): String {
        val name = StringBuilder()
        for (ch in raw.trim()) {
            if (ch.isWhitespace() || ch == '/') break
            name.append(ch.lowercaseChar())
        }
        return name.toString()
    }

    /** Matching close tag, or the end of the string when the tag is unclosed. */
    private fun closeTag(markup: String, name: String, from: Int): Close {
        var i = from
        while (i < markup.length) {
            val lt = markup.indexOf('<', i)
            if (lt < 0) return Close(markup.length, markup.length)
            val gt = markup.indexOf('>', lt + 1)
            if (gt < 0) return Close(markup.length, markup.length)
            val raw = markup.substring(lt + 1, gt).trim()
            if (raw.startsWith("/") && tagName(raw.substring(1)) == name) {
                return Close(lt, gt + 1)
            }
            i = gt + 1
        }
        return Close(markup.length, markup.length)
    }

    private fun inlineText(raw: String): String {
        val stripped = Regex("(?is)<[^>]+>").replace(raw, "")
        return decode(stripped).replace(Regex("\\s+"), " ").trim()
    }

    private fun blockText(raw: String): String {
        var s = Regex("(?is)<br\\s*/?>").replace(raw, "\n")
        s = Regex("(?is)</?code\\b[^>]*>").replace(s, "")
        s = Regex("(?is)<[^>]+>").replace(s, "")
        s = decode(s).replace("\r\n", "\n").replace('\r', '\n')
        return s.trim('\n')
    }

    private fun decode(s: String): String {
        var out = s
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        val named = arrayOf(
            "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">",
            "quot" to "\"", "apos" to "'", "rsquo" to "'", "lsquo" to "'",
            "rdquo" to "\"", "ldquo" to "\"", "ndash" to "-", "mdash" to "-",
            "hellip" to "...", "copy" to "(c)", "reg" to "(r)"
        )
        for ((name, ch) in named) out = out.replace("&$name;", ch)
        return out
    }
}
