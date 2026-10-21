package com.evan.voicesrs

/**
 * Small markdown subset for explanation bubbles: bold, headings, inline and
 * fenced code, and lists. The plain text never keeps `**` or backticks.
 */
data class MdSpan(val start: Int, val end: Int, val kind: Kind) {
    enum class Kind { BOLD, CODE, HEADING }
}

data class MarkdownDoc(val text: String, val spans: List<MdSpan>) {
    fun hasRawMarkers(): Boolean = text.contains("**") || text.contains('`')
}

object MarkdownRender {
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val list = Regex("""^\s*([-*]|\d+[.)])\s+(.*)$""")

    fun render(src: String): MarkdownDoc {
        val text = StringBuilder()
        val spans = ArrayList<MdSpan>()
        val lines = src.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        var any = false
        fun breakLine() {
            if (any) text.append('\n')
            any = true
        }
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().startsWith("```")) {
                i++
                val block = StringBuilder()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (block.isNotEmpty()) block.append('\n')
                    block.append(lines[i])
                    i++
                }
                if (i < lines.size && lines[i].trim().startsWith("```")) i++
                breakLine()
                val from = text.length
                appendScrubbed(text, block.toString())
                if (text.length > from) spans += MdSpan(from, text.length, MdSpan.Kind.CODE)
                continue
            }
            breakLine()
            val trimmed = line.trim()
            val head = heading.find(trimmed)
            if (head != null) {
                val from = text.length
                appendInline(text, spans, head.groupValues[2])
                if (text.length > from) spans += MdSpan(from, text.length, MdSpan.Kind.HEADING)
                i++
                continue
            }
            val item = list.find(line)
            if (item != null) {
                val marker = item.groupValues[1]
                val prefix = if (marker[0].isDigit()) marker.trimEnd('.', ')') + ". " else "• "
                text.append(prefix)
                appendInline(text, spans, item.groupValues[2])
                i++
                continue
            }
            appendInline(text, spans, line)
            i++
        }
        val plain = text.toString()
        return MarkdownDoc(plain, spans.filter { it.start < it.end && it.end <= plain.length })
    }

    private fun appendInline(text: StringBuilder, spans: MutableList<MdSpan>, src: String) {
        var i = 0
        while (i < src.length) {
            if (src.startsWith("**", i) || src.startsWith("__", i)) {
                val delim = src.substring(i, i + 2)
                val end = src.indexOf(delim, i + 2)
                if (end > i + 2) {
                    val from = text.length
                    appendScrubbed(text, src.substring(i + 2, end))
                    spans += MdSpan(from, text.length, MdSpan.Kind.BOLD)
                    i = end + 2
                    continue
                }
                i += 2
                continue
            }
            if (src[i] == '`') {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    val from = text.length
                    appendScrubbed(text, src.substring(i + 1, end))
                    spans += MdSpan(from, text.length, MdSpan.Kind.CODE)
                    i = end + 1
                    continue
                }
            }
            val ch = src[i]
            if (ch != '`') text.append(ch)
            i++
        }
    }

    /** Drops leftover markdown markers so a bubble cannot show raw `**` or backticks. */
    private fun appendScrubbed(text: StringBuilder, raw: String) {
        var i = 0
        while (i < raw.length) {
            if (raw.startsWith("**", i) || raw.startsWith("__", i)) {
                i += 2
                continue
            }
            if (raw[i] != '`') text.append(raw[i])
            i++
        }
    }
}
